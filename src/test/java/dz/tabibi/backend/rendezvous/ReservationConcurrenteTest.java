package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Double reservation : deux patients qui reservent le meme creneau au meme instant. Les deux lisent
 * le creneau « disponible » avant que l'un ou l'autre n'ecrive (barriere) : seule la reservation
 * atomique du creneau (disponible -> pris, en une operation) laisse passer une seule reservation.
 */
class ReservationConcurrenteTest {

    /** Depot de creneaux qui fait attendre chaque lecteur jusqu'a ce que les deux aient lu le creneau. */
    static class DepotSynchronise implements CreneauRepository {
        private final CreneauRepository depot;
        private final CyclicBarrier barriere;

        DepotSynchronise(CreneauRepository depot, int lecteurs) {
            this.depot = depot;
            this.barriere = new CyclicBarrier(lecteurs);
        }

        @Override
        public Optional<Creneau> parId(UUID id) {
            Optional<Creneau> lu = depot.parId(id);
            try {
                barriere.await(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return lu;
        }

        @Override public List<Creneau> disponiblesPour(UUID medecinId) { return depot.disponiblesPour(medecinId); }
        @Override public Creneau enregistrer(Creneau creneau) { return depot.enregistrer(creneau); }
        @Override public List<Creneau> aVenirPour(UUID medecinId, Instant depuis) { return depot.aVenirPour(medecinId, depuis); }
        @Override public void supprimer(UUID id) { depot.supprimer(id); }
        @Override public boolean supprimerSiDisponible(UUID id) { return depot.supprimerSiDisponible(id); }
        @Override public int supprimerLibresDuMedecin(UUID medecinId) { return depot.supprimerLibresDuMedecin(medecinId); }
        @Override public boolean reserverSiDisponible(UUID id) { return depot.reserverSiDisponible(id); }
    }

    @Test
    void deux_reservations_simultanees_du_meme_creneau_une_seule_reussit() throws Exception {
        EnMemoireCreneauRepository creneaux = new EnMemoireCreneauRepository();
        Creneau creneau = creneaux.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(86_400), 20, true));
        EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
        RendezVousService service = new RendezVousService(rdvs, new DepotSynchronise(creneaux, 2),
                (d, s, m) -> { }, (medecinId, debut) -> { });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<RendezVous>> essais = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID patient = UUID.randomUUID();
            essais.add(pool.submit(() -> service.reserverCreneau(patient, creneau.id())));
        }
        int reussites = 0;
        int refus = 0;
        for (Future<RendezVous> essai : essais) {
            try {
                essai.get(10, TimeUnit.SECONDS);
                reussites++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(CreneauDejaReserveException.class);
                refus++;
            }
        }
        pool.shutdownNow();

        assertThat(reussites).isEqualTo(1);
        assertThat(refus).isEqualTo(1);
        assertThat(rdvs.parMedecin(creneau.medecinId())).hasSize(1);
        assertThat(creneaux.parId(creneau.id()).orElseThrow().disponible()).isFalse();
    }
}
