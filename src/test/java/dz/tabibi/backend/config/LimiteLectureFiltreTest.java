package dz.tabibi.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteLectureFiltreTest {

    private int lire(LimiteLectureFiltre filtre, String chemin, String ip) throws Exception {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", chemin);
        requete.setRemoteAddr(ip);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(requete, reponse, new MockFilterChain());
        return reponse.getStatus();
    }

    @Test
    void freine_une_ip_publique_qui_aspire_l_annuaire_sans_toucher_au_reseau_interne_ni_aux_autres_ip() throws Exception {
        LimiteLectureFiltre filtre = new LimiteLectureFiltre(3);
        for (int i = 0; i < 3; i++) {
            assertThat(lire(filtre, "/api/medecins", "41.200.10.5")).isEqualTo(200);
        }
        assertThat(lire(filtre, "/api/medecins/123", "41.200.10.5")).isEqualTo(429);
        assertThat(lire(filtre, "/api/medecins", "41.200.10.6")).isEqualTo(200);
        // rendu serveur du site (reseau Docker) : jamais limite
        for (int i = 0; i < 10; i++) {
            assertThat(lire(filtre, "/api/medecins", "172.18.0.5")).isEqualTo(200);
        }
        // hors annuaire : pas concerne
        assertThat(lire(filtre, "/api/moi", "41.200.10.5")).isEqualTo(200);
    }

    @Test
    void reconnait_les_adresses_internes() {
        assertThat(LimiteLectureFiltre.estInterne("127.0.0.1")).isTrue();
        assertThat(LimiteLectureFiltre.estInterne("172.18.0.5")).isTrue();
        assertThat(LimiteLectureFiltre.estInterne("10.0.0.3")).isTrue();
        assertThat(LimiteLectureFiltre.estInterne("41.200.10.5")).isFalse();
        assertThat(LimiteLectureFiltre.estInterne("")).isFalse();
    }
}
