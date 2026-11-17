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

    @Autowired
    dz.tabibi.backend.creneaux.domain.CreneauRepository creneaux;

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

    @Test
    void commune_filtree_avant_la_limite_et_liste_des_communes() {
        for (int i = 0; i < 60; i++) {
            repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Aaa " + i,
                    "cardiologue-it", "Cardiologue", "16", "Alger", "Alger-Centre"));
        }
        for (int i = 0; i < 8; i++) {
            repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Zzz " + i,
                    "cardiologue-it", "Cardiologue", "16", "Alger", "Bab Ezzouar"));
        }
        repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Beni 1", "cardiologue-it", "Cardiologue", "16", "Alger", "Béni Messous"));
        repository.enregistrer(new Medecin(UUID.randomUUID(), "Dr Wil 1", "cardiologue-it", "Cardiologue", "16", "Alger", "Alger"));

        assertThat(repository.rechercher(CritereRecherche.de("cardiologue-it", "16", null))).hasSize(50);
        assertThat(repository.rechercher(CritereRecherche.de("cardiologue-it", "16", null, "bab-ezzouar")))
                .hasSize(8).allMatch(m -> m.ville().equals("Bab Ezzouar"));
        assertThat(repository.rechercher(CritereRecherche.de("cardiologue-it", "16", null, "beni-messous"))).hasSize(1);
        assertThat(repository.rechercher(CritereRecherche.de("cardiologue-it", "16", null, "alger-centre"))).hasSize(50);

        assertThat(repository.communes("cardiologue-it", "16", 3))
                .extracting(MedecinRepository.CommuneNombre::slug, MedecinRepository.CommuneNombre::nombre)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("alger-centre", 60L),
                        org.assertj.core.groups.Tuple.tuple("bab-ezzouar", 8L));
    }

    @Test
    void alias_d_une_fiche_reclamee() {
        UUID ancien = UUID.randomUUID();
        UUID nouveau = UUID.randomUUID();
        assertThat(repository.aliasDe(ancien)).isEmpty();

        repository.ajouterAlias(ancien, nouveau);

        assertThat(repository.aliasDe(ancien)).contains(nouveau);
    }

    @Test
    void le_medecin_reservable_passe_en_tete_avant_la_limite_et_les_pharmacies_sont_a_part() {
        String wilaya = "47"; // wilaya propre a ce test
        for (int i = 0; i < 60; i++) {
            repository.enregistrer(new Medecin(UUID.randomUUID(), "Aaa Medecin " + i,
                    "pediatre", "Pédiatre", wilaya, "Ghardaia", null));
        }
        UUID inscrit = UUID.randomUUID();
        repository.enregistrer(new Medecin(inscrit, "Zzz Inscrit", "pediatre", "Pédiatre", wilaya, "Ghardaia", null));
        creneaux.enregistrer(new dz.tabibi.backend.creneaux.domain.Creneau(UUID.randomUUID(), inscrit,
                java.time.Instant.now().plusSeconds(86_400), 20, true));
        UUID passe = UUID.randomUUID(); // creneau libre mais passe : pas reservable
        repository.enregistrer(new Medecin(passe, "Zzz Passe", "pediatre", "Pédiatre", wilaya, "Ghardaia", null));
        creneaux.enregistrer(new dz.tabibi.backend.creneaux.domain.Creneau(UUID.randomUUID(), passe,
                java.time.Instant.now().minusSeconds(86_400), 20, true));
        repository.enregistrer(new Medecin(UUID.randomUUID(), "Pharmacie Ghardaia", "pharmacie", "Pharmacie",
                wilaya, "Ghardaia", null));

        var resultats = repository.rechercher(CritereRecherche.de(null, wilaya, null));

        assertThat(resultats).hasSize(50);
        assertThat(resultats.get(0).id()).isEqualTo(inscrit);
        assertThat(resultats).extracting(Medecin::id).doesNotContain(passe);
        assertThat(resultats).noneMatch(m -> m.specialiteSlug().equals("pharmacie"));
        assertThat(repository.rechercher(CritereRecherche.de("pharmacie", wilaya, null)))
                .extracting(Medecin::nomComplet).containsExactly("Pharmacie Ghardaia");
    }
}
