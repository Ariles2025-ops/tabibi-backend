package dz.tabibi.backend.dawini;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.StatutBesoin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regles du domaine : medicament et wilaya obligatoires, longueurs bornees, cloture unique. */
class BesoinMedicamentTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final Instant PUBLIE_LE = Instant.parse("2026-09-18T10:00:00Z");
    private static final Instant CLOTURE_LE = Instant.parse("2026-09-19T10:00:00Z");
    private static final DemandeBesoin DEMANDE =
            new DemandeBesoin("Insuline glargine 100 UI/ml", "16", "Bab Ezzouar", "Stylo prerempli, urgent");

    private static BesoinMedicament publie() {
        return BesoinMedicament.publier(PATIENT, DEMANDE, PUBLIE_LE);
    }

    @Test
    void publier_cree_un_besoin_ouvert() {
        BesoinMedicament b = publie();

        assertThat(b.id()).isNotNull();
        assertThat(b.patientId()).isEqualTo(PATIENT);
        assertThat(b.medicament()).isEqualTo("Insuline glargine 100 UI/ml");
        assertThat(b.wilayaCode()).isEqualTo("16");
        assertThat(b.commune()).isEqualTo("Bab Ezzouar");
        assertThat(b.precision()).isEqualTo("Stylo prerempli, urgent");
        assertThat(b.statut()).isEqualTo(StatutBesoin.OUVERT);
        assertThat(b.estOuvert()).isTrue();
        assertThat(b.publieLe()).isEqualTo(PUBLIE_LE);
        assertThat(b.clotureLe()).isNull();
        assertThat(b.estDe(PATIENT)).isTrue();
        assertThat(b.estDe(UUID.randomUUID())).isFalse();
    }

    @Test
    void publier_nettoie_les_espaces_et_vide_les_champs_facultatifs_blancs() {
        BesoinMedicament b = BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("  Paracetamol 1 g ", " 31 ", "  ", null), PUBLIE_LE);

        assertThat(b.medicament()).isEqualTo("Paracetamol 1 g");
        assertThat(b.wilayaCode()).isEqualTo("31");
        assertThat(b.commune()).isNull();
        assertThat(b.precision()).isNull();
    }

    @Test
    void publier_exige_le_medicament_et_la_wilaya() {
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT, new DemandeBesoin(" ", "16", null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT, new DemandeBesoin(null, "16", null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT, new DemandeBesoin("Paracetamol", "", null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT, new DemandeBesoin("Paracetamol", null, null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT, null, PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(null, DEMANDE, PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
    }

    @Test
    void publier_borne_les_longueurs() {
        String medicament200 = "a".repeat(BesoinMedicament.LONGUEUR_MAX_MEDICAMENT);
        String precision500 = "b".repeat(BesoinMedicament.LONGUEUR_MAX_PRECISION);

        BesoinMedicament borne = BesoinMedicament.publier(PATIENT,
                new DemandeBesoin(medicament200, "1601", null, precision500), PUBLIE_LE);

        assertThat(borne.medicament()).hasSize(200);
        assertThat(borne.precision()).hasSize(500);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT,
                new DemandeBesoin(medicament200 + "a", "16", null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("Paracetamol", "16", null, precision500 + "b"), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("Paracetamol", "Alger", null, null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> BesoinMedicament.publier(PATIENT,
                new DemandeBesoin("Paracetamol", "16", "c".repeat(121), null), PUBLIE_LE))
                .isInstanceOf(BesoinInvalideException.class);
    }

    @Test
    void cloturer_produit_une_copie_cloturee_datee() {
        BesoinMedicament b = publie();

        BesoinMedicament cloture = b.cloturer(CLOTURE_LE);

        assertThat(cloture.id()).isEqualTo(b.id());
        assertThat(cloture.statut()).isEqualTo(StatutBesoin.CLOTURE);
        assertThat(cloture.estOuvert()).isFalse();
        assertThat(cloture.clotureLe()).isEqualTo(CLOTURE_LE);
        assertThat(cloture.medicament()).isEqualTo(b.medicament());
        assertThat(b.statut()).isEqualTo(StatutBesoin.OUVERT);
        assertThat(b.clotureLe()).isNull();
    }

    @Test
    void cloturer_deux_fois_est_refuse() {
        BesoinMedicament cloture = publie().cloturer(CLOTURE_LE);

        assertThatThrownBy(() -> cloture.cloturer(CLOTURE_LE.plusSeconds(60)))
                .isInstanceOf(TransitionInvalideException.class);
    }
}
