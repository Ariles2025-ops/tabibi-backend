package dz.tabibi.keycloak;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.jboss.logging.Logger;
import org.keycloak.theme.Theme;

/**
 * Connexion par numéro de téléphone + code SMS à 6 chiffres. Le compte est créé à la première connexion.
 *
 * <p>Limites (anti-abus et anti-faux comptes) :
 * <ul>
 *   <li>3 codes par numéro par 15 min, 10 par numéro par jour, 20 par adresse IP par heure ;</li>
 *   <li>60 s avant de pouvoir redemander un code ;</li>
 *   <li>code valable 5 min, 5 essais au maximum, stocké uniquement sous forme d'empreinte.</li>
 * </ul>
 *
 * <p>Numéros de revue des stores ({@link ReviewAccess}, désactivé par défaut) : pas de SMS, code fixe connu par son
 * empreinte, mêmes 5 essais par code et 10 échecs par jour au plus ; chaque demande, échec et connexion est journalisé.
 */
public class PhoneOtpAuthenticator implements Authenticator {

    static final int CODE_TTL_SECONDS = 300;
    static final int MAX_TRIES = 5;
    static final int RESEND_AFTER_SECONDS = 60;
    static final int MAX_PER_15_MIN = 3;
    static final int MAX_PER_DAY = 10;
    static final int MAX_PER_IP_HOUR = 20;

    private static final String PHONE = "tabibi.phone";
    private static final String HASH = "tabibi.code.hash";
    private static final String EXPIRES = "tabibi.code.expires";
    private static final String SENT_AT = "tabibi.code.sent";
    private static final String TRIES = "tabibi.code.tries";
    private static final String REVIEW = "tabibi.review";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = Logger.getLogger(PhoneOtpAuthenticator.class);

    private final SmsSender sms;
    private final ReviewAccess review;

    PhoneOtpAuthenticator(SmsSender sms, ReviewAccess review) {
        this.sms = sms;
        this.review = review;
    }

    @Override
    public void authenticate(AuthenticationFlowContext ctx) {
        ctx.challenge(phoneForm(ctx, null).createForm("tabibi-phone.ftl"));
    }

    @Override
    public void action(AuthenticationFlowContext ctx) {
        MultivaluedMap<String, String> form = ctx.getHttpRequest().getDecodedFormParameters();
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        String action = form.getFirst("action");

        if (auth.getAuthNote(PHONE) == null || "restart".equals(action)) {
            if ("restart".equals(action)) clearCode(auth);
            if (form.containsKey("phone")) requestCode(ctx, form);
            else authenticate(ctx);
            return;
        }
        if ("resend".equals(action)) {
            resend(ctx);
            return;
        }
        verify(ctx, form.getFirst("code"));
    }

    // ------------------------------------------------------------------ étape 1 : numéro

    private void requestCode(AuthenticationFlowContext ctx, MultivaluedMap<String, String> form) {
        String phone = Phones.normalize(form.getFirst("phone"));
        boolean consent = "yes".equals(form.getFirst("consent"));
        if (phone == null) {
            fail(ctx, phoneForm(ctx, form).setError("auth.phone.invalid").createForm("tabibi-phone.ftl"));
            return;
        }
        if (!consent) {
            fail(ctx, phoneForm(ctx, form).setError("auth.phone.consentRequired").createForm("tabibi-phone.ftl"));
            return;
        }
        if (reviewAllowed(ctx, phone)) {
            ctx.getAuthenticationSession().setAuthNote(PHONE, phone);
            startReview(ctx, phone);
            ctx.challenge(otpForm(ctx).createForm("tabibi-otp.ftl"));
            return;
        }
        Integer waitMinutes = rateLimit(ctx, phone);
        if (waitMinutes != null) {
            ctx.getEvent().detail("phone", phone).error(Errors.ACCESS_DENIED);
            fail(ctx, phoneForm(ctx, form).setError("auth.phone.tooMany", digits(waitMinutes)).createForm("tabibi-phone.ftl"));
            return;
        }
        ctx.getAuthenticationSession().setAuthNote(PHONE, phone);
        if (!sendCode(ctx, phone)) {
            fail(ctx, phoneForm(ctx, form).setError("auth.sms.failed").createForm("tabibi-phone.ftl"));
            return;
        }
        ctx.challenge(otpForm(ctx).createForm("tabibi-otp.ftl"));
    }

