package dz.tabibi.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les taches planifiees (@Scheduled), dont le planificateur des rappels de rendez-vous.
 * Configuration a part : elle n'est pas chargee par les tests web (@WebMvcTest), qui n'importent
 * que SecurityConfig.
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
