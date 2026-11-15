package dz.tabibi.keycloak;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Envoi des SMS. Fournisseur choisi par {@code TABIBI_SMS_MODE} :
 * <ul>
 *   <li>{@code budgetsms} : API HTTP de BudgetSMS ({@code TABIBI_BUDGETSMS_USERNAME}, {@code TABIBI_BUDGETSMS_USERID},
 *       {@code TABIBI_BUDGETSMS_HANDLE}, expéditeur {@code TABIBI_SMS_SENDER}, « Tabibi » par défaut).</li>
 *   <li>{@code http} : POST JSON {@code {"to": "+213…", "text": "…"}} vers {@code TABIBI_SMS_URL},
 *       avec {@code Authorization: Bearer TABIBI_SMS_TOKEN}, pour un autre fournisseur derrière un petit adaptateur.</li>
 *   <li>{@code console} (dev seulement) : le code est écrit dans les logs Keycloak.</li>
 * </ul>
 * Une configuration incomplète empêche Keycloak de démarrer : jamais de connexion silencieusement sans SMS.
 */
interface SmsSender {

    void send(String to, String text);

    static SmsSender fromEnv() {
        return fromEnv(System.getenv());
    }

    static SmsSender fromEnv(Map<String, String> env) {
        String mode = env.getOrDefault("TABIBI_SMS_MODE", "http");
        return switch (mode) {
            case "console" -> new Console();
            case "budgetsms" -> new BudgetSms(env.getOrDefault("TABIBI_BUDGETSMS_URL", BudgetSms.URL),
                    env.get("TABIBI_BUDGETSMS_USERNAME"), env.get("TABIBI_BUDGETSMS_USERID"), env.get("TABIBI_BUDGETSMS_HANDLE"),
                    env.getOrDefault("TABIBI_SMS_SENDER", "Tabibi"));
            case "http" -> new Http(env.get("TABIBI_SMS_URL"), env.get("TABIBI_SMS_TOKEN"));
            default -> throw new IllegalStateException("TABIBI_SMS_MODE inconnu « " + mode + " » (budgetsms, http ou console)");
        };
    }

    HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    final class Console implements SmsSender {
        private static final Logger log = Logger.getLogger(SmsSender.class);

        @Override
        public void send(String to, String text) {
            log.warnf("[SMS DEV, NE PAS UTILISER EN PRODUCTION] %s : %s", to, text);
        }
    }

    final class Http implements SmsSender {
        private final String url;
        private final String token;

        Http(String url, String token) {
            if (url == null || url.isBlank()) throw new IllegalStateException("TABIBI_SMS_URL manquant");
            this.url = url;
            this.token = token;
        }

        @Override
        public void send(String to, String text) {
            String body = "{\"to\":\"" + json(to) + "\",\"text\":\"" + json(text) + "\"}";
            var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (token == null ? "" : token))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = call(req).statusCode();
            if (status >= 300) throw new IllegalStateException("Passerelle SMS : HTTP " + status);
        }

        private static String json(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    /**
     * BudgetSMS : GET {@code /sendsms/}, réponse texte {@code OK <smsid> …} ou {@code ERR <code>} (toujours HTTP 200).
     * Le texte arabe part automatiquement en Unicode (70 caractères par SMS, le code de connexion tient en un seul).
     * Le handle est dans l'URL : l'URL n'est jamais journalisée, seuls le numéro masqué et le code d'erreur le sont.
     */
    final class BudgetSms implements SmsSender {
        static final String URL = "https://api.budgetsms.net/sendsms/";
        private static final Logger log = Logger.getLogger(SmsSender.class);
        /** Erreurs de configuration du compte : à corriger dans prod.env ou dans l'espace BudgetSMS. */
        private static final Map<String, String> ACCOUNT_ERRORS = Map.of(
                "1001", "crédit épuisé, recharger le compte BudgetSMS",
                "1002", "identifiants refusés (username, userid, handle)",
                "1003", "compte BudgetSMS inactif",
                "1004", "adresse IP du serveur non autorisée dans l'espace BudgetSMS",
                "2003", "expéditeur trop long (11 caractères au plus)",
                "2004", "expéditeur refusé",
                "3001", "pas de route vers ce numéro, contacter BudgetSMS",
                "3002", "pas de route configurée, contacter BudgetSMS");

        private final String url;
        private final String username;
        private final String userid;
        private final String handle;
        private final String sender;

        BudgetSms(String url, String username, String userid, String handle, String sender) {
            if (blank(username) || blank(userid) || blank(handle)) {
                throw new IllegalStateException("BudgetSMS : TABIBI_BUDGETSMS_USERNAME, TABIBI_BUDGETSMS_USERID et TABIBI_BUDGETSMS_HANDLE sont obligatoires");
            }
            if (!userid.trim().matches("[0-9]+")) throw new IllegalStateException("TABIBI_BUDGETSMS_USERID : nombre attendu");
            if (!sender.matches("[A-Za-z0-9 ]{1,11}")) {
                throw new IllegalStateException("TABIBI_SMS_SENDER : 1 à 11 lettres ou chiffres (exigé par Mobilis et Ooredoo)");
            }
            this.url = url;
            this.username = username.trim();
            this.userid = userid.trim();
            this.handle = handle.trim();
            this.sender = sender;
        }

        @Override
        public void send(String to, String text) {
            var params = new java.util.LinkedHashMap<String, String>();
            params.put("username", username);
            params.put("userid", userid);
            params.put("handle", handle);
            params.put("msg", text);
            params.put("from", sender);
            // E.164 sans « + » : +213550123456 → 213550123456
            params.put("to", to.startsWith("+") ? to.substring(1) : to);
            String query = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            var req = HttpRequest.newBuilder(URI.create(url + "?" + query)).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = call(req);
            String body = res.body() == null ? "" : res.body().trim();
            if (res.statusCode() == 200 && body.startsWith("OK")) return;
            String code = body.startsWith("ERR") ? body.substring(3).trim()
                    : res.statusCode() == 200 ? "réponse inattendue" : "HTTP " + res.statusCode();
            String hint = ACCOUNT_ERRORS.get(code);
            log.errorf("[SMS] BudgetSMS a refusé l'envoi vers %s : %s%s", masked(to), code, hint == null ? "" : " (" + hint + ")");
            throw new IllegalStateException("Passerelle SMS : " + code);
        }

        /** +213550123456 → +2135501…56 : assez pour retrouver un envoi, pas pour réutiliser le numéro. */
        static String masked(String phone) {
            return phone.length() < 8 ? "…" : phone.substring(0, 8) + "…" + phone.substring(phone.length() - 2);
        }

        private static boolean blank(String s) {
            return s == null || s.isBlank();
        }
    }

    private static HttpResponse<String> call(HttpRequest req) {
        try {
            return CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Passerelle SMS injoignable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
