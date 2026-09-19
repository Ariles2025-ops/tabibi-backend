package dz.tabibi.backend.supervision;

import dz.tabibi.backend.commun.domain.Compteurs;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Supervision sur l'application complete : la sante reste publique, les metriques (dont le format
 * Prometheus) sont reservees au role ADMIN, et les compteurs metier y figurent bien.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SupervisionWebTest {

    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired TestRestTemplate http;
    @Autowired MeterRegistry registre;
    @Autowired Compteurs compteurs;
    @MockBean JwtDecoder jwtDecoder; // remplace le decodeur Keycloak : aucun appel reseau

    @BeforeEach
    void jetonsDeTest() {
        doThrow(new BadJwtException("Jeton inconnu.")).when(jwtDecoder).decode(anyString());
        doReturn(jeton(PATIENT, "PATIENT")).when(jwtDecoder).decode("patient");
        doReturn(jeton(ADMIN, "ADMIN")).when(jwtDecoder).decode("admin");
    }

    private static Jwt jeton(UUID sujet, String role) {
        return Jwt.withTokenValue(role.toLowerCase())
                .header("alg", "none")
                .subject(sujet.toString())
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }

    private ResponseEntity<String> get(String chemin, String jeton) {
        HttpHeaders entetes = new HttpHeaders();
        if (jeton != null) {
            entetes.setBearerAuth(jeton);
        }
        return http.exchange(chemin, HttpMethod.GET, new HttpEntity<>(null, entetes), String.class);
    }

    @Test
    void la_sante_reste_publique() {
        assertThat(get("/actuator/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health/readiness", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void les_metriques_sont_refusees_sans_jeton() {
        assertThat(get("/actuator/prometheus", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/actuator/metrics", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void les_metriques_sont_refusees_a_un_utilisateur_sans_le_role_admin() {
        assertThat(get("/actuator/prometheus", "patient").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/actuator/metrics", "patient").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void l_administrateur_lit_les_metriques_au_format_prometheus() {
        compteurs.incrementer(Compteurs.RENDEZVOUS_RESERVES);

        ResponseEntity<String> reponse = get("/actuator/prometheus", "admin");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Prometheus remplace les points par des tires bas dans le nom expose.
        assertThat(reponse.getBody()).contains("tabibi_rendezvous_reserves");
        assertThat(reponse.getBody()).contains("application=\"tabibi-backend\"");
    }

    @Test
    void l_administrateur_lit_aussi_la_liste_des_metriques() {
        ResponseEntity<String> reponse = get("/actuator/metrics", "admin");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody()).contains("jvm.memory.used");
    }

    @Test
    void les_compteurs_metier_sont_bien_branches_sur_le_registre_de_l_application() {
        // Le contexte Spring est partage entre classes de test : on mesure l'ecart, pas la valeur
        // absolue du compteur (d'autres scenarios ont pu deposer des avis sur le meme registre).
        double avant = registre.counter(Compteurs.AVIS_DEPOSES).count();
        compteurs.incrementer(Compteurs.AVIS_DEPOSES);
        compteurs.incrementer(Compteurs.AVIS_DEPOSES);

        assertThat((long) (registre.counter(Compteurs.AVIS_DEPOSES).count() - avant)).isEqualTo(2);
    }
}
