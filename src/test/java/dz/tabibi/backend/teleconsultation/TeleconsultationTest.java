package dz.tabibi.backend.teleconsultation;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.teleconsultation.domain.StatutTeleconsultation;
import dz.tabibi.backend.teleconsultation.domain.Teleconsultation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Transitions de la teleconsultation : consentement du patient, demarrage, cloture, annulation. */
class TeleconsultationTest {

    private static final UUID RENDEZ_VOUS = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final String SALLE = "tabibi-0123456789abcdef0123456789abcdef";
    private static final Instant T1 = Instant.parse("2026-12-07T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-12-07T09:05:00Z");
    private static final Instant T3 = Instant.parse("2026-12-07T09:25:00Z");

    private static Teleconsultation planifiee() {
        return Teleconsultation.planifier(RENDEZ_VOUS, PATIENT, MEDECIN, SALLE);
    }

    private static Teleconsultation enCours() {
        Teleconsultation t = planifiee();
        t.consentir(T1);
        t.demarrer(T2);
        return t;
    }

    @Test
    void planifier_cree_une_teleconsultation_planifiee_sans_consentement() {
        Teleconsultation t = planifiee();

        assertThat(t.id()).isNotNull();
        assertThat(t.rendezVousId()).isEqualTo(RENDEZ_VOUS);
        assertThat(t.patientId()).isEqualTo(PATIENT);
        assertThat(t.medecinId()).isEqualTo(MEDECIN);
        assertThat(t.salleId()).isEqualTo(SALLE);
        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
        assertThat(t.patientAConsenti()).isFalse();
        assertThat(t.consentementPatientLe()).isNull();
        assertThat(t.creeLe()).isNotNull();
        assertThat(t.demarreeLe()).isNull();
        assertThat(t.termineeLe()).isNull();
        assertThat(t.estAnnulee()).isFalse();
    }

    @Test
    void consentir_enregistre_la_date_et_reste_idempotent() {
        Teleconsultation t = planifiee();

        t.consentir(T1);
        t.consentir(T2);

        assertThat(t.patientAConsenti()).isTrue();
        assertThat(t.consentementPatientLe()).isEqualTo(T1);
        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
    }

    @Test
    void refuse_le_consentement_apres_cloture_ou_annulation() {
        Teleconsultation terminee = enCours();
        terminee.terminer(T3);
        Teleconsultation annulee = planifiee();
        annulee.annuler();

        assertThatThrownBy(() -> terminee.consentir(T3)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> annulee.consentir(T3)).isInstanceOf(TransitionInvalideException.class);
        assertThat(annulee.patientAConsenti()).isFalse();
    }

    @Test
    void refuse_de_demarrer_sans_consentement() {
        Teleconsultation t = planifiee();

        assertThatThrownBy(() -> t.demarrer(T2))
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessageContaining("consenti");
        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
        assertThat(t.demarreeLe()).isNull();
    }

    @Test
    void demarre_apres_consentement() {
        Teleconsultation t = planifiee();
        t.consentir(T1);

        t.demarrer(T2);

        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.EN_COURS);
        assertThat(t.demarreeLe()).isEqualTo(T2);
        assertThat(t.termineeLe()).isNull();
    }

    @Test
    void refuse_de_demarrer_deux_fois() {
        Teleconsultation t = enCours();

        assertThatThrownBy(() -> t.demarrer(T3)).isInstanceOf(TransitionInvalideException.class);
        assertThat(t.demarreeLe()).isEqualTo(T2);
    }

    @Test
    void termine_une_teleconsultation_en_cours() {
        Teleconsultation t = enCours();

        t.terminer(T3);

        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.TERMINEE);
        assertThat(t.termineeLe()).isEqualTo(T3);
    }

    @Test
    void refuse_de_terminer_une_teleconsultation_qui_n_est_pas_en_cours() {
        Teleconsultation planifiee = planifiee();
        Teleconsultation terminee = enCours();
        terminee.terminer(T3);

        assertThatThrownBy(() -> planifiee.terminer(T3)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> terminee.terminer(T3)).isInstanceOf(TransitionInvalideException.class);
        assertThat(planifiee.statut()).isEqualTo(StatutTeleconsultation.PLANIFIEE);
    }

    @Test
    void annule_une_teleconsultation_planifiee() {
        Teleconsultation t = planifiee();
        t.consentir(T1);

        t.annuler();

        assertThat(t.statut()).isEqualTo(StatutTeleconsultation.ANNULEE);
        assertThat(t.estAnnulee()).isTrue();
    }

    @Test
    void refuse_d_annuler_une_teleconsultation_en_cours_ou_terminee() {
        Teleconsultation enCours = enCours();
        Teleconsultation terminee = enCours();
        terminee.terminer(T3);

        assertThatThrownBy(enCours::annuler).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(terminee::annuler).isInstanceOf(TransitionInvalideException.class);
        assertThat(enCours.statut()).isEqualTo(StatutTeleconsultation.EN_COURS);
    }

    @Test
    void appartient_au_patient_et_est_avec_le_medecin() {
        Teleconsultation t = planifiee();

        assertThat(t.appartientA(PATIENT)).isTrue();
        assertThat(t.appartientA(MEDECIN)).isFalse();
        assertThat(t.estAvec(MEDECIN)).isTrue();
        assertThat(t.estAvec(PATIENT)).isFalse();
    }

    @Test
    void la_salle_est_accessible_au_medecin_toujours_au_patient_apres_consentement_a_un_tiers_jamais() {
        Teleconsultation t = planifiee();

        assertThat(t.peutAccederALaSalle(MEDECIN)).isTrue();
        assertThat(t.peutAccederALaSalle(PATIENT)).isFalse();
        assertThat(t.peutAccederALaSalle(UUID.randomUUID())).isFalse();

        t.consentir(T1);

        assertThat(t.peutAccederALaSalle(PATIENT)).isTrue();
        assertThat(t.peutAccederALaSalle(UUID.randomUUID())).isFalse();
    }
}
