package dz.tabibi.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Test
    void la_verification_des_ordonnances_est_aussi_limitee() throws Exception {
        LimiteLectureFiltre filtre = new LimiteLectureFiltre(2);
        assertThat(lire(filtre, "/api/ordonnances/verifier/AAAA1111", "41.200.10.7")).isEqualTo(200);
        assertThat(lire(filtre, "/api/ordonnances/verifier/AAAA2222", "41.200.10.7")).isEqualTo(200);
        assertThat(lire(filtre, "/api/ordonnances/verifier/AAAA3333", "41.200.10.7")).isEqualTo(429);
        // les autres lectures d'ordonnances (connecte) ne sont pas concernees
        assertThat(lire(filtre, "/api/ordonnances/mes", "41.200.10.7")).isEqualTo(200);
    }

    @Test
    void les_lectures_de_reservation_et_les_requetes_authentifiees_ne_sont_pas_limitees() throws Exception {
        LimiteLectureFiltre filtre = new LimiteLectureFiltre(60);
        for (int i = 0; i < 61; i++) {
            assertThat(lire(filtre, "/api/medecins/42/creneaux", "41.200.10.8")).isEqualTo(200);
            assertThat(lire(filtre, "/api/medecins/42/avis", "41.200.10.8")).isEqualTo(200);
            assertThat(lire(filtre, "/api/medecins/42/reclamable", "41.200.10.8")).isEqualTo(200);
        }
        // la recherche anonyme n'est limitee qu'au-dela du seuil
        for (int i = 0; i < 60; i++) {
            assertThat(lire(filtre, "/api/medecins", "41.200.10.9")).isEqualTo(200);
        }
        assertThat(lire(filtre, "/api/medecins", "41.200.10.9")).isEqualTo(429);
        // authentifie : pas de limite par IP
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", "ROLE_PATIENT"));
        try {
            for (int i = 0; i < 70; i++) {
                assertThat(lire(filtre, "/api/medecins", "41.200.10.9")).isEqualTo(200);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
