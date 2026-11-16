package dz.tabibi.backend.commun.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Appels a l'API d'administration Keycloak, contre un serveur simule. */
class KeycloakComptesUtilisateursTest {

    private static final UUID COMPTE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String JETON = "{\"access_token\":\"t\"}";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://kc");
    private final MockRestServiceServer serveur = MockRestServiceServer.bindTo(builder).build();

    private void attendJeton() {
        serveur.expect(requestTo("http://kc/realms/tabibi/protocol/openid-connect/token"))
               .andExpect(method(HttpMethod.POST))
               .andRespond(withSuccess(JETON, MediaType.APPLICATION_JSON));
    }

    @Test
    void supprimer_appelle_delete_users() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret");
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andExpect(method(HttpMethod.DELETE))
               .andRespond(withStatus(HttpStatus.NO_CONTENT));

        comptes.supprimer(COMPTE);

        serveur.verify();
    }

    @Test
    void supprimer_un_compte_deja_absent_n_est_pas_une_erreur() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret");
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andRespond(withStatus(HttpStatus.NOT_FOUND));

        comptes.supprimer(COMPTE);

        serveur.verify();
    }

    @Test
    void sans_secret_supprimer_ne_fait_aucun_appel() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "");

        comptes.supprimer(COMPTE);

        serveur.verify();
    }
}