    private void resend(AuthenticationFlowContext ctx) {
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        String phone = auth.getAuthNote(PHONE);
        if (secondsUntilResend(auth) > 0) {
            ctx.challenge(otpForm(ctx).createForm("tabibi-otp.ftl"));
            return;
        }
        if (auth.getAuthNote(REVIEW) != null) {
            startReview(ctx, phone);
            ctx.challenge(otpForm(ctx).createForm("tabibi-otp.ftl"));
            return;
        }
        Integer waitMinutes = rateLimit(ctx, phone);
        if (waitMinutes != null) {
            fail(ctx, otpForm(ctx).setError("auth.phone.tooMany", digits(waitMinutes)).createForm("tabibi-otp.ftl"));
            return;
        }
        if (!sendCode(ctx, phone)) {
            fail(ctx, otpForm(ctx).setError("auth.sms.failed").createForm("tabibi-otp.ftl"));
            return;
        }
        ctx.challenge(otpForm(ctx).createForm("tabibi-otp.ftl"));
    }

    // ------------------------------------------------------------------ étape 2 : code

    private void verify(AuthenticationFlowContext ctx, String code) {
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        String phone = auth.getAuthNote(PHONE);
        long expires = Long.parseLong(note(auth, EXPIRES, "0"));
        int tries = Integer.parseInt(note(auth, TRIES, "0"));

        if (System.currentTimeMillis() > expires) {
            fail(ctx, otpForm(ctx).setError("auth.otp.expired").createForm("tabibi-otp.ftl"));
            return;
        }
        if (tries >= MAX_TRIES) {
            clearCode(auth);
            fail(ctx, phoneForm(ctx, null).setError("auth.otp.locked", digits(15)).createForm("tabibi-phone.ftl"));
            return;
        }
        boolean reviewing = auth.getAuthNote(REVIEW) != null;
        if (reviewing && (!review.covers(phone, Instant.now())   // accès de revue retiré ou expiré pendant la saisie
                || count(ctx.getSession().singleUseObjects(), reviewFailuresKey(phone)) >= ReviewAccess.MAX_FAILURES_PER_DAY)) {
            clearCode(auth);
            fail(ctx, otpForm(ctx).setError("auth.otp.expired").createForm("tabibi-otp.ftl"));
            return;
        }
        boolean correct = reviewing ? review.accepts(code)
                : code != null && MessageDigest.isEqual(hash(auth, phone, code.trim()).getBytes(StandardCharsets.UTF_8),
                        note(auth, HASH, "").getBytes(StandardCharsets.UTF_8));
        if (!correct) {
            if (reviewing) {
                int failures = increment(ctx.getSession().singleUseObjects(), reviewFailuresKey(phone), 86400);
                log.warnf("[REVUE STORES] code refusé pour %s (%d échec(s) aujourd'hui, IP %s)", phone, failures, ctx.getConnection().getRemoteAddr());
                ctx.getEvent().detail("review", "true");
            }
            auth.setAuthNote(TRIES, String.valueOf(tries + 1));
            ctx.getEvent().detail("phone", phone).error(Errors.INVALID_USER_CREDENTIALS);
            fail(ctx, otpForm(ctx).setError("auth.otp.wrong", digits(MAX_TRIES - tries - 1)).createForm("tabibi-otp.ftl"));
            return;
        }

        KeycloakSession session = ctx.getSession();
        RealmModel realm = ctx.getRealm();
        UserModel user = session.users().getUserByUsername(realm, phone);
        if (user == null) {
            user = session.users().addUser(realm, null, phone, true, true); // rôles par défaut : client
            user.setEnabled(true);
            user.setSingleAttribute("phone_number", phone);
            user.setSingleAttribute("locale", locale(ctx).getLanguage());
            ctx.getEvent().detail("registration", "true");
        }
        if (!user.isEnabled()) {
            ctx.getEvent().user(user).error(Errors.USER_DISABLED);
            fail(ctx, phoneForm(ctx, null).setError("error.account.suspended").createForm("tabibi-phone.ftl"));
            return;
        }
        if (reviewing) {
            log.warnf("[REVUE STORES] connexion du numéro de revue %s (accès valable jusqu'au %s inclus, heure d'Alger ; IP %s)", phone, review.lastDay(),
                    ctx.getConnection().getRemoteAddr());
            ctx.getEvent().detail("review", "true");
        }
        clearCode(auth);
        auth.removeAuthNote(PHONE);
        ctx.setUser(user);
        ctx.success();
    }

