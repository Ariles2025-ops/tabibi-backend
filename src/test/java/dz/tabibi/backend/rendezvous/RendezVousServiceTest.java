package dz.tabibi.backend.rendezvous;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendezVousServiceTest {

    private static final UUID MEDECIN_DEMO = EnMemoireMedecinRepository.MEDECINS_DEMO.get(0).id();

    private final CreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final RendezVousService service =
            new RendezVousService(new EnMemoireRendezVousRepository(), creneaux);

    private Creneau premierCreneauDisponible() {
        return creneaux.disponiblesPour(MEDECIN_DEMO).get(0);
    }

    private boolean estDisponible(UUID creneauId) {
        return creneaux.parId(creneauId).orElseThrow().disponible();
    }

    @Test
    void reserve_un_creneau_libre() {
        UUID patient = UUID.randomUUID();
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");

        RendezVous rdv = service.reserver(patient, medecin, debut);

        assertThat(rdv.id()).isNotNull();
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.medecinId()).isEqualTo(medecin);
        assertThat(rdv.creneauId()).isNull();
    }

    @Test
    void refuse_un_creneau_deja_pris() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.parse("2026-12-04T09:00:00Z");
        service.reserver(UUID.randomUUID(), medecin, debut);

        assertThatThrownBy(() -> service.reserver(UUID.randomUUID(), medecin, debut))
                .isInstanceOf(CreneauDejaReserveException.class);
    }

    @Test
    void reserve_un_creneau_disponible_de_l_agenda() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();

        RendezVous rdv = service.reserverCreneau(patient, creneau.id());

        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(rdv.patientId()).isEqualTo(patient);
        assertThat(rdv.medecinId()).isEqualTo(creneau.medecinId());
        assertThat(rdv.debut()).isEqualTo(creneau.debut());
        assertThat(rdv.creneauId()).isEqualTo(creneau.id());
        assertThat(estDisponible(creneau.id())).isFalse();
        assertThat(creneaux.disponiblesPour(MEDECIN_DEMO)).noneMatch(c -> c.id().equals(creneau.id()));
    }

    @Test
    void refuse_de_reserver_deux_fois_le_meme_creneau() {
        Creneau creneau = premierCreneauDisponible();
        service.reserverCreneau(UUID.randomUUID(), creneau.id());

        assertThatThrownBy(() -> service.reserverCreneau(UUID.randomUUID(), creneau.id()))
                .isInstanceOf(CreneauDejaReserveException.class);
    }

    @Test
    void refuse_un_creneau_inconnu() {
        assertThatThrownBy(() -> service.reserverCreneau(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(CreneauIntrouvableException.class);
    }

    @Test
    void liste_les_rendezvous_du_patient_par_date() {
        UUID patient = UUID.randomUUID();
        List<Creneau> disponibles = creneaux.disponiblesPour(MEDECIN_DEMO);
        service.reserverCreneau(patient, disponibles.get(1).id());
        service.reserverCreneau(patient, disponibles.get(0).id());
        service.reserverCreneau(UUID.randomUUID(), disponibles.get(2).id());

        List<RendezVous> mes = service.mesRendezVous(patient);

        assertThat(mes).hasSize(2);
        assertThat(mes).allMatch(r -> r.patientId().equals(patient));
        assertThat(mes).isSortedAccordingTo(Comparator.comparing(RendezVous::debut));
    }

    @Test
    void annule_et_remet_le_creneau_a_disposition() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(patient, creneau.id());

        RendezVous annule = service.annuler(patient, rdv.id());

        assertThat(annule.id()).isEqualTo(rdv.id());
        assertThat(annule.statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(estDisponible(creneau.id())).isTrue();
        assertThat(creneaux.disponiblesPour(MEDECIN_DEMO)).anyMatch(c -> c.id().equals(creneau.id()));
        assertThat(service.mesRendezVous(patient)).allMatch(r -> r.statut() == StatutRdv.ANNULE);
    }

    @Test
    void refuse_l_annulation_par_un_autre_patient() {
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(UUID.randomUUID(), creneau.id());

        assertThatThrownBy(() -> service.annuler(UUID.randomUUID(), rdv.id()))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(rdv.statut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(estDisponible(creneau.id())).isFalse();
    }

    @Test
    void refuse_d_annuler_un_rendezvous_inconnu() {
        assertThatThrownBy(() -> service.annuler(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void annuler_deux_fois_ne_relibere_pas_un_creneau_repris_entre_temps() {
        UUID patient = UUID.randomUUID();
        Creneau creneau = premierCreneauDisponible();
        RendezVous rdv = service.reserverCreneau(patient, creneau.id());
        service.annuler(patient, rdv.id());
        service.reserverCreneau(UUID.randomUUID(), creneau.id()); // un autre patient reprend le creneau

        service.annuler(patient, rdv.id());

        assertThat(estDisponible(creneau.id())).isFalse();
    }
}
