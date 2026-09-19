package dz.tabibi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge de reference des cas d'usage qui raisonnent sur l'heure courante (rappels) : UTC en
 * production, une horloge fixe dans les tests. Injectee par constructeur, jamais lue statiquement.
 */
@Configuration
public class HorlogeConfig {

    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }
}
