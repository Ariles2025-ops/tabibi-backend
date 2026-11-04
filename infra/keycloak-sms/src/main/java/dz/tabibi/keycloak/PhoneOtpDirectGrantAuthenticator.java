package dz.tabibi.keycloak;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;

/**
 * Connexion depuis les écrans de l'appli mobile, sans page web : flux « direct grant » du client public
 * {@code tabibi-mobile}, en deux appels au point {@code /token} (grant_type=password) :
 * <ol>
 *   <li>{@code phone} + {@code consent=yes} : le code part par SMS ; réponse 401
 *       {@code {"error":"tabibi_otp_sent","resend_in":60}} ;</li>
 *   <li>{@code phone} + {@code code} : jetons si le code est bon.</li>
 * </ol>
 * Mêmes règles que la page web ({@link PhoneOtpAuthenticator}) : limites d'envoi, code valable 5 min, 5 essais,
 * empreinte seulement, numéros de revue des stores. Chaque appel au point /token ouvre une nouvelle session : l'état
 * du code est donc gardé côté serveur, par numéro. Les refus renvoient {@code {"error":"tabibi_otp","code":<clé i18n>,
 * "args":{…}}}, traduits par l'appli.
 */
public class PhoneOtpDirectGrantAuthenticator implements Authenticator {

    static final String SENT = "tabibi_otp_sent";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = Logger.getLogger(PhoneOtpDirectGrantAuthenticator.class);

    private final SmsSender sms;
    private final ReviewAccess review;

    PhoneOtpDirectGrantAuthenticator(SmsSender sms, ReviewAccess review) {
        this.sms = sms;
        this.review = review;
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        MultivaluedMap<String, String> form = ctx.getHttpRequest().getDecodedFormParameters();
        String phone = Phones.normalize(form.getFirst("phone"));
        if (phone == null) {
            refuse(ctx, 400, "auth.phone.invalid", Map.of());
            return;
        }
        String code = form.getFirst("code");
        if (code == null || code.isBlank()) requestCode(ctx, phone, "yes".equals(form.getFirst("consent")), locale(ctx, form));
        else verify(ctx, phone, code.trim(), locale(ctx, form));
    }

    // ------------------------------------------------------------------ étape 1 : envoi du code

