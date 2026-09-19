package dz.tabibi.backend.avis;

import dz.tabibi.backend.avis.adapter.EnMemoireAvisRepository;
import dz.tabibi.backend.avis.application.AvisService;
import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisIntrouvableException;
import dz.tabibi.backend.avis.domain.AvisInvalideException;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvisServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final Instant DEBUT = Instant.parse("2026-09-01T09:00:00Z");

    private final AvisRepository avis = new EnMemoireAvisRepository();
    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final AvisService service = new AvisService(avis, rendezVous);

    /** Rendez-vous honore entre ce patient et ce medecin, enregistre directement. */
    private RendezVous honore(UUID patient, UUID medecin) {
        RendezVous rdv = RendezVous.confirmer(patient, medecin, DEBUT);
        rdv.honorer();
        return rendezVous.enregistrer(rdv);
    }

    /** Avis depose par le service sur un rendez-vous honore frais. */
    private Avis depose(UUID patient, UUID medecin, int note, String commentaire) {
        return service.deposer(patient, honore(patient, medecin).id(), note, commentaire);
    }

    /** Avis date, enregistre directement (sans passer par le service). */
    private Avis deposeLe(UUID patient, UUID medecin, int note, String date) {
        return avis.enregistrer(Avis.deposer(UUID.randomUUID(), patient, medecin, note, null, Instant.parse(date)));
    }

    @Test
    void depose_un_avis_publie_sur_un_rendezvous_honore() {
        RendezVous rdv = honore(PATIENT, MEDECIN);

        Avis a = service.deposer(PATIENT, rdv.id(), 5, " Excellent accueil. ");

        assertThat(a.rendezVousId()).isEqualTo(rdv.id());
        assertThat(a.patientId()).isEqualTo(PATIENT);
        assertThat(a.medecinId()).isEqualTo(MEDECIN);
        assertThat(a.note()).isEqualTo(5);
        assertThat(a.commentaire()).isEqualTo("Excellent accueil.");
        assertThat(a.statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThat(a.deposeLe()).isNotNull();
        assertThat(avis.parId(a.id())).contains(a);
        assertThat(avis.parRendezVous(rdv.id())).contains(a);
        assertThat(service.mesAvis(PATIENT)).containsExactly(a);
    }

    @Test
    void refuse_une_note_hors_bornes_ou_un_commentaire_trop_long() {
        RendezVous rdv = honore(PATIENT, MEDECIN);

        assertThatThrownBy(() -> service.deposer(PATIENT, rdv.id(), 0, null)).isInstanceOf(AvisInvalideException.class);
        assertThatThrownBy(() -> service.deposer(PATIENT, rdv.id(), 6, null)).isInstanceOf(AvisInvalideException.class);
        assertThatThrownBy(() -> service.deposer(PATIENT, rdv.id(), 4, "a".repeat(501)))
                .isInstanceOf(AvisInvalideException.class);
        assertThatThrownBy(() -> service.deposer(PATIENT, null, 4, null)).isInstanceOf(AvisInvalideException.class);
        assertThat(avis.parRendezVous(rdv.id())).isEmpty();
        assertThat(service.mesAvis(PATIENT)).isEmpty();
    }

    @Test
    void refuse_un_rendezvous_inconnu() {
        assertThatThrownBy(() -> service.deposer(PATIENT, UUID.randomUUID(), 4, null))
                .isInstanceOf(RendezVousIntrouvableException.class);
    }

    @Test
    void refuse_le_rendezvous_d_un_autre_patient() {
        RendezVous rdv = honore(UUID.randomUUID(), MEDECIN);

        assertThatThrownBy(() -> service.deposer(PATIENT, rdv.id(), 4, null)).isInstanceOf(AccesRefuseException.class);
        assertThat(avis.parRendezVous(rdv.id())).isEmpty();
    }

    @Test
    void refuse_un_rendezvous_qui_n_est_pas_honore() {
        RendezVous confirme = rendezVous.enregistrer(RendezVous.confirmer(PATIENT, MEDECIN, DEBUT));
        RendezVous annule = rendezVous.enregistrer(RendezVous.confirmer(PATIENT, MEDECIN, DEBUT.plusSeconds(3600)));
        annule.annuler();
        rendezVous.enregistrer(annule);

        assertThatThrownBy(() -> service.deposer(PATIENT, confirme.id(), 4, null)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.deposer(PATIENT, annule.id(), 4, null)).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.mesAvis(PATIENT)).isEmpty();
    }

    @Test
    void refuse_un_second_avis_pour_le_meme_rendezvous() {
        RendezVous rdv = honore(PATIENT, MEDECIN);
        service.deposer(PATIENT, rdv.id(), 4, null);

        assertThatThrownBy(() -> service.deposer(PATIENT, rdv.id(), 2, "Finalement non."))
                .isInstanceOf(TransitionInvalideException.class);
        assertThat(service.mesAvis(PATIENT)).hasSize(1);
        assertThat(service.mesAvis(PATIENT).get(0).note()).isEqualTo(4);
    }

    @Test
    void mes_avis_sont_les_miens_tous_statuts_les_plus_recents_d_abord() {
        Avis ancien = deposeLe(PATIENT, MEDECIN, 3, "2025-01-10T09:00:00Z");
        Avis recent = deposeLe(PATIENT, UUID.randomUUID(), 5, "2025-03-01T09:00:00Z");
        deposeLe(UUID.randomUUID(), MEDECIN, 1, "2025-02-01T09:00:00Z"); // un autre patient
        Avis masque = service.masquer(ancien.id());

        assertThat(service.mesAvis(PATIENT)).containsExactly(recent, masque);
        assertThat(service.mesAvis(UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_synthese_publique_compte_les_avis_publies_du_medecin_seulement() {
        deposeLe(PATIENT, MEDECIN, 5, "2025-01-10T09:00:00Z");
        Avis recent = deposeLe(UUID.randomUUID(), MEDECIN, 4, "2025-03-01T09:00:00Z");
        Avis signale = deposeLe(UUID.randomUUID(), MEDECIN, 1, "2025-02-01T09:00:00Z");
        Avis masque = deposeLe(UUID.randomUUID(), MEDECIN, 1, "2025-02-15T09:00:00Z");
        deposeLe(UUID.randomUUID(), UUID.randomUUID(), 1, "2025-02-20T09:00:00Z"); // un autre medecin
        service.signaler(MEDECIN, signale.id());
        service.masquer(masque.id());

        SyntheseAvis synthese = service.avisPublics(MEDECIN);

        assertThat(synthese.nombre()).isEqualTo(2);
        assertThat(synthese.moyenne()).isEqualTo(4.5);
        assertThat(synthese.avis()).hasSize(2);
        assertThat(synthese.avis().get(0)).isEqualTo(recent);
        assertThat(synthese.avis()).allMatch(Avis::estPublie);
    }

    @Test
    void la_synthese_d_un_medecin_sans_avis_est_vide() {
        SyntheseAvis synthese = service.avisPublics(UUID.randomUUID());

        assertThat(synthese.nombre()).isEqualTo(0);
        assertThat(synthese.moyenne()).isNull();
        assertThat(synthese.avis()).isEmpty();
    }

    @Test
    void retablir_un_avis_masque_le_remet_dans_la_synthese() {
        Avis a = depose(PATIENT, MEDECIN, 4, null);
        service.masquer(a.id());
        assertThat(service.avisPublics(MEDECIN).nombre()).isEqualTo(0);

        Avis retabli = service.retablir(a.id());

        assertThat(retabli.statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThat(service.avisPublics(MEDECIN).nombre()).isEqualTo(1);
        assertThat(service.avisPublics(MEDECIN).moyenne()).isEqualTo(4.0);
    }

    @Test
    void signaler_par_le_medecin_concerne_retire_l_avis_de_la_synthese() {
        Avis a = depose(PATIENT, MEDECIN, 1, "Inadmissible.");

        Avis signale = service.signaler(MEDECIN, a.id());

        assertThat(signale.id()).isEqualTo(a.id());
        assertThat(signale.statut()).isEqualTo(StatutAvis.SIGNALE);
        assertThat(avis.parId(a.id()).orElseThrow().statut()).isEqualTo(StatutAvis.SIGNALE);
        assertThat(service.avisPublics(MEDECIN).nombre()).isEqualTo(0);
        assertThat(service.lister(Optional.of(StatutAvis.SIGNALE))).containsExactly(signale);
        assertThatThrownBy(() -> service.signaler(MEDECIN, a.id())).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void signaler_est_refuse_a_un_autre_medecin_et_pour_un_avis_inconnu() {
        Avis a = depose(PATIENT, MEDECIN, 1, null);

        assertThatThrownBy(() -> service.signaler(UUID.randomUUID(), a.id())).isInstanceOf(AccesRefuseException.class);
        assertThatThrownBy(() -> service.signaler(MEDECIN, UUID.randomUUID())).isInstanceOf(AvisIntrouvableException.class);
        assertThat(avis.parId(a.id()).orElseThrow().statut()).isEqualTo(StatutAvis.PUBLIE);
    }

    @Test
    void masquer_et_retablir_suivent_les_transitions_du_domaine() {
        Avis a = depose(PATIENT, MEDECIN, 2, null);

        Avis masque = service.masquer(a.id());

        assertThat(masque.statut()).isEqualTo(StatutAvis.MASQUE);
        assertThatThrownBy(() -> service.masquer(a.id())).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.retablir(a.id()).statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThatThrownBy(() -> service.retablir(a.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.masquer(UUID.randomUUID())).isInstanceOf(AvisIntrouvableException.class);
        assertThatThrownBy(() -> service.retablir(UUID.randomUUID())).isInstanceOf(AvisIntrouvableException.class);
    }

    @Test
    void lister_pour_l_admin_les_plus_anciens_d_abord_avec_filtre_par_statut() {
        Avis recent = deposeLe(PATIENT, MEDECIN, 3, "2025-03-01T09:00:00Z");
        Avis ancien = deposeLe(UUID.randomUUID(), MEDECIN, 5, "2025-01-10T09:00:00Z");
        Avis milieu = deposeLe(UUID.randomUUID(), UUID.randomUUID(), 1, "2025-02-01T09:00:00Z");
        Avis masque = service.masquer(milieu.id());

        List<Avis> tous = service.lister(Optional.empty());

        assertThat(tous).containsExactly(ancien, masque, recent);
        assertThat(service.lister(Optional.of(StatutAvis.PUBLIE))).containsExactly(ancien, recent);
        assertThat(service.lister(Optional.of(StatutAvis.MASQUE))).containsExactly(masque);
        assertThat(service.lister(Optional.of(StatutAvis.SIGNALE))).isEmpty();
    }
}
