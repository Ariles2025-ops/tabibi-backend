package dz.tabibi.backend.administration;

import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regles du domaine : donnees obligatoires au depot, validation et refus depuis EN_ATTENTE seulement. */
class CandidatureMedecinTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final Instant DEPOSEE_LE = Instant.parse("2026-09-18T10:00:00Z");
    private static final Instant TRAITEE_LE = Instant.parse("2026-09-19T10:00:00Z");
    private static final DemandeCandidature DEMANDE = new DemandeCandidature(
            "Dr Nadia Bensalem", "cardiologue", "Cardiologue", "16", "Alger", "Hydra", "16-12345", "0550000000");

    private static CandidatureMedecin deposee() {
        return CandidatureMedecin.deposer(MEDECIN, DEMANDE, DEPOSEE_LE);
    }

    @Test
    void deposer_cree_une_candidature_en_attente() {
        CandidatureMedecin c = deposee();

        assertThat(c.id()).isNotNull();
        assertThat(c.medecinId()).isEqualTo(MEDECIN);
        assertThat(c.nomComplet()).isEqualTo("Dr Nadia Bensalem");
        assertThat(c.specialiteSlug()).isEqualTo("cardiologue");
        assertThat(c.specialiteFr()).isEqualTo("Cardiologue");
        assertThat(c.wilayaCode()).isEqualTo("16");
        assertThat(c.wilayaFr()).isEqualTo("Alger");
        assertThat(c.ville()).isEqualTo("Hydra");
        assertThat(c.numeroOrdre()).isEqualTo("16-12345");
        assertThat(c.telephone()).isEqualTo("0550000000");
        assertThat(c.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        assertThat(c.estEnAttente()).isTrue();
        assertThat(c.motifRefus()).isNull();
        assertThat(c.deposeeLe()).isEqualTo(DEPOSEE_LE);
        assertThat(c.traiteeLe()).isNull();
    }

    @Test
    void deposer_nettoie_les_espaces_et_vide_les_champs_facultatifs_blancs() {
        DemandeCandidature demande = new DemandeCandidature(
                "  Dr Nadia Bensalem ", " cardiologue", "  ", "16 ", null, " ", " 16-12345 ", "");

        CandidatureMedecin c = CandidatureMedecin.deposer(MEDECIN, demande, DEPOSEE_LE);

        assertThat(c.nomComplet()).isEqualTo("Dr Nadia Bensalem");
        assertThat(c.specialiteSlug()).isEqualTo("cardiologue");
        assertThat(c.specialiteFr()).isNull();
        assertThat(c.wilayaCode()).isEqualTo("16");
        assertThat(c.wilayaFr()).isNull();
        assertThat(c.ville()).isNull();
        assertThat(c.numeroOrdre()).isEqualTo("16-12345");
        assertThat(c.telephone()).isNull();
    }

    @Test
    void deposer_exige_le_nom_la_specialite_la_wilaya_et_le_numero_d_ordre() {
        assertThatThrownBy(() -> CandidatureMedecin.deposer(MEDECIN,
                new DemandeCandidature(" ", "cardiologue", null, "16", null, null, "16-12345", null), DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> CandidatureMedecin.deposer(MEDECIN,
                new DemandeCandidature("Dr X", null, null, "16", null, null, "16-12345", null), DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> CandidatureMedecin.deposer(MEDECIN,
                new DemandeCandidature("Dr X", "cardiologue", null, "", null, null, "16-12345", null), DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> CandidatureMedecin.deposer(MEDECIN,
                new DemandeCandidature("Dr X", "cardiologue", null, "16", null, null, null, null), DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> CandidatureMedecin.deposer(MEDECIN, null, DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> CandidatureMedecin.deposer(null, DEMANDE, DEPOSEE_LE))
                .isInstanceOf(CandidatureInvalideException.class);
    }

    @Test
    void valider_passe_la_candidature_a_validee_sans_modifier_l_originale() {
        CandidatureMedecin c = deposee();

        CandidatureMedecin validee = c.valider(TRAITEE_LE);

        assertThat(validee.id()).isEqualTo(c.id());
        assertThat(validee.statut()).isEqualTo(StatutCandidature.VALIDEE);
        assertThat(validee.estEnAttente()).isFalse();
        assertThat(validee.traiteeLe()).isEqualTo(TRAITEE_LE);
        assertThat(validee.motifRefus()).isNull();
        assertThat(validee.nomComplet()).isEqualTo(c.nomComplet());
        assertThat(c.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
    }

    @Test
    void refuser_passe_la_candidature_a_refusee_avec_le_motif() {
        CandidatureMedecin refusee = deposee().refuser("Numero d'ordre invalide.", TRAITEE_LE);

        assertThat(refusee.statut()).isEqualTo(StatutCandidature.REFUSEE);
        assertThat(refusee.motifRefus()).isEqualTo("Numero d'ordre invalide.");
        assertThat(refusee.traiteeLe()).isEqualTo(TRAITEE_LE);
    }

    @Test
    void refuser_exige_un_motif() {
        CandidatureMedecin c = deposee();

        assertThatThrownBy(() -> c.refuser(null, TRAITEE_LE)).isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> c.refuser("   ", TRAITEE_LE)).isInstanceOf(CandidatureInvalideException.class);
    }

    @Test
    void valider_et_refuser_exigent_une_candidature_en_attente() {
        CandidatureMedecin validee = deposee().valider(TRAITEE_LE);
        CandidatureMedecin refusee = deposee().refuser("Motif.", TRAITEE_LE);

        assertThatThrownBy(() -> validee.valider(TRAITEE_LE)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> validee.refuser("Motif.", TRAITEE_LE)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> refusee.valider(TRAITEE_LE)).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> refusee.refuser("Motif.", TRAITEE_LE)).isInstanceOf(TransitionInvalideException.class);
    }
}
