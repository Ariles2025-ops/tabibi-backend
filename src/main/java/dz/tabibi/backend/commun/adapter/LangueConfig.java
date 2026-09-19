package dz.tabibi.backend.commun.adapter;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre le filtre de langue en tete de la chaine servlet : avant la limitation de debit et
 * avant Spring Security, pour que meme un refus 429 ou une erreur metier soit rendu dans la langue
 * demandee. Configuration a part, non importee par les tests web (@WebMvcTest), qui n'en ont pas
 * besoin : sans elle, la langue vaut le francais.
 */
@Configuration
public class LangueConfig {

    /** Avant le filtre de limitation de debit (DEFAULT_FILTER_ORDER - 1) et la chaine de securite. */
    public static final int ORDRE_FILTRE = SecurityProperties.DEFAULT_FILTER_ORDER - 2;

    @Bean
    public FilterRegistrationBean<FiltreLangue> filtreLangue() {
        FilterRegistrationBean<FiltreLangue> enregistrement = new FilterRegistrationBean<>(new FiltreLangue());
        enregistrement.setName("filtreLangue");
        enregistrement.setOrder(ORDRE_FILTRE);
        return enregistrement;
    }
}
