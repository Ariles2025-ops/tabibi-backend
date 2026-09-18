package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'integration de l'adaptateur JPA sur un vrai PostgreSQL (Testcontainers).
 * Necessite Docker : tourne en CI (et en local avec -Dit.docker=true).
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class JpaRendezVousRepositoryIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired
    RendezVousRepository repository;

    @Test
    void enregistre_et_detecte_le_creneau_pris() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");
        assertThat(repository.creneauEstLibre(medecin, debut)).isTrue();

        repository.enregistrer(RendezVous.confirmer(UUID.randomUUID(), medecin, debut));

        assertThat(repository.creneauEstLibre(medecin, debut)).isFalse();
    }
}
