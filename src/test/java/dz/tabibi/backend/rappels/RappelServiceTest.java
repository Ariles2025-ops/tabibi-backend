package dz.tabibi.backend.rappels;

import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rappels.application.RappelService;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rappels a heure fixe : un rendez-vous confirme qui commence dans les 24 prochaines heures vaut un
 * rappel au patient, une seule fois ; les autres (plus lointains, passes, deja rappeles, annules, honores)
 * sont ignores.
 */
class RappelServiceTest {

    /** 6 decembre 2026, 10:00 UTC (11:00 a Alger). */
    private static final Instant MAINTENANT = Instant.parse("2026-12-06T10:00:00Z");

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();

        @Override
        public void notifier(UUID destinataireId, String sujet, String message) {
            appels.add(new Appel(destinataireId, sujet, message));
        }

        List<Appel> pour(UUID destinataireId) {
            return appels.stream().filter(a -> a.destinataireId().equals(destinataireId)).toList();
        }
    }

    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final RappelService service =
            new RappelService(rendezVous, notifieur, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    /** Rendez-vous confirme enregistre, qui commence dans ce delai a partir de l'heure fixe. */
    private RendezVous confirmeDans(Duration delai) {
        return rendezVous.enregistrer(RendezVous.confirmer(UUID.randomUUID(), UUID.randomUUID(), MAINTENANT.plus(delai)));
    }

    private RendezVous recharge(RendezVous rdv) {
        return rendezVous.parId(rdv.id()).orElseThrow();
    }

    @Test
    void un_rendezvous_dans_deux_heures_est_rappele_au_patient_et_marque() {
        RendezVous rdv = confirmeDans(Duration.ofHours(2));
        assertThat(rdv.rappelEnvoye()).isFalse();

        int nombre = service.executer();

        assertThat(nombre).isEqualTo(1);
        assertThat(notifieur.appels).hasSize(1);
        assertThat(notifieur.pour(rdv.patientId()).get(0).sujet()).isEqualTo("Rappel de rendez-vous");
        assertThat(notifieur.pour(rdv.patientId()).get(0).message())
                .isEqualTo("Votre rendez-vous du 06/12/2026 a 13:00 est demain. Pensez a vous presenter 10 minutes en avance.");
        assertThat(recharge(rdv).rappelEnvoye()).isTrue();
        assertThat(recharge(rdv).rappelEnvoyeLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void un_rendezvous_dans_trente_heures_est_ignore() {
        RendezVous rdv = confirmeDans(Duration.ofHours(30));

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(rdv).rappelEnvoye()).isFalse();
    }

    @Test
    void la_fenetre_va_de_maintenant_inclus_a_vingt_quatre_heures_exclu() {
        RendezVous aLInstant = confirmeDans(Duration.ZERO);
        RendezVous presqueDemain = confirmeDans(Duration.ofHours(23).plusMinutes(59));
        RendezVous demainMemeHeure = confirmeDans(Duration.ofHours(24));
        RendezVous passe = confirmeDans(Duration.ofMinutes(-1));

        assertThat(service.executer()).isEqualTo(2);
        assertThat(recharge(aLInstant).rappelEnvoye()).isTrue();
        assertThat(recharge(presqueDemain).rappelEnvoye()).isTrue();
        assertThat(recharge(demainMemeHeure).rappelEnvoye()).isFalse();
        assertThat(recharge(passe).rappelEnvoye()).isFalse();
        assertThat(notifieur.pour(passe.patientId())).isEmpty();
    }

    @Test
    void un_rendezvous_deja_rappele_est_ignore() {
        RendezVous rdv = confirmeDans(Duration.ofHours(5));
        rdv.marquerRappelEnvoye(MAINTENANT.minus(Duration.ofHours(1)));
        rendezVous.enregistrer(rdv);

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(rdv).rappelEnvoyeLe()).isEqualTo(MAINTENANT.minus(Duration.ofHours(1)));
    }

    @Test
    void un_rendezvous_annule_ou_honore_est_ignore() {
        RendezVous annule = confirmeDans(Duration.ofHours(3));
        annule.annuler();
        rendezVous.enregistrer(annule);
        RendezVous honore = confirmeDans(Duration.ofHours(4));
        honore.honorer();
        rendezVous.enregistrer(honore);

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(annule).rappelEnvoye()).isFalse();
        assertThat(recharge(honore).rappelEnvoye()).isFalse();
    }

    @Test
    void deux_executions_n_envoient_le_rappel_qu_une_fois() {
        RendezVous premier = confirmeDans(Duration.ofHours(2));
        RendezVous second = confirmeDans(Duration.ofHours(12));

        assertThat(service.executer()).isEqualTo(2);
        assertThat(service.executer()).isEqualTo(0);

        assertThat(notifieur.appels).hasSize(2);
        assertThat(notifieur.pour(premier.patientId())).hasSize(1);
        assertThat(notifieur.pour(second.patientId())).hasSize(1);
    }

    @Test
    void sans_rendezvous_a_rappeler_rien_n_est_envoye() {
        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
    }
}
