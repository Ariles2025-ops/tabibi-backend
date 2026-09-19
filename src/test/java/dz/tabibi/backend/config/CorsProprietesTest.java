package dz.tabibi.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalisation des origines configurees et configuration CORS construite par SecurityConfig. */
class CorsProprietesTest {

    @Test
    void nettoie_les_blancs_et_les_entrees_vides() {
        CorsProprietes proprietes = new CorsProprietes(List.of(" http://localhost:4200 ", "", "https://tabibi.example", "  "));

        assertThat(proprietes.origines()).containsExactly("http://localhost:4200", "https://tabibi.example");
    }

    @Test
    void sans_origine_la_liste_est_vide_et_rien_n_est_autorise() {
        assertThat(new CorsProprietes(null).origines()).isEmpty();
        assertThat(new CorsProprietes(List.of()).origines()).isEmpty();
    }

    @Test
    void la_configuration_cors_ne_porte_que_sur_l_api_avec_les_origines_configurees() {
        CorsProprietes proprietes = new CorsProprietes(Arrays.asList("http://localhost:4200", "https://tabibi.example"));

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) new SecurityConfig().corsConfigurationSource(proprietes);

        assertThat(source.getCorsConfigurations().keySet()).containsExactly("/api/**");
        CorsConfiguration configuration = source.getCorsConfigurations().get("/api/**");
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:4200", "https://tabibi.example");
        assertThat(configuration.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }
}
