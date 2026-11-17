package dz.tabibi.backend.rappels.adapter;

import dz.tabibi.backend.rappels.domain.EnvoiSms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Envoi de SMS par BudgetSMS, avec les MEMES reglages que la connexion par SMS de Keycloak
 * (TABIBI_SMS_MODE=budgetsms, TABIBI_BUDGETSMS_USERNAME / USERID / HANDLE, TABIBI_SMS_SENDER).
 * Tout autre mode : aucune passerelle (les rappels restent des notifications dans l'application).
 * Les numeros sont masques dans les journaux.
 */
@Component
public class BudgetSmsEnvoi implements EnvoiSms {

    private static final Logger log = LoggerFactory.getLogger(BudgetSmsEnvoi.class);

    private final boolean actif;
    private final String url;
    private final String username;
    private final String userid;
    private final String handle;
    private final String expediteur;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public BudgetSmsEnvoi(@Value("${TABIBI_SMS_MODE:console}") String mode,
                          @Value("${TABIBI_BUDGETSMS_URL:https://api.budgetsms.net/sendsms/}") String url,
                          @Value("${TABIBI_BUDGETSMS_USERNAME:}") String username,
                          @Value("${TABIBI_BUDGETSMS_USERID:}") String userid,
                          @Value("${TABIBI_BUDGETSMS_HANDLE:}") String handle,
                          @Value("${TABIBI_SMS_SENDER:Ndawik}") String expediteur) {
        this.actif = "budgetsms".equalsIgnoreCase(mode == null ? "" : mode.trim())
                && !username.isBlank() && !userid.isBlank() && !handle.isBlank();
        this.url = url;
        this.username = username.trim();
        this.userid = userid.trim();
        this.handle = handle.trim();
        this.expediteur = expediteur;
        log.info("Rappels SMS : {}", actif ? "BudgetSMS actif" : "desactives (mode " + mode + ")");
    }

    @Override
    public boolean disponible() {
        return actif;
    }

    @Override
    public void envoyer(String telephone, String texte) {
        if (!actif) {
            return;
        }
        Map<String, String> p = new LinkedHashMap<>();
        p.put("username", username);
        p.put("userid", userid);
        p.put("handle", handle);
        p.put("msg", texte);
        p.put("from", expediteur);
        p.put("to", telephone.startsWith("+") ? telephone.substring(1) : telephone);
        String requete = p.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url + "?" + requete))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            String corps = r.body() == null ? "" : r.body().trim();
            if (r.statusCode() == 200 && corps.startsWith("OK")) {
                log.info("Rappel SMS envoye a {}", masque(telephone));
                return;
            }
            throw new IllegalStateException("BudgetSMS : " + (corps.startsWith("ERR") ? corps : "HTTP " + r.statusCode()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("BudgetSMS injoignable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envoi SMS interrompu", e);
        }
    }

    static String masque(String tel) {
        return tel.length() < 8 ? "…" : tel.substring(0, 8) + "…" + tel.substring(tel.length() - 2);
    }
}
