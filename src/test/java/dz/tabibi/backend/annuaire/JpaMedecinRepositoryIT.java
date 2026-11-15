package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recherche de l'annuaire sur un vrai PostgreSQL (Testcontainers).
 * Necessite Docker : tourne en CI (et en local avec -Dit.docker=true).
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class JpaMedecinRepositoryIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired
    MedecinRepository repository;

    @Test
    void recherche_sans_critere_puis_par_nom_et_limite_les_resultats() {
        for (int i = 0; i < 60; i++) {
            repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Test " + i,
                    "cardiologue", "Cardiologue", "16", "Alger", null));
        }
        repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Benali Amina",
                "dentiste", "Dentiste", "31", "Oran", "Oran"));

        assertThat(repository.rechercher(CritereRecherche.de(null, null, null))).hasSize(50);
        assertThat(repository.rechercher(CritereRecherche.de(null, null, "BENALI")))
                .extracting(Medecin::nomComplet).containsExactly("Dr Benali Amina");
        assertThat(repository.rechercher(CritereRecherche.de("dentiste", "31", null))).hasSize(1);
    }
}