    private void requestCode(AuthenticationFlowContext ctx, String phone, boolean consent, Locale locale) {
        if (!consent) {
            refuse(ctx, 400, "auth.phone.consentRequired", Map.of());
            return;
        }
        SingleUseObjectProvider store = ctx.getSession().singleUseObjects();
        long now = System.currentTimeMillis();
        // Un seul envoi par minute et par numéro, même pour des demandes simultanées (réservation atomique).
        if (!store.putIfAbsent(lockKey(phone), PhoneOtpAuthenticator.RESEND_AFTER_SECONDS)) {
            Map<String, String> state = store.get(key(phone));
            long wait = state == null ? PhoneOtpAuthenticator.RESEND_AFTER_SECONDS * 1000L
                    : Long.parseLong(state.getOrDefault("sent", "0")) + PhoneOtpAuthenticator.RESEND_AFTER_SECONDS * 1000L - now;
            sent(ctx, Math.max(1, wait / 1000 + 1));
            return;
        }
        if (review.covers(phone, Instant.now())
                && PhoneOtpAuthenticator.count(store, PhoneOtpAuthenticator.reviewFailuresKey(phone)) < ReviewAccess.MAX_FAILURES_PER_DAY) {
            save(store, phone, Map.of("review", "true", "sent", String.valueOf(now), "exp", String.valueOf(now + ttlMillis())));
            log.warnf("[REVUE STORES] connexion depuis l'appli demandée pour le numéro de revue %s, sans SMS (IP %s)", phone,
                    ctx.getConnection().getRemoteAddr());
            sent(ctx, PhoneOtpAuthenticator.RESEND_AFTER_SECONDS);
            return;
        }
        Integer waitMinutes = PhoneOtpAuthenticator.rateLimit(ctx, phone);
        if (waitMinutes != null) {
            store.remove(lockKey(phone));
            ctx.getEvent().detail("phone", phone).error(Errors.ACCESS_DENIED);
            refuse(ctx, 429, "auth.phone.tooMany", Map.of("minutes", waitMinutes));
            return;
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String salt = HexFormat.of().formatHex(RANDOM.generateSeed(16));
        save(store, phone, Map.of("hash", hash(salt, phone, code), "salt", salt, "sent", String.valueOf(now),
                "exp", String.valueOf(now + ttlMillis())));
        try {
            sms.send(phone, message(ctx, locale, "auth.sms.text", code));
        } catch (RuntimeException e) {
            log.errorf("[SMS] code non envoyé : %s", e.getMessage());
            store.remove(key(phone));
            store.remove(lockKey(phone));   // renvoi possible tout de suite
            refuse(ctx, 503, "auth.sms.failed", Map.of());
            return;
        }
        sent(ctx, PhoneOtpAuthenticator.RESEND_AFTER_SECONDS);
    }

    // ------------------------------------------------------------------ étape 2 : vérification

    private void verify(AuthenticationFlowContext ctx, String phone, String code, Locale locale) {
        SingleUseObjectProvider store = ctx.getSession().singleUseObjects();
        Map<String, String> state = store.get(key(phone));
        if (state == null || System.currentTimeMillis() > Long.parseLong(state.getOrDefault("exp", "0"))) {
            refuse(ctx, 400, "auth.otp.expired", Map.of());
            return;
        }
        // Chaque essai réserve une des 5 places de ce code avant d'être comparé : des essais simultanés ne peuvent pas
        // dépasser la limite (un compteur lu puis réécrit le permettait). Places gardées 15 min : blocage après 5 échecs.
        int slot = PhoneOtpAuthenticator.reserveSlot(store, "tabibi-otp-app-try:" + phone + ":" + state.get("sent"),
                PhoneOtpAuthenticator.MAX_TRIES, 900);
        if (slot < 0) {
            refuse(ctx, 429, "auth.otp.locked", Map.of("minutes", 15));
            return;
        }
        boolean reviewing = state.containsKey("review");
        if (reviewing && (!review.covers(phone, Instant.now())
                || PhoneOtpAuthenticator.count(store, PhoneOtpAuthenticator.reviewFailuresKey(phone)) >= ReviewAccess.MAX_FAILURES_PER_DAY)) {
            store.remove(key(phone));
            refuse(ctx, 400, "auth.otp.expired", Map.of());
            return;
        }
        boolean correct = reviewing ? review.accepts(code)
                : MessageDigest.isEqual(hash(state.get("salt"), phone, code).getBytes(StandardCharsets.UTF_8),
                        state.getOrDefault("hash", "").getBytes(StandardCharsets.UTF_8));
        if (!correct) {
            if (reviewing) {
                int failures = PhoneOtpAuthenticator.increment(store, PhoneOtpAuthenticator.reviewFailuresKey(phone), 86400);
                log.warnf("[REVUE STORES] code refusé pour %s (%d échec(s) aujourd'hui, IP %s)", phone, failures, ctx.getConnection().getRemoteAddr());
            }
            ctx.getEvent().detail("phone", phone).error(Errors.INVALID_USER_CREDENTIALS);
            int left = PhoneOtpAuthenticator.MAX_TRIES - slot - 1;
            if (left > 0) refuse(ctx, 401, "auth.otp.wrong", Map.of("attempts", left));
            else refuse(ctx, 429, "auth.otp.locked", Map.of("minutes", 15));
            return;
        }

        KeycloakSession session = ctx.getSession();
        RealmModel realm = ctx.getRealm();
        UserModel user = session.users().getUserByUsername(realm, phone);
        if (user == null) {
            user = session.users().addUser(realm, null, phone, true, true);   // rôles par défaut
            user.setEnabled(true);
            user.setSingleAttribute("phone_number", phone);
            user.setSingleAttribute("locale", locale.getLanguage());
            ctx.getEvent().detail("registration", "true");
        }
        if (!user.isEnabled()) {
            ctx.getEvent().user(user).error(Errors.USER_DISABLED);
            refuse(ctx, 403, "error.account.suspended", Map.of());
            return;
        }
        if (reviewing) log.warnf("[REVUE STORES] connexion depuis l'appli du numéro de revue %s (IP %s)", phone, ctx.getConnection().getRemoteAddr());
        store.remove(key(phone));
        ctx.setUser(user);
        ctx.success();
    }

    // ------------------------------------------------------------------ outils

    private static String key(String phone) {
        return "tabibi-otp-app:" + phone;
    }

    private static String lockKey(String phone) {
        return "tabibi-otp-app-lock:" + phone;
    }

    private static long ttlMillis() {
        return PhoneOtpAuthenticator.CODE_TTL_SECONDS * 1000L;
    }

    /** L'état vit jusqu'à l'expiration du code. */
    private static void save(SingleUseObjectProvider store, String phone, Map<String, String> state) {
        long ttl = Math.max(1, (Long.parseLong(state.getOrDefault("exp", "0")) - System.currentTimeMillis()) / 1000);
        store.remove(key(phone));
        store.put(key(phone), ttl, state);
    }

    private static String hash(String salt, String phone, String code) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update((salt + "|" + phone + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sent(AuthenticationFlowContext ctx, long resendIn) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", SENT);
        body.put("resend_in", resendIn);
        ctx.challenge(json(401, body));
    }

    private static void refuse(AuthenticationFlowContext ctx, int status, String code, Map<String, Object> args) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "tabibi_otp");
        body.put("code", code);
        body.put("args", args);
        ctx.challenge(json(status, body));
    }

    private static Response json(int status, Map<String, Object> body) {
        return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    /** Langue choisie dans l'appli (paramètre {@code locale}), pour le texte du SMS et le profil créé. */
    private static Locale locale(AuthenticationFlowContext ctx, MultivaluedMap<String, String> form) {
        String l = form.getFirst("locale");
        if ("ar".equals(l) || "fr".equals(l)) return Locale.forLanguageTag(l);
        return ctx.getSession().getContext().resolveLocale(null);
    }

    private static String message(AuthenticationFlowContext ctx, Locale locale, String key, Object... args) {
        try {
            Theme theme = ctx.getSession().theme().getTheme(Theme.Type.LOGIN);
            String pattern = theme.getMessages(locale).getProperty(key, key);
            return new MessageFormat(pattern, locale).format(args);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {}

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void close() {}
}
