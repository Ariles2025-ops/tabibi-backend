package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Double reservation sur un vrai PostgreSQL : UPDATE conditionnel du creneau et index unique partiel
 * rendez_vous(creneau_id) des rendez-vous non annules (migration 021).
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class ReservationConcurrentePostgresIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired RendezVousService service;
    @Autowired CreneauRepository creneaux;
    @Autowired RendezVousRepository rendezVous;

    private Creneau creneauLibre() {
        return creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES), 20, true));
    }

    @Test
    void deux_reservations_simultanees_une_seule_reussit() throws Exception {
        Creneau creneau = creneauLibre();
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<RendezVous>> essais = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID patient = UUID.randomUUID();
            essais.add(pool.submit(() -> {
                depart.await();
                return service.reserverCreneau(patient, creneau.id());
            }));
        }
        depart.countDown();
        int reussites = 0;
        int refus = 0;
        for (Future<RendezVous> essai : essais) {
            try {
                essai.get(30, TimeUnit.SECONDS);
                reussites++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(CreneauDejaReserveException.class,
                        DataIntegrityViolationException.class);
                refus++;
            }
        }
        pool.shutdownNow();

        assertThat(reussites).isEqualTo(1);
        assertThat(refus).isEqualTo(1);
        assertThat(rendezVous.parMedecin(creneau.medecinId())).hasSize(1);
        assertThat(creneaux.parId(creneau.id()).orElseThrow().disponible()).isFalse();
    }

    @Test
    void l_index_refuse_deux_rendezvous_actifs_sur_le_meme_creneau_mais_pas_apres_annulation() {
        Creneau creneau = creneauLibre();
        RendezVous premier = rendezVous.enregistrer(
                RendezVous.confirmer(UUID.randomUUID(), creneau.medecinId(), creneau.debut(), creneau.id()));

        assertThatThrownBy(() -> rendezVous.enregistrer(
                RendezVous.confirmer(UUID.randomUUID(), creneau.medecinId(), creneau.debut(), creneau.id())))
                .isInstanceOf(DataIntegrityViolationException.class);

        premier.annuler();
        rendezVous.enregistrer(premier);
        rendezVous.enregistrer(
                RendezVous.confirmer(UUID.randomUUID(), creneau.medecinId(), creneau.debut(), creneau.id()));
        assertThat(rendezVous.parMedecin(creneau.medecinId())).hasSize(2);
    }
}
