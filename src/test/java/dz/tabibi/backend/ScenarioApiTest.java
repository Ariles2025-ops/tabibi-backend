package dz.tabibi.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.rappels.adapter.PlanificateurRappels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * Scenario de bout en bout sur l'application complete (contexte entier, serveur HTTP sur un port
 * libre, adaptateurs en memoire du profil par defaut : aucune base ni Keycloak). Seul le decodeur
 * de jetons est remplace : chaque jeton de test ("patient", "medecin", "admin") est traduit en
 * un Jwt construit a la main avec le sujet et le role Keycloak attendus ; tout le reste (chaine de
 * securite, roles, filtres d'audit et de limitation de debit, controleurs, services, PDF) est reel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioApiTest {

    /** Premier praticien de demonstration de l'annuaire en memoire (Dr Amina Belkacem). */
    private static final UUID MEDECIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired ApplicationContext contexte;
    @MockBean JwtDecoder jwtDecoder; // remplace le decodeur Keycloak : aucun appel reseau

    @BeforeEach
    void jetonsDeTest() {
        // Tout autre jeton est refuse comme le ferait le vrai decodeur (401) ; doReturn pour ne pas declencher ce refus en stubbant.
        doThrow(new BadJwtException("Jeton inconnu.")).when(jwtDecoder).decode(anyString());
        doReturn(jeton(PATIENT, "PATIENT")).when(jwtDecoder).decode("patient");
        doReturn(jeton(MEDECIN, "MEDECIN")).when(jwtDecoder).decode("medecin");
        doReturn(jeton(ADMIN, "ADMIN")).when(jwtDecoder).decode("admin");
    }

    /** Un Jwt tel que Keycloak l'emettrait : sujet = identifiant de l'utilisateur, role dans realm_access.roles. */
    private static Jwt jeton(UUID sujet, String role) {
        return Jwt.withTokenValue(role.toLowerCase())
                .header("alg", "none")
                .subject(sujet.toString())
                .claim("realm_access", Map.of("roles", List.of(role)))
                .build();
    }

    private ResponseEntity<String> appeler(HttpMethod methode, String chemin, String jeton, String corpsJson) {
        HttpHeaders entetes = new HttpHeaders();
        if (jeton != null) {
            entetes.setBearerAuth(jeton);
        }
        if (corpsJson != null) {
            entetes.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(chemin, methode, new HttpEntity<>(corpsJson, entetes), String.class);
    }

    private ResponseEntity<String> get(String chemin, String jeton) {
        return appeler(HttpMethod.GET, chemin, jeton, null);
    }

    private ResponseEntity<String> post(String chemin, String jeton, String corpsJson) {
        return appeler(HttpMethod.POST, chemin, jeton, corpsJson);
    }

    private JsonNode corps(ResponseEntity<String> reponse) throws Exception {
        return json.readTree(reponse.getBody());
    }

    private static List<String> sujets(JsonNode notifications) {
        List<String> sujets = new ArrayList<>();
        notifications.forEach(n -> sujets.add(n.get("sujet").asText()));
        return sujets;
    }

    @Test
    void l_application_demarre_sans_base_avec_le_planificateur_l_audit_et_la_limitation_de_debit() {
        assertThat(contexte.getBeanNamesForType(javax.sql.DataSource.class).length).isZero(); // profil par defaut : en memoire
        assertThat(contexte.getBean(PlanificateurRappels.class)).isNotNull();
        assertThat(contexte.getBean("filtreAudit")).isInstanceOf(FilterRegistrationBean.class);
        assertThat(contexte.getBean("filtreLimiteDebit")).isInstanceOf(FilterRegistrationBean.class);

        assertThat(get("/actuator/health", null).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/moi", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/moi", "jeton-forge").getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/admin/statistiques", "patient").getStatusCode().value()).isEqualTo(403);
        assertThat(get("/api/moi", "patient").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void parcours_complet_du_creneau_a_l_ordonnance_verifiee_et_imprimee() throws Exception {
        // 1. Le medecin ouvre un creneau dans son agenda.
        String debut = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString();
        ResponseEntity<String> creneau = post("/api/medecin/creneaux", "medecin",
                "{\"debut\":\"" + debut + "\",\"dureeMinutes\":20}");
        assertThat(creneau.getStatusCode().value()).isEqualTo(201);
        String creneauId = corps(creneau).get("id").asText();
        assertThat(post("/api/medecin/creneaux", "patient", "{\"debut\":\"" + debut + "\",\"dureeMinutes\":20}")
                .getStatusCode().value()).isEqualTo(403); // pas un medecin

        // 2. Le creneau est visible sans jeton, puis le patient le reserve.
        JsonNode disponibles = corps(get("/api/medecins/" + MEDECIN + "/creneaux", null));
        List<String> ids = new ArrayList<>();
        disponibles.forEach(c -> ids.add(c.get("id").asText()));
        assertThat(ids).contains(creneauId);

        ResponseEntity<String> reservation = post("/api/creneaux/" + creneauId + "/reserver", "patient", null);
        assertThat(reservation.getStatusCode().value()).isEqualTo(201);
        JsonNode rendezVous = corps(reservation);
        String rendezVousId = rendezVous.get("id").asText();
        assertThat(rendezVous.get("statut").asText()).isEqualTo("CONFIRME");
        assertThat(rendezVous.get("patientId").asText()).isEqualTo(PATIENT.toString());
        assertThat(rendezVous.get("medecinId").asText()).isEqualTo(MEDECIN.toString());
        assertThat(post("/api/creneaux/" + creneauId + "/reserver", "patient", null).getStatusCode().value())
                .isEqualTo(409); // deja pris

        // 3. Les deux sont prevenus.
        assertThat(sujets(corps(get("/api/notifications/mes", "patient")))).contains("Rendez-vous confirme");
        assertThat(sujets(corps(get("/api/notifications/mes", "medecin")))).contains("Nouveau rendez-vous");
        assertThat(corps(get("/api/notifications/non-lues/nombre", "patient")).get("nombre").asInt()).isGreaterThan(0);

        // 4. Le medecin honore le rendez-vous.
        ResponseEntity<String> honore = post("/api/rendezvous/" + rendezVousId + "/honorer", "medecin", null);
        assertThat(honore.getStatusCode().value()).isEqualTo(200);
        assertThat(corps(honore).get("statut").asText()).isEqualTo("HONORE");

        // 5. Le patient depose un avis (un seul par rendez-vous).
        String avisJson = "{\"rendezVousId\":\"" + rendezVousId + "\",\"note\":5,\"commentaire\":\"Tres a l'ecoute.\"}";
        ResponseEntity<String> avis = post("/api/avis", "patient", avisJson);
        assertThat(avis.getStatusCode().value()).isEqualTo(201);
        assertThat(corps(avis).get("statut").asText()).isEqualTo("PUBLIE");
        assertThat(post("/api/avis", "patient", avisJson).getStatusCode().value()).isEqualTo(409);

        // 6. La synthese publique du medecin montre un avis, anonymise.
        ResponseEntity<String> synthese = get("/api/medecins/" + MEDECIN + "/avis", null);
        assertThat(synthese.getStatusCode().value()).isEqualTo(200);
        JsonNode syntheseJson = corps(synthese);
        assertThat(syntheseJson.get("nombre").asLong()).isEqualTo(1L);
        assertThat(syntheseJson.get("moyenne").asText()).isEqualTo("5.0");
        assertThat(syntheseJson.get("avis").size()).isEqualTo(1);
        assertThat(syntheseJson.get("avis").get(0).get("commentaire").asText()).isEqualTo("Tres a l'ecoute.");
        assertThat(syntheseJson.get("avis").get(0).has("patientId")).isFalse();
        assertThat(syntheseJson.get("avis").get(0).has("rendezVousId")).isFalse();

        // 7. Le medecin redige une ordonnance rattachee au rendez-vous.
        ResponseEntity<String> ordonnance = post("/api/ordonnances", "medecin",
                "{\"patientId\":\"" + PATIENT + "\",\"rendezVousId\":\"" + rendezVousId + "\","
                + "\"lignes\":[{\"medicament\":\"Paracetamol 1 g\",\"posologie\":\"1 comprime matin et soir\",\"duree\":\"5 jours\"}]}");
        assertThat(ordonnance.getStatusCode().value()).isEqualTo(201);
        JsonNode ordonnanceJson = corps(ordonnance);
        String ordonnanceId = ordonnanceJson.get("id").asText();
        String code = ordonnanceJson.get("codeVerification").asText();
        assertThat(code).hasSize(8);
        assertThat(ordonnanceJson.get("statut").asText()).isEqualTo("EMISE");

        // 8. Un pharmacien verifie le code sans jeton et sans donnee personnelle.
        ResponseEntity<String> verification = get("/api/ordonnances/verifier/" + code.toLowerCase(), null);
        assertThat(verification.getStatusCode().value()).isEqualTo(200);
        JsonNode verificationJson = corps(verification);
        assertThat(verificationJson.get("valide").asBoolean()).isTrue();
        assertThat(verificationJson.get("statut").asText()).isEqualTo("EMISE");
        assertThat(verificationJson.has("patientId")).isFalse();
        assertThat(verificationJson.has("lignes")).isFalse();
        assertThat(get("/api/ordonnances/verifier/ZZZZZZZZ", null).getStatusCode().value()).isEqualTo(404);

        // 9. Le patient (comme le medecin) obtient la version imprimable ; un tiers non.
        HttpHeaders entetes = new HttpHeaders();
        entetes.setBearerAuth("patient");
        ResponseEntity<byte[]> pdf = http.exchange("/api/ordonnances/" + ordonnanceId + "/pdf", HttpMethod.GET,
                new HttpEntity<>(entetes), byte[].class);
        assertThat(pdf.getStatusCode().value()).isEqualTo(200);
        assertThat(pdf.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PDF)).isTrue();
        assertThat(pdf.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"ordonnance-" + code + ".pdf\"");
        assertThat(pdf.getBody()).isNotNull();
        assertThat(pdf.getBody().length).isGreaterThan(1024);
        assertThat(new String(pdf.getBody(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(get("/api/ordonnances/" + ordonnanceId + "/pdf", "medecin").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/ordonnances/" + ordonnanceId + "/pdf", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/ordonnances/" + ordonnanceId + "/pdf", "admin").getStatusCode().value()).isEqualTo(403);

        // 10. Le journal des acces a tout trace (sujet du jeton, jamais le contenu), pour l'administrateur seul.
        assertThat(get("/api/admin/audit", "medecin").getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> audit = get("/api/admin/audit/sujet/" + MEDECIN + "?limite=50", "admin");
        assertThat(audit.getStatusCode().value()).isEqualTo(200);
        List<String> acces = new ArrayList<>();
        corps(audit).forEach(e -> acces.add(e.get("methode").asText() + " " + e.get("chemin").asText() + " " + e.get("statut").asInt()));
        assertThat(acces).contains("POST /api/medecin/creneaux 201", "POST /api/ordonnances 201",
                "POST /api/rendezvous/" + rendezVousId + "/honorer 200");
        assertThat(audit.getBody()).doesNotContain("Paracetamol", "Tres a l'ecoute");
    }

    @Test
    void une_rafale_sur_une_route_de_publication_est_limitee_en_debit() {
        // POST /api/conversations est limite a 20 requetes par minute et par adresse ; le parcours ne l'utilise pas.
        String corpsJson = "{\"medecinId\":\"" + UUID.randomUUID() + "\"}"; // medecin jamais consulte : refus metier 403, mais compte
        ResponseEntity<String> refus = null;
        for (int i = 0; i < 25 && refus == null; i++) {
            ResponseEntity<String> reponse = post("/api/conversations", "patient", corpsJson);
            if (reponse.getStatusCode().value() == 429) {
                refus = reponse;
            } else {
                assertThat(reponse.getStatusCode().value()).isEqualTo(403);
            }
        }

        assertThat(refus).isNotNull();
        assertThat(refus.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(refus.getBody()).isEqualTo("{\"erreur\":\"Trop de requetes, reessayez dans un instant.\"}");
    }
}
