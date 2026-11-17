package dz.tabibi.backend.rendezvous;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RendezVousHonorerMessageTest {

    @Test
    void honorer_un_rendezvous_absent_affiche_le_libelle_du_statut() {
        RendezVous rdv = new RendezVous(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minusSeconds(3600), StatutRdv.ABSENT);

        assertThatThrownBy(rdv::honorer)
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessage("Seul un rendez-vous confirmé peut être honoré (statut actuel : absent).");
    }

    @Test
    void honorer_un_rendezvous_futur_a_l_accent_sur_marque() {
        RendezVous rdv = new RendezVous(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), StatutRdv.CONFIRME);

        assertThatThrownBy(rdv::honorer)
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessage("Ce rendez-vous n'a pas encore eu lieu : il ne peut pas être marqué comme honoré.");
    }
}
