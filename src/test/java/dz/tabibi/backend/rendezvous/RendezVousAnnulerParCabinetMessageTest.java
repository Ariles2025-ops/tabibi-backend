package dz.tabibi.backend.rendezvous;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RendezVousAnnulerParCabinetMessageTest {

    @Test
    void annuler_un_rendezvous_deja_annule_affiche_le_libelle_du_statut() {
        RendezVous rdv = new RendezVous(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), StatutRdv.ANNULE);

        assertThatThrownBy(rdv::annulerParCabinet)
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessage("Seul un rendez-vous confirmé peut être annulé par le cabinet (statut actuel : annulé).");
    }
}
