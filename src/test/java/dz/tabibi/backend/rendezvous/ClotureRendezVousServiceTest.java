package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.ClotureRendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cloture automatique : seuls les rendez-vous CONFIRME commences depuis plus de 24 h passent a HONORE. */
class ClotureRendezVousServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-12-06T10:00:00Z");

    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final ClotureRendezVousService service =
            new ClotureRendezVousService(rendezVous, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private RendezVous rdv(Duration decalage, StatutRdv statut) {
        return rendezVous.enregistrer(new RendezVous(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MAINTENANT.plus(decalage), statut));
    }

    private StatutRdv statutDe(RendezVous r) {
        return rendezVous.parId(r.id()).orElseThrow().statut();
    }

    @Test
    void clot_un_rendezvous_confirme_d_hier() {
        RendezVous hier = rdv(Duration.ofHours(-25), StatutRdv.CONFIRME);

        assertThat(service.cloturerLesRendezVousPasses()).isEqualTo(1);
        assertThat(statutDe(hier)).isEqualTo(StatutRdv.HONORE);
    }

    @Test
    void laisse_inchanges_les_rendezvous_recents_ou_a_venir() {
        RendezVous dansUneHeure = rdv(Duration.ofHours(1), StatutRdv.CONFIRME);
        RendezVous ilYaDeuxHeures = rdv(Duration.ofHours(-2), StatutRdv.CONFIRME);

        assertThat(service.cloturerLesRendezVousPasses()).isZero();
        assertThat(statutDe(dansUneHeure)).isEqualTo(StatutRdv.CONFIRME);
        assertThat(statutDe(ilYaDeuxHeures)).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    void laisse_inchanges_les_rendezvous_annules_absents_ou_deja_honores() {
        RendezVous annule = rdv(Duration.ofDays(-3), StatutRdv.ANNULE);
        RendezVous absent = rdv(Duration.ofDays(-3), StatutRdv.ABSENT);
        RendezVous honore = rdv(Duration.ofDays(-3), StatutRdv.HONORE);

        assertThat(service.cloturerLesRendezVousPasses()).isZero();
        assertThat(statutDe(annule)).isEqualTo(StatutRdv.ANNULE);
        assertThat(statutDe(absent)).isEqualTo(StatutRdv.ABSENT);
        assertThat(statutDe(honore)).isEqualTo(StatutRdv.HONORE);
    }
}
