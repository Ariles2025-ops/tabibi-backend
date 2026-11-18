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
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret", java.time.Clock.systemUTC());
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andExpect(method(HttpMethod.DELETE))
               .andRespond(withStatus(HttpStatus.NO_CONTENT));

        comptes.supprimer(COMPTE);

        serveur.verify();
    }

    @Test
    void supprimer_un_compte_deja_absent_n_est_pas_une_erreur() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret", java.time.Clock.systemUTC());
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andRespond(withStatus(HttpStatus.NOT_FOUND));

        comptes.supprimer(COMPTE);

        serveur.verify();
    }

    @Test
    void sans_secret_supprimer_ne_fait_aucun_appel() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "", java.time.Clock.systemUTC());

        comptes.supprimer(COMPTE);

        serveur.verify();
    }

    /** Horloge reglable a la main. */
    static class Horloge extends java.time.Clock {
        java.time.Instant maintenant = java.time.Instant.parse("2026-11-16T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return maintenant; }
    }

    private void attendCompte(String telephone) {
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andExpect(method(HttpMethod.GET))
               .andRespond(withSuccess("{\"id\":\"" + COMPTE + "\",\"firstName\":\"Amina\","
                       + "\"attributes\":{\"phoneNumber\":[\"" + telephone + "\"]}}", MediaType.APPLICATION_JSON));
    }

    @Test
    void le_cache_des_comptes_expire_au_bout_d_une_heure() {
        Horloge horloge = new Horloge();
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret", horloge);
        attendCompte("0550000001");
        attendCompte("0550000002");

        org.assertj.core.api.Assertions.assertThat(comptes.infos(COMPTE).orElseThrow().telephone()).isEqualTo("0550000001");
        horloge.maintenant = horloge.maintenant.plus(java.time.Duration.ofMinutes(59));
        org.assertj.core.api.Assertions.assertThat(comptes.infos(COMPTE).orElseThrow().telephone()).isEqualTo("0550000001");
        horloge.maintenant = horloge.maintenant.plus(java.time.Duration.ofMinutes(2));
        org.assertj.core.api.Assertions.assertThat(comptes.infos(COMPTE).orElseThrow().telephone()).isEqualTo("0550000002");

        serveur.verify();
    }

    @Test
    void reponse_admin_sans_attributes_donne_un_telephone_null() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret", java.time.Clock.systemUTC());
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andRespond(withSuccess("{\"id\":\"" + COMPTE + "\",\"firstName\":\"Amina\"}", MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThat(comptes.infos(COMPTE).orElseThrow().telephone()).isNull();
    }

    @Test
    void attribut_phone_number_est_lu() {
        KeycloakComptesUtilisateurs comptes = new KeycloakComptesUtilisateurs(builder, "tabibi", "c", "secret", java.time.Clock.systemUTC());
        attendJeton();
        serveur.expect(requestTo("http://kc/admin/realms/tabibi/users/" + COMPTE))
               .andRespond(withSuccess("{\"id\":\"" + COMPTE + "\",\"attributes\":{\"phone_number\":[\"+213550123456\"]}}",
                       MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions.assertThat(comptes.infos(COMPTE).orElseThrow().telephone()).isEqualTo("+213550123456");
    }
}
