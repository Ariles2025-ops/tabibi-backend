package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendezVousServiceTest {

    private final RendezVousService service = new RendezVousService(new EnMemoireRendezVousRepository());

    @Test
    void reserve_un_creneau_libre() {
        UUID patient = UUID.randomUUID();
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");

        RendezVous rdv = service.reserver(patient, medecin, debut);

        assertThat(rdv.id()).isNotNull();
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.medecinId()).isEqualTo(medecin);
    }

    @Test
    void refuse_un_creneau_deja_pris() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");
        service.reserver(UUID.randomUUID(), medecin, debut);

        assertThatThrownBy(() -> service.reserver(UUID.randomUUID(), medecin, debut))
                .isInstanceOf(CreneauDejaReserveException.class);
    }
}
