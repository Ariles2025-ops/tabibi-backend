package dz.tabibi.backend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDebitFiltreTest {

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    private int envoyer(LimiteDebitFiltre filtre, String methode, String compte) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                compte == null ? null : new UsernamePasswordAuthenticationToken(compte, null, List.of()));
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        filtre.doFilter(new MockHttpServletRequest(methode, "/api/x"), reponse, new MockFilterChain());
        return reponse.getStatus();
    }

    @Test
    void refuse_les_actions_au_dela_de_la_limite_par_compte_sans_toucher_aux_lectures_ni_aux_autres_comptes() throws Exception {
        LimiteDebitFiltre filtre = new LimiteDebitFiltre(3);
        for (int i = 0; i < 3; i++) {
            assertThat(envoyer(filtre, "POST", "a")).isEqualTo(200);
        }
        assertThat(envoyer(filtre, "POST", "a")).isEqualTo(429);
        assertThat(envoyer(filtre, "GET", "a")).isEqualTo(200);
        assertThat(envoyer(filtre, "POST", "b")).isEqualTo(200);
        assertThat(envoyer(filtre, "POST", null)).isEqualTo(200);
    }
}