    // ------------------------------------------------------------------ outils

    /** Faux si la passerelle a refusé ou n'a pas répondu : aucun code valide ne reste en session, renvoi possible tout de suite. */
    private boolean sendCode(AuthenticationFlowContext ctx, String phone) {
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        auth.setAuthNote(HASH, hash(auth, phone, code));
        auth.setAuthNote(EXPIRES, String.valueOf(System.currentTimeMillis() + CODE_TTL_SECONDS * 1000L));
        auth.setAuthNote(SENT_AT, String.valueOf(System.currentTimeMillis()));
        auth.setAuthNote(TRIES, "0");
        try {
            sms.send(phone, message(ctx, "auth.sms.text", code));
            return true;
        } catch (RuntimeException e) {
            log.errorf("[SMS] code non envoyé : %s", e.getMessage());
            clearCode(auth);
            return false;
        }
    }

    /** Numéro de revue valide et pas encore bloqué pour aujourd'hui (sinon : parcours SMS normal). */
    private boolean reviewAllowed(AuthenticationFlowContext ctx, String phone) {
        if (!review.covers(phone, Instant.now())) return false;
        if (count(ctx.getSession().singleUseObjects(), reviewFailuresKey(phone)) < ReviewAccess.MAX_FAILURES_PER_DAY) return true;
        log.errorf("[REVUE STORES] %s bloqué pour aujourd'hui : %d codes refusés", phone, ReviewAccess.MAX_FAILURES_PER_DAY);
        return false;
    }

    /** Code de revue : aucun SMS, aucun code généré ; le code fixe est vérifié par son empreinte. */
    private void startReview(AuthenticationFlowContext ctx, String phone) {
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        auth.setAuthNote(REVIEW, "true");
        auth.setAuthNote(EXPIRES, String.valueOf(System.currentTimeMillis() + CODE_TTL_SECONDS * 1000L));
        auth.setAuthNote(SENT_AT, String.valueOf(System.currentTimeMillis()));
        auth.setAuthNote(TRIES, "0");
        log.warnf("[REVUE STORES] connexion demandée pour le numéro de revue %s, sans SMS (IP %s)", phone, ctx.getConnection().getRemoteAddr());
    }

    static String reviewFailuresKey(String phone) {
        return "tabibi-review-fail:" + phone + ":" + System.currentTimeMillis() / 86_400_000;
    }

    /**
     * Renvoie le nombre de minutes à attendre si une limite est atteinte, sinon {@code null} (et compte la demande).
     * Chaque demande réserve une « place » par {@code putIfAbsent}, opération atomique du cache Keycloak : des demandes
     * envoyées en même temps ne peuvent pas dépasser les limites (un compteur lu puis réécrit le permettait).
     */
    static Integer rateLimit(AuthenticationFlowContext ctx, String phone) {
        SingleUseObjectProvider store = ctx.getSession().singleUseObjects();
        long now = System.currentTimeMillis() / 1000;
        String ip = ctx.getConnection().getRemoteAddr();
        if (!reserve(store, "tabibi-otp:" + phone + ":15m:" + now / 900, MAX_PER_15_MIN, 900)) return (int) Math.max(1, (900 - now % 900) / 60);
        if (!reserve(store, "tabibi-otp:" + phone + ":day:" + now / 86400, MAX_PER_DAY, 86400)) return (int) Math.max(1, (86400 - now % 86400) / 60);
        if (!reserve(store, "tabibi-otp:ip:" + ip + ":" + now / 3600, MAX_PER_IP_HOUR, 3600)) return (int) Math.max(1, (3600 - now % 3600) / 60);
        return null;
    }

