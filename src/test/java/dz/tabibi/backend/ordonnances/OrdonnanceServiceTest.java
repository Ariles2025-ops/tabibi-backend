package dz.tabibi.backend.ordonnances;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.ordonnances.adapter.EnMemoireOrdonnanceRepository;
import dz.tabibi.backend.ordonnances.application.OrdonnanceService;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
import dz.tabibi.backend.ordonnances.domain.StatutOrdonnance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdonnanceServiceTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final List<LigneOrdonnance> LIGNES = List.of(
            new LigneOrdonnance("Paracetamol 1 g", "1 comprime matin et soir", "5 jours"),
            new LigneOrdonnance("Amoxicilline 500 mg", "1 gelule toutes les 8 heures", "7 jours"));

    private final OrdonnanceRepository repository = new EnMemoireOrdonnanceRepository();
    private final OrdonnanceService service = new OrdonnanceService(repository);

    /** Ordonnance emise a une date choisie, enregistree directement (sans passer par le service). */
    private Ordonnance emiseLe(UUID patient, String date, StatutOrdonnance statut, String code) {
        return repository.enregistrer(new Ordonnance(
                UUID.randomUUID(), MEDECIN, patient, null, LIGNES, Instant.parse(date), code, statut));
    }

    @Test
    void emet_une_ordonnance_avec_un_code_de_8_caracteres_et_le_statut_emise() {
        Ordonnance o = service.emettre(MEDECIN, PATIENT, null, LIGNES);

        assertThat(o.id()).isNotNull();
        assertThat(o.codeVerification()).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(o.statut()).isEqualTo(StatutOrdonnance.EMISE);
        assertThat(o.emiseLe()).isNotNull();
        assertThat(o.medecinId()).isEqualTo(MEDECIN);
        assertThat(o.patientId()).isEqualTo(PATIENT);
        assertThat(o.rendezVousId()).isNull();
        assertThat(o.lignes()).containsExactlyElementsOf(LIGNES);
        assertThat(repository.parId(o.id())).contains(o);
    }

    @Test
    void rattache_l_ordonnance_au_rendezvous_indique() {
        UUID rendezVous = UUID.randomUUID();

        Ordonnance o = service.emettre(MEDECIN, PATIENT, rendezVous, LIGNES);

        assertThat(o.rendezVousId()).isEqualTo(rendezVous);
    }

    @Test
    void refuse_une_ordonnance_sans_ligne() {
        assertThatThrownBy(() -> service.emettre(MEDECIN, PATIENT, null, List.of()))
                .isInstanceOf(OrdonnanceInvalideException.class);
        assertThatThrownBy(() -> service.emettre(MEDECIN, PATIENT, null, null))
                .isInstanceOf(OrdonnanceInvalideException.class);
        assertThat(service.mesOrdonnances(PATIENT)).isEmpty();
    }

    @Test
    void refuse_une_ligne_sans_medicament() {
        List<LigneOrdonnance> lignes = List.of(new LigneOrdonnance("  ", "1 comprime par jour", "3 jours"));

        assertThatThrownBy(() -> service.emettre(MEDECIN, PATIENT, null, lignes))
                .isInstanceOf(OrdonnanceInvalideException.class);
    }

    @Test
    void refuse_une_ordonnance_sans_patient() {
        assertThatThrownBy(() -> service.emettre(MEDECIN, null, null, LIGNES))
                .isInstanceOf(OrdonnanceInvalideException.class);
    }

    @Test
    void attribue_un_code_distinct_a_chaque_ordonnance() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(service.emettre(MEDECIN, PATIENT, null, LIGNES).codeVerification());
        }
        assertThat(codes).hasSize(100);
    }

    @Test
    void mes_ordonnances_liste_celles_du_patient_les_plus_recentes_d_abord() {
        Ordonnance ancienne = emiseLe(PATIENT, "2026-01-10T09:00:00Z", StatutOrdonnance.EMISE, "AAAAAAA2");
        Ordonnance recente = emiseLe(PATIENT, "2026-03-01T09:00:00Z", StatutOrdonnance.EMISE, "AAAAAAA3");
        emiseLe(UUID.randomUUID(), "2026-02-01T09:00:00Z", StatutOrdonnance.EMISE, "AAAAAAA4");

        assertThat(service.mesOrdonnances(PATIENT)).containsExactly(recente, ancienne);
    }

    @Test
    void ordonnances_du_medecin_liste_celles_qu_il_a_redigees_les_plus_recentes_d_abord() {
        Ordonnance ancienne = emiseLe(PATIENT, "2026-01-10T09:00:00Z", StatutOrdonnance.EMISE, "DDDDDDD2");
        Ordonnance recente = emiseLe(UUID.randomUUID(), "2026-03-01T09:00:00Z", StatutOrdonnance.EMISE, "DDDDDDD3");
        repository.enregistrer(new Ordonnance(UUID.randomUUID(), UUID.randomUUID(), PATIENT, null, LIGNES,
                Instant.parse("2026-02-01T09:00:00Z"), "DDDDDDD4", StatutOrdonnance.EMISE)); // autre medecin

        assertThat(service.ordonnancesDuMedecin(MEDECIN)).containsExactly(recente, ancienne);
        assertThat(service.ordonnancesDuMedecin(UUID.randomUUID())).isEmpty();
    }

    @Test
    void parIdPour_est_accessible_au_patient_et_au_medecin_auteur() {
        Ordonnance o = service.emettre(MEDECIN, PATIENT, null, LIGNES);

        assertThat(service.parIdPour(PATIENT, o.id())).isEqualTo(o);
        assertThat(service.parIdPour(MEDECIN, o.id())).isEqualTo(o);
    }

    @Test
    void parIdPour_refuse_un_tiers() {
        Ordonnance o = service.emettre(MEDECIN, PATIENT, null, LIGNES);

        assertThatThrownBy(() -> service.parIdPour(UUID.randomUUID(), o.id()))
                .isInstanceOf(AccesRefuseException.class);
    }

    @Test
    void parIdPour_une_ordonnance_inconnue_est_introuvable() {
        assertThatThrownBy(() -> service.parIdPour(PATIENT, UUID.randomUUID()))
                .isInstanceOf(OrdonnanceIntrouvableException.class);
    }

    @Test
    void verifie_un_code_connu_sans_donnee_personnelle() {
        Ordonnance o = service.emettre(MEDECIN, PATIENT, null, LIGNES);

        ResultatVerification r = service.verifier(o.codeVerification());

        assertThat(r.valide()).isTrue();
        assertThat(r.emiseLe()).isEqualTo(o.emiseLe());
        assertThat(r.statut()).isEqualTo(StatutOrdonnance.EMISE);
    }

    @Test
    void accepte_un_code_saisi_en_minuscules_avec_des_espaces() {
        Ordonnance o = service.emettre(MEDECIN, PATIENT, null, LIGNES);

        ResultatVerification r = service.verifier(" " + o.codeVerification().toLowerCase(Locale.ROOT) + " ");

        assertThat(r.valide()).isTrue();
    }

    @Test
    void une_ordonnance_annulee_n_est_plus_valide() {
        emiseLe(PATIENT, "2026-01-10T09:00:00Z", StatutOrdonnance.ANNULEE, "BBBBBBB2");

        ResultatVerification r = service.verifier("BBBBBBB2");

        assertThat(r.valide()).isFalse();
        assertThat(r.statut()).isEqualTo(StatutOrdonnance.ANNULEE);
    }

    @Test
    void verifier_un_code_inconnu_est_introuvable() {
        assertThatThrownBy(() -> service.verifier("ZZZZZZZZ"))
                .isInstanceOf(OrdonnanceIntrouvableException.class);
    }
}
