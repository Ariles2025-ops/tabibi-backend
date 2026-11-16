package dz.tabibi.backend.annuaire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration 023 (reclassement des fiches « autre ») sur des fiches d'exemple, avec le vrai SQL du changeset,
 * sur un vrai PostgreSQL. Liquibase l'a deja jouee sur la table vide au demarrage : on la rejoue ici sur les
 * donnees d'exemple, comme elle s'executera en production.
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class ReclassementAutreIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    private UUID fiche(String nom, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into medecin (id, nom_complet, nom_tri, specialite_slug, specialite_fr, wilaya_code, wilaya_fr) "
                + "values (?, ?, ?, ?, ?, '15', 'Tizi Ouzou')", id, nom, nom.toLowerCase(), slug,
                "autre".equals(slug) ? "Autre" : slug);
        return id;
    }

    private Map<String, Object> lire(UUID id) {
        return jdbc.queryForMap("select nom_complet, nom_tri, specialite_slug, specialite_fr from medecin where id = ?", id);
    }

    @Test
    void reclasse_les_specialites_connues_et_laisse_les_autres() throws Exception {
        UUID cardio = fiche("Abbassen Malika, Cardiologue À Tizi Ouzou", "autre");
        UUID generaliste = fiche("Benali Karim, Médecin Généraliste", "autre");
        UUID dentiste = fiche("Hamdi Sara, Chirurgien-Dentiste À Oran", "autre");
        UUID orl = fiche("Kaci Amine, Orl", "autre");
        UUID gastro = fiche("Ouali Nadia, Gastro-Entérologue À Bejaia", "autre");
        UUID pediatre = fiche("Zerrouki Imene, Pédiatre", "autre");
        UUID interniste = fiche("Mansouri Lila, Médecin Interniste À Alger", "autre");
        UUID dejaClasse = fiche("Saidi Omar, Cardiologue", "pediatre");
        UUID sansVirgule = fiche("Clinique El Azhar", "autre");

        String sql = new ClassPathResource("db/changelog/sql/023-reclassement-autre.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);

        assertThat(lire(cardio)).containsEntry("nom_complet", "Abbassen Malika")
                .containsEntry("nom_tri", "abbassen malika")
                .containsEntry("specialite_slug", "cardiologue").containsEntry("specialite_fr", "Cardiologue");
        assertThat(lire(generaliste)).containsEntry("nom_complet", "Benali Karim")
                .containsEntry("specialite_slug", "medecin-generaliste").containsEntry("specialite_fr", "Médecin généraliste");
        assertThat(lire(dentiste)).containsEntry("nom_complet", "Hamdi Sara").containsEntry("specialite_slug", "dentiste");
        assertThat(lire(orl)).containsEntry("nom_complet", "Kaci Amine").containsEntry("specialite_slug", "ORL")
                .containsEntry("specialite_fr", "ORL");
        assertThat(lire(gastro)).containsEntry("specialite_slug", "gastro-enterologue")
                .containsEntry("specialite_fr", "Gastro-entérologue");
        assertThat(lire(pediatre)).containsEntry("specialite_slug", "pediatre").containsEntry("specialite_fr", "Pédiatre");
        // Libelle sans specialite exacte sur le site, deja classee, ou sans suffixe : intouchees.
        assertThat(lire(interniste)).containsEntry("nom_complet", "Mansouri Lila, Médecin Interniste À Alger")
                .containsEntry("specialite_slug", "autre");
        assertThat(lire(dejaClasse)).containsEntry("nom_complet", "Saidi Omar, Cardiologue")
                .containsEntry("specialite_slug", "pediatre");
        assertThat(lire(sansVirgule)).containsEntry("nom_complet", "Clinique El Azhar")
                .containsEntry("specialite_slug", "autre");

        // Rejouee, la migration ne change plus rien (les fiches reclassees ne sont plus « autre »).
        jdbc.execute(sql);
        assertThat(lire(cardio)).containsEntry("nom_complet", "Abbassen Malika");
    }
}
