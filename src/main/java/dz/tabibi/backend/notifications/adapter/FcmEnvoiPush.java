package dz.tabibi.backend.notifications.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.notifications.domain.EnvoiPush;
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
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Envoi par Firebase Cloud Messaging (HTTP v1). La cle de service (JSON du compte de service Firebase) est lue
 * dans la variable d'environnement TABIBI_FCM_CLE_SERVICE ; si elle est absente ou illisible, l'envoi est
 * desactive sans erreur. Jetons et contenus ne sont jamais journalises.
 */
@Component
public class FcmEnvoiPush implements EnvoiPush {

    private static final Logger log = LoggerFactory.getLogger(FcmEnvoiPush.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PORTEE = "https://www.googleapis.com/auth/firebase.messaging";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String projet;
    private final String email;
    private final String urlJeton;
    private final java.security.PrivateKey cle;
    private String accesJeton;
    private Instant accesExpire = Instant.EPOCH;

    public FcmEnvoiPush(@Value("${TABIBI_FCM_CLE_SERVICE:}") String cleJson) {
        String p = null, e = null, u = null;
        java.security.PrivateKey k = null;
        if (cleJson != null && !cleJson.isBlank()) {
            try {
                JsonNode n = JSON.readTree(cleJson);
                p = n.path("project_id").asText("");
                e = n.path("client_email").asText("");
                u = n.path("token_uri").asText("https://oauth2.googleapis.com/token");
                String pem = n.path("private_key").asText("")
                        .replaceAll("-----(BEGIN|END) PRIVATE KEY-----|\\s", "");
                k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
                if (p.isBlank() || e.isBlank()) k = null;
            } catch (Exception ex) {
                k = null;
                log.warn("Cle de service FCM illisible : notifications push desactivees.");
            }
        }
        this.cle = k;
        this.projet = p;
        this.email = e;
        this.urlJeton = u;
        log.info("Notifications push (FCM) : {}", k != null ? "actives" : "desactivees (aucune cle de service)");
    }

    @Override
    public boolean disponible() {
        return cle != null;
    }

    @Override
    public Resultat envoyer(String jeton, String titre, String corps, String lien) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("token", jeton);
            message.put("notification", Map.of("title", titre, "body", corps));
            if (lien != null) message.put("data", Map.of("lien", lien));
            HttpRequest requete = HttpRequest.newBuilder(
                            URI.create("https://fcm.googleapis.com/v1/projects/" + projet + "/messages:send"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + jetonAcces())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("message", message))))
                    .build();
            HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
            return interpreter(reponse.statusCode(), reponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envoi FCM interrompu", e);
        } catch (java.io.IOException | java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Envoi FCM impossible : " + e.getClass().getSimpleName(), e);
        }
    }

    /** 200 = envoye ; UNREGISTERED (404) = jeton a supprimer ; tout autre statut = echec. */
    static Resultat interpreter(int statut, String corps) {
        if (statut / 100 == 2) return Resultat.ENVOYE;
        if (statut == 404 && corps != null && corps.contains("UNREGISTERED")) return Resultat.JETON_REFUSE;
        throw new IllegalStateException("FCM a refuse l'envoi (HTTP " + statut + ").");
    }

    private synchronized String jetonAcces() throws java.io.IOException, InterruptedException, java.security.GeneralSecurityException {
        if (accesJeton != null && Instant.now().isBefore(accesExpire.minusSeconds(60))) return accesJeton;
        long now = Instant.now().getEpochSecond();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String entete = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String charge = b64.encodeToString(JSON.writeValueAsBytes(Map.of(
                "iss", email, "scope", PORTEE, "aud", urlJeton, "iat", now, "exp", now + 3600)));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(cle);
        s.update((entete + "." + charge).getBytes(StandardCharsets.UTF_8));
        String assertion = entete + "." + charge + "." + b64.encodeToString(s.sign());
        HttpRequest requete = HttpRequest.newBuilder(URI.create(urlJeton))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type="
                        + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                        + "&assertion=" + assertion))
                .build();
        HttpResponse<String> r = http.send(requete, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("Jeton d'acces Google refuse (HTTP " + r.statusCode() + ").");
        JsonNode n = JSON.readTree(r.body());
        accesJeton = n.path("access_token").asText();
        accesExpire = Instant.now().plusSeconds(n.path("expires_in").asLong(3600));
        return accesJeton;
    }
}
