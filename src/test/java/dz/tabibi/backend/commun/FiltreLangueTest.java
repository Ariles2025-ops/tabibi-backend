package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.ContexteLangue;
import dz.tabibi.backend.commun.adapter.FiltreLangue;
import dz.tabibi.backend.commun.domain.Langue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le filtre pose la langue de l'en-tete Accept-Language pour la duree du traitement,
 * et l'efface a la sortie — meme quand la suite de la chaine echoue.
 */
class FiltreLangueTest {

    private final FiltreLangue filtre = new FiltreLangue();

    @AfterEach
    void rendreLaLangue() {
        ContexteLangue.effacer();
    }

    /** Chaine qui note la langue vue au moment ou la requete est traitee. */
    private static final class ChaineTemoin implements FilterChain {
        final List<Langue> vues = new ArrayList<>();

        @Override
        public void doFilter(ServletRequest requete, ServletResponse reponse) {
            vues.add(ContexteLangue.courante());
        }
    }

    private static MockHttpServletRequest requete(String acceptLanguage) {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/api/moi/profil");
        if (acceptLanguage != null) {
            requete.addHeader(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return requete;
    }

    @Test
    void pose_la_langue_de_l_en_tete_pendant_le_traitement() throws Exception {
        ChaineTemoin chaine = new ChaineTemoin();

        filtre.doFilter(requete("ar-DZ,fr;q=0.9"), new MockHttpServletResponse(), chaine);

        assertThat(chaine.vues).containsExactly(Langue.AR);
    }

    @Test
    void sans_en_tete_la_langue_est_le_francais() throws Exception {
        ChaineTemoin chaine = new ChaineTemoin();

        filtre.doFilter(requete(null), new MockHttpServletResponse(), chaine);
        filtre.doFilter(requete("de"), new MockHttpServletResponse(), chaine);

        assertThat(chaine.vues).containsExactly(Langue.FR, Langue.FR);
    }

    @Test
    void efface_la_langue_a_la_sortie() throws Exception {
        filtre.doFilter(requete("en"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(ContexteLangue.courante()).isEqualTo(Langue.FR);
    }

    @Test
    void efface_la_langue_meme_si_la_suite_de_la_chaine_echoue() {
        FilterChain quiEchoue = (requete, reponse) -> {
            throw new IllegalStateException("panne");
        };

        assertThatThrownBy(() -> filtre.doFilter(requete("ar"), new MockHttpServletResponse(), quiEchoue))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ContexteLangue.courante()).isEqualTo(Langue.FR);
    }
}
