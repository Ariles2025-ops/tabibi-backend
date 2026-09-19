package dz.tabibi.backend.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou sur le realm Keycloak importe (infra/keycloak/tabibi-realm.json, lu depuis le repertoire
 * du projet, ou Maven lance les tests) : durcissement present, roles et clients attendus, comptes de
 * demonstration importes haches (jamais en clair) et conformes aux mots de passe documentes dans le README.
 */
class RealmKeycloakTest {

    private static final Path REALM = Path.of("infra/keycloak/tabibi-realm.json");

    /** Comptes de demonstration du README : dev local uniquement. */
    private static final Map<String, String> MOTS_DE_PASSE_DEMO = Map.of(
            "patient.demo", "patient",
            "medecin.demo", "medecin",
            "admin.demo", "admin",
            "pharmacie.demo", "pharmacie",
            "secretaire.demo", "secretaire");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNode realm = lire();

    private JsonNode lire() {
        try {
            return mapper.readTree(REALM.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Realm illisible : " + REALM.toAbsolutePath(), e);
        }
    }

    private static List<String> textes(JsonNode tableau, String champ) {
        List<String> valeurs = new ArrayList<>();
        for (JsonNode element : tableau) {
            valeurs.add(champ == null ? element.asText() : element.get(champ).asText());
        }
        return valeurs;
    }

    private JsonNode client(String clientId) {
        for (JsonNode client : realm.get("clients")) {
            if (clientId.equals(client.get("clientId").asText())) {
                return client;
            }
        }
        throw new AssertionError("Client absent du realm : " + clientId);
    }

    private JsonNode utilisateur(String username) {
        for (JsonNode utilisateur : realm.get("users")) {
            if (username.equals(utilisateur.get("username").asText())) {
                return utilisateur;
            }
        }
        throw new AssertionError("Utilisateur absent du realm : " + username);
    }

    @Test
    void le_realm_tabibi_est_protege_contre_la_force_brute() {
        assertThat(realm.get("realm").asText()).isEqualTo("tabibi");
        assertThat(realm.get("enabled").asBoolean()).isTrue();
        assertThat(realm.get("bruteForceProtected").asBoolean()).isTrue();
        assertThat(realm.get("permanentLockout").asBoolean()).isFalse();
        assertThat(realm.get("failureFactor").asInt()).isEqualTo(5);
        assertThat(realm.get("waitIncrementSeconds").asInt()).isEqualTo(60);
        assertThat(realm.get("maxFailureWaitSeconds").asInt()).isEqualTo(900);
    }

    @Test
    void la_politique_de_mot_de_passe_et_les_sessions_sont_durcies() {
        assertThat(realm.get("passwordPolicy").asText())
                .isEqualTo("length(10) and digits(1) and lowerCase(1) and upperCase(1) and notUsername");
        assertThat(realm.get("otpPolicyType").asText()).isEqualTo("totp");
        assertThat(realm.get("sslRequired").asText()).isEqualTo("external");
        assertThat(realm.get("accessTokenLifespan").asInt()).isEqualTo(300);
        assertThat(realm.get("ssoSessionIdleTimeout").asInt()).isEqualTo(1800);
        assertThat(realm.get("rememberMe").asBoolean()).isFalse();
    }

    @Test
    void les_roles_de_la_plateforme_sont_presents() {
        assertThat(textes(realm.get("roles").get("realm"), "name"))
                .containsExactlyInAnyOrder("PATIENT", "MEDECIN", "SECRETAIRE", "ADMIN", "PHARMACIE");
    }

    @Test
    void le_client_web_est_public_avec_pkce_et_des_origines_explicites() {
        JsonNode web = client("tabibi-web");

        assertThat(web.get("publicClient").asBoolean()).isTrue();
        assertThat(web.get("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(web.get("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(textes(web.get("redirectUris"), null)).contains("http://localhost:4200/*", "https://tabibi.example/*");
        assertThat(textes(web.get("webOrigins"), null)).containsExactlyInAnyOrder("http://localhost:4200", "https://tabibi.example");
        assertThat(textes(web.get("redirectUris"), null)).doesNotContain("*", "http://*", "https://*");
        assertThat(textes(web.get("webOrigins"), null)).doesNotContain("*", "+");
        assertThat(web.get("attributes").get("pkce.code.challenge.method").asText()).isEqualTo("S256");
    }

    @Test
    void le_client_mobile_est_public_avec_pkce_sans_mot_de_passe_direct() {
        JsonNode mobile = client("tabibi-mobile");

        assertThat(mobile.get("publicClient").asBoolean()).isTrue();
        assertThat(mobile.get("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(mobile.get("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(mobile.get("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(textes(mobile.get("redirectUris"), null)).containsExactly("dz.tabibi.app:/oauthredirect");
        assertThat(mobile.get("attributes").get("pkce.code.challenge.method").asText()).isEqualTo("S256");
    }

    @Test
    void les_comptes_de_demonstration_sont_importes_haches_et_correspondent_au_readme() throws Exception {
        assertThat(realm.get("users").size()).isEqualTo(MOTS_DE_PASSE_DEMO.size());

        for (Map.Entry<String, String> compte : MOTS_DE_PASSE_DEMO.entrySet()) {
            JsonNode utilisateur = utilisateur(compte.getKey());
            assertThat(utilisateur.get("credentials").size()).isEqualTo(1);
            JsonNode credential = utilisateur.get("credentials").get(0);
            assertThat(credential.get("type").asText()).isEqualTo("password");
            assertThat(credential.has("value")).as("mot de passe en clair pour %s", compte.getKey()).isFalse();

            JsonNode donnees = mapper.readTree(credential.get("credentialData").asText());
            JsonNode secret = mapper.readTree(credential.get("secretData").asText());
            assertThat(donnees.get("algorithm").asText()).isEqualTo("pbkdf2-sha512");
            byte[] sel = Base64.getDecoder().decode(secret.get("salt").asText());
            byte[] attendu = Base64.getDecoder().decode(secret.get("value").asText());
            byte[] calcule = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(
                    new PBEKeySpec(compte.getValue().toCharArray(), sel, donnees.get("hashIterations").asInt(), attendu.length * 8))
                    .getEncoded();

            assertThat(Base64.getEncoder().encodeToString(calcule))
                    .as("empreinte du mot de passe de %s", compte.getKey())
                    .isEqualTo(secret.get("value").asText());
        }
    }

    @Test
    void les_comptes_de_demonstration_ont_des_identifiants_fixes_et_un_seul_role() {
        assertThat(utilisateur("patient.demo").get("id").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(utilisateur("medecin.demo").get("id").asText()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(utilisateur("admin.demo").get("id").asText()).isEqualTo("33333333-3333-3333-3333-333333333333");
        assertThat(utilisateur("pharmacie.demo").get("id").asText()).isEqualTo("44444444-4444-4444-4444-444444444444");
        assertThat(utilisateur("secretaire.demo").get("id").asText()).isEqualTo("55555555-5555-5555-5555-555555555555");
        assertThat(textes(utilisateur("secretaire.demo").get("realmRoles"), null)).containsExactly("SECRETAIRE");
        assertThat(textes(utilisateur("admin.demo").get("realmRoles"), null)).containsExactly("ADMIN");
    }
}
