package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.FiltreLimiteDebit.Regle;
import dz.tabibi.backend.commun.adapter.FiltreLimiteDebit;
import dz.tabibi.backend.commun.adapter.LimiteDebitConfig;
import dz.tabibi.backend.commun.adapter.LimiteurDebit;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Compteurs;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le filtre refuse (429, Retry-After, corps JSON) la requete de trop sur les routes limitees, par
 * adresse IP, laisse passer tout le reste sans le compter, et ne lit X-Forwarded-For que derriere
 * un reverse proxy.
 */
class FiltreLimiteDebitTest {

    private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-03-01T09:00:00Z"));
    private final List<Object> requetesTransmises = new ArrayList<>();
    private final FilterChain chaine = (req, res) -> {
        requetesTransmises.add(req);
        ((HttpServletResponse) res).setStatus(200);
    };

    private FiltreLimiteDebit filtre(boolean derriereProxy) {
        return new FiltreLimiteDebit(LimiteDebitConfig.regles(3, 2, 2, horloge), derriereProxy);
    }

    private static MockHttpServletRequest requete(String methode, String chemin, String ip) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setRemoteAddr(ip);
        return requete;
    }

    private MockHttpServletResponse appeler(FiltreLimiteDebit filtre, MockHttpServletRequest requete) throws Exception {
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, chaine);
        return reponse;
    }

    private List<Integer> statuts(FiltreLimiteDebit filtre, String methode, String chemin, String ip, int fois) throws Exception {
        List<Integer> statuts = new ArrayList<>();
        for (int i = 0; i < fois; i++) {
            statuts.add(appeler(filtre, requete(methode, chemin, ip)).getStatus());
        }
        return statuts;
    }

    @Test
    void refuse_429_apres_depassement_avec_retry_after_et_corps_json() throws Exception {
        FiltreLimiteDebit filtre = filtre(false);

        assertThat(statuts(filtre, "GET", "/api/medecins", "192.168.1.37", 3)).containsExactly(200, 200, 200);
        MockHttpServletResponse refus = appeler(filtre, requete("GET", "/api/medecins/00000000-0000-0000-0000-000000000001", "192.168.1.37"));

        assertThat(refus.getStatus()).isEqualTo(429);
        assertThat(refus.getHeader("Retry-After")).isEqualTo("20");
        assertThat(refus.getContentType()).startsWith("application/json");
        assertThat(refus.getContentAsString()).isEqualTo("{\"erreur\":\"Trop de requetes, reessayez dans un instant.\"}");
        assertThat(requetesTransmises).hasSize(3); // la requete refusee n'atteint pas la suite de la chaine
    }

    @Test
    void chaque_route_limitee_a_son_propre_quota_et_les_ip_sont_independantes() throws Exception {
        FiltreLimiteDebit filtre = filtre(false);

        assertThat(statuts(filtre, "GET", "/api/ordonnances/verifier/AB23CD45", "10.0.0.1", 3)).containsExactly(200, 200, 429);
        assertThat(statuts(filtre, "POST", "/api/dawini/besoins", "10.0.0.1", 3)).containsExactly(200, 200, 429);
        assertThat(statuts(filtre, "POST", "/api/conversations", "10.0.0.1", 3)).containsExactly(200, 200, 429);
        assertThat(statuts(filtre, "POST", "/api/avis", "10.0.0.1", 3)).containsExactly(200, 200, 429);
        assertThat(statuts(filtre, "POST", "/api/avis", "10.0.0.2", 1)).containsExactly(200);
    }

    @Test
    void le_quota_se_recharge_avec_le_temps() throws Exception {
        FiltreLimiteDebit filtre = filtre(false);
        statuts(filtre, "GET", "/api/medecins", "10.0.0.1", 3);
        assertThat(appeler(filtre, requete("GET", "/api/medecins", "10.0.0.1")).getStatus()).isEqualTo(429);

        horloge.avancer(Duration.ofSeconds(20)); // un jeton (3 par minute)

        assertThat(statuts(filtre, "GET", "/api/medecins", "10.0.0.1", 2)).containsExactly(200, 429);
    }

    @Test
    void les_routes_non_concernees_ne_sont_jamais_limitees_ni_comptees() throws Exception {
        FiltreLimiteDebit filtre = filtre(false);
        for (String[] appel : List.of(
                new String[] {"GET", "/api/moi"},
                new String[] {"POST", "/api/rendezvous"},
                new String[] {"GET", "/api/ordonnances/mes"},
                new String[] {"POST", "/api/medecins"},                 // autre methode
                new String[] {"GET", "/api/dawini/besoins"},            // autre methode
                new String[] {"POST", "/api/conversations/1/messages"}, // chemin exact seulement
                new String[] {"POST", "/api/avis/1/signaler"},
                new String[] {"GET", "/api/medecinsx"},                 // prefixe strict
                new String[] {"GET", "/actuator/health"},
                new String[] {"OPTIONS", "/api/medecins"})) {
            for (int i = 0; i < 10; i++) {
                assertThat(appeler(filtre, requete(appel[0], appel[1], "10.0.0.1")).getStatus()).isEqualTo(200);
            }
        }
        assertThat(requetesTransmises).hasSize(100);
        assertThat(statuts(filtre, "GET", "/api/medecins", "10.0.0.1", 4)).containsExactly(200, 200, 200, 429); // quota intact
    }

    @Test
    void hors_proxy_x_forwarded_for_est_ignore() throws Exception {
        FiltreLimiteDebit filtre = filtre(false);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest requete = requete("GET", "/api/medecins", "10.0.0.1");
            requete.addHeader("X-Forwarded-For", "203.0.113." + i); // un client qui tente de changer de cle
            assertThat(appeler(filtre, requete).getStatus()).isEqualTo(200);
        }

        assertThat(appeler(filtre, requete("GET", "/api/medecins", "10.0.0.1")).getStatus()).isEqualTo(429);
    }

    @Test
    void derriere_un_proxy_la_cle_est_le_premier_element_de_x_forwarded_for() throws Exception {
        FiltreLimiteDebit filtre = filtre(true);
        MockHttpServletRequest requete = requete("GET", "/api/medecins", "10.0.0.1");
        requete.addHeader("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1");
        assertThat(filtre.cleDe(requete)).isEqualTo("203.0.113.7");

        for (int i = 0; i < 3; i++) {
            assertThat(appeler(filtre, requete).getStatus()).isEqualTo(200);
        }
        assertThat(appeler(filtre, requete).getStatus()).isEqualTo(429);

        MockHttpServletRequest autreClient = requete("GET", "/api/medecins", "10.0.0.1"); // meme proxy, autre client
        autreClient.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(appeler(filtre, autreClient).getStatus()).isEqualTo(200);

        MockHttpServletRequest sansEnTete = requete("GET", "/api/medecins", "10.0.0.9");
        assertThat(filtre.cleDe(sansEnTete)).isEqualTo("10.0.0.9"); // repli sur l'adresse distante
        MockHttpServletRequest enTeteVide = requete("GET", "/api/medecins", "10.0.0.9");
        enTeteVide.addHeader("X-Forwarded-For", "  ");
        assertThat(filtre.cleDe(enTeteVide)).isEqualTo("10.0.0.9");
    }

    @Test
    void une_adresse_inconnue_partage_un_seul_seau() {
        FiltreLimiteDebit filtre = filtre(false);

        assertThat(filtre.cleDe(requete("GET", "/api/medecins", null))).isEqualTo("inconnue");
        assertThat(filtre.cleDe(requete("GET", "/api/medecins", ""))).isEqualTo("inconnue");
    }

    @Test
    void les_regles_par_defaut_couvrent_les_routes_publiques_et_de_publication() {
        List<Regle> regles = LimiteDebitConfig.regles(120, 30, 20, horloge);

        assertThat(regles).hasSize(5);
        assertThat(regles.get(0).methode()).isEqualTo("GET");
        assertThat(regles.get(0).chemin()).isEqualTo("/api/medecins/**");
        assertThat(regles.get(0).limiteur().capacite()).isEqualTo(120);
        assertThat(regles.get(1).chemin()).isEqualTo("/api/ordonnances/verifier/**");
        assertThat(regles.get(1).limiteur().capacite()).isEqualTo(30);
        assertThat(regles.get(2).chemin()).isEqualTo("/api/dawini/besoins");
        assertThat(regles.get(3).chemin()).isEqualTo("/api/conversations");
        assertThat(regles.get(4).chemin()).isEqualTo("/api/avis");
        assertThat(regles.get(4).limiteur().capacite()).isEqualTo(20);
        assertThat(LimiteDebitConfig.ORDRE_FILTRE).isEqualTo(-101);
    }

    @Test
    void une_regle_incomplete_est_refusee() {
        LimiteurDebit limiteur = LimiteurDebit.parMinute(1, horloge);

        assertThatThrownBy(() -> new Regle("", "/api/medecins", limiteur)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Regle("GET", "api/medecins", limiteur)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Regle("GET", "/api/medecins", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compte_les_depassements_pour_la_supervision() throws Exception {
        CompteursEnregistres compteurs = new CompteursEnregistres();
        FiltreLimiteDebit filtre = new FiltreLimiteDebit(
                LimiteDebitConfig.regles(3, 2, 2, horloge), false, Messages.partagees(), compteurs);

        for (int i = 0; i < 5; i++) {
            filtre.doFilter(requete("GET", "/api/medecins", "10.0.0.1"), new MockHttpServletResponse(), chaine);
        }

        assertThat(compteurs.compte(Compteurs.LIMITE_DEPASSEMENTS)).isEqualTo(2); // 3 passent, 2 sont refusees
    }
}
