package dz.tabibi.backend.teleconsultation.adapter;

import dz.tabibi.backend.teleconsultation.domain.GenerateurSalle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Expose le generateur de salle du domaine comme bean, sans annoter le domaine. */
@Configuration
public class TeleconsultationConfig {

    @Bean
    public GenerateurSalle generateurSalle() {
        return new GenerateurSalle();
    }
}
