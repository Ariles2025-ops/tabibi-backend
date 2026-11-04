package dz.tabibi.backend.administration.adapter;

import dz.tabibi.backend.administration.domain.AttributionRoleMedecin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Donne le role MEDECIN par l'API d'administration de Keycloak, avec le compte de service du client
 * {@code tabibi-backend-admin} (role manage-users du realm). Sans secret configure (developpement, tests),
 * l'attribution est seulement journalisee : l'administrateur donne alors le role dans la console Keycloak.
 */
@Component
public class KeycloakAttributionRoleMedecin implements AttributionRoleMedecin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAttributionRoleMedecin.class);
    private static final String ROLE = "MEDECIN";

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String secret;

    public KeycloakAttributionRoleMedecin(
            @Value("${tabibi.keycloak.admin.url:http://keycloak:8080}") String url,
            @Value("${tabibi.keycloak.admin.realm:tabibi}") String realm,
            @Value("${tabibi.keycloak.admin.client-id:tabibi-backend-admin}") String clientId,
            @Value("${tabibi.keycloak.admin.secret:}") String secret) {
        this.http = RestClient.builder().baseUrl(url).build();
        this.realm = realm;
        this.clientId = clientId;
        this.secret = secret;
    }

    @Override
    public void accorder(UUID compte) {
        if (secret.isBlank()) {
            log.warn("Role {} a donner a la main au compte {} : secret d'administration Keycloak non configure.", ROLE, compte);
            return;
        }
        String jeton = "Bearer " + jeton();
        Map<?, ?> role = http.get()
                .uri("/admin/realms/{realm}/roles/{role}", realm, ROLE)
                .header("Authorization", jeton)
                .retrieve().body(Map.class);
        http.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, compte)
                .header("Authorization", jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve().toBodilessEntity();
        log.info("Role {} donne au compte {}.", ROLE, compte);
    }

    private String jeton() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> reponse = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(Map.class);
        return (String) reponse.get("access_token");
    }
}
