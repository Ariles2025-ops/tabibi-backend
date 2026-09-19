package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.application.AuditService;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Enregistre le filtre d'audit dans la chaine servlet, juste apres la chaine de Spring Security :
 * le sujet du jeton est alors connu, et les requetes refusees par la securite elle-meme (401 sans
 * jeton ou jeton invalide, 403 du verrou /api/admin/**) ne sont pas des acces. Configuration a part,
 * non importee par les tests web (@WebMvcTest), qui n'en ont pas besoin.
 */
@Configuration
public class AuditConfig {

    /** Juste apres la chaine de filtres de Spring Security (ordre -100). */
    public static final int ORDRE_FILTRE = SecurityProperties.DEFAULT_FILTER_ORDER + 1;

    @Bean
    public FilterRegistrationBean<FiltreAudit> filtreAudit(AuditService service, Clock horloge) {
        FilterRegistrationBean<FiltreAudit> enregistrement = new FilterRegistrationBean<>(new FiltreAudit(service, horloge));
        enregistrement.setName("filtreAudit");
        enregistrement.setOrder(ORDRE_FILTRE);
        return enregistrement;
    }
}