    /** Prend la première place libre parmi {@code max} : numéro de la place (0, 1, …), ou -1 si toutes sont prises. */
    static int reserveSlot(SingleUseObjectProvider store, String key, int max, long ttlSeconds) {
        for (int i = 0; i < max; i++) {
            if (store.putIfAbsent(key + "#" + i, ttlSeconds)) return i;
        }
        return -1;
    }

    static boolean reserve(SingleUseObjectProvider store, String key, int max, long ttlSeconds) {
        return reserveSlot(store, key, max, ttlSeconds) >= 0;
    }

    static int count(SingleUseObjectProvider store, String key) {
        Map<String, String> v = store.get(key);
        return v == null ? 0 : Integer.parseInt(v.getOrDefault("n", "0"));
    }

    static int increment(SingleUseObjectProvider store, String key, long ttl) {
        int n = count(store, key) + 1;
        store.put(key, ttl, Map.of("n", String.valueOf(n)));
        return n;
    }

    private static String hash(AuthenticationSessionModel auth, String phone, String code) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            md.update((auth.getParentSession().getId() + "|" + phone + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long secondsUntilResend(AuthenticationSessionModel auth) {
        long sent = Long.parseLong(note(auth, SENT_AT, "0"));
        return Math.max(0, (sent + RESEND_AFTER_SECONDS * 1000L - System.currentTimeMillis()) / 1000);
    }

    /**
     * Nombre passé en texte : formaté par la locale arabe, Keycloak afficherait des chiffres arabes orientaux
     * (٥٩). Tabibi affiche toujours les chiffres 0-9, comme en Algérie.
     */
    static String digits(long n) {
        return "\u2066" + n + "\u2069";
    }

    private static void clearCode(AuthenticationSessionModel auth) {
        for (String n : new String[] {HASH, EXPIRES, SENT_AT, TRIES, REVIEW}) auth.removeAuthNote(n);
    }

    private static String note(AuthenticationSessionModel auth, String name, String fallback) {
        String v = auth.getAuthNote(name);
        return v == null ? fallback : v;
    }

    private LoginFormsProvider phoneForm(AuthenticationFlowContext ctx, MultivaluedMap<String, String> form) {
        LoginFormsProvider f = ctx.form();
        if (form != null) {
            f.setAttribute("phone", form.getFirst("phone") == null ? "" : form.getFirst("phone"));
            if ("yes".equals(form.getFirst("consent"))) f.setAttribute("consent", true);
        }
        return f;
    }

    private LoginFormsProvider otpForm(AuthenticationFlowContext ctx) {
        AuthenticationSessionModel auth = ctx.getAuthenticationSession();
        return ctx.form()
                .setAttribute("phoneDisplay", "\u2066" + Phones.display(auth.getAuthNote(PHONE)) + "\u2069")
                .setAttribute("resendIn", secondsUntilResend(auth))
                .setAttribute("resendInText", digits(secondsUntilResend(auth)));
    }

    /**
     * Réaffiche le formulaire avec l'erreur. On n'utilise pas failureChallenge : dans un flux à étapes
     * ALTERNATIVE, Keycloak repartirait du premier écran et l'erreur serait perdue.
     */
    private static void fail(AuthenticationFlowContext ctx, Response challenge) {
        ctx.challenge(challenge);
    }

    private static Locale locale(AuthenticationFlowContext ctx) {
        return ctx.getSession().getContext().resolveLocale(null);
    }

    /** Texte traduit depuis les messages du thème (générés depuis i18n/fr.json et i18n/ar.json). */
    private static String message(AuthenticationFlowContext ctx, String key, Object... args) {
        try {
            Theme theme = ctx.getSession().theme().getTheme(Theme.Type.LOGIN);
            Locale locale = locale(ctx);
            String pattern = theme.getMessages(locale).getProperty(key, key);
            return new MessageFormat(pattern, locale).format(args);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

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
