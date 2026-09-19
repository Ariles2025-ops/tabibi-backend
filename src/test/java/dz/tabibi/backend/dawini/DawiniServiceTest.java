package dz.tabibi.backend.dawini;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.dawini.adapter.EnMemoireBesoinRepository;
import dz.tabibi.backend.dawini.adapter.EnMemoireReponseRepository;
import dz.tabibi.backend.dawini.application.DawiniService;
import dz.tabibi.backend.dawini.domain.BesoinIntrouvableException;
import dz.tabibi.backend.dawini.domain.BesoinInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.dawini.domain.ReponseInvalideException;
import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import dz.tabibi.backend.dawini.domain.ReponseRepository;
import dz.tabibi.backend.dawini.domain.StatutBesoin;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DawiniServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID PHARMACIE = UUID.randomUUID();
    private static final DemandeBesoin DEMANDE =
            new DemandeBesoin("Insuline glargine 100 UI/ml", "16", "Bab Ezzouar", "Stylo prerempli, urgent");
    private static final DemandeReponse REPONSE =
            new DemandeReponse("Pharmacie El Amel", true, 850, "En stock ce matin.");

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

    private final BesoinRepository besoins = new EnMemoireBesoinRepository();
    private final ReponseRepository reponses = new EnMemoireReponseRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final DawiniService service = new DawiniService(besoins, reponses, notifieur);

    /** Besoin date, enregistre directement (sans passer par le service). */
    private BesoinMedicament publieLe(UUID patient, String wilaya, String date) {
        return besoins.enregistrer(BesoinMedicament.publier(patient,
                new DemandeBesoin("Paracetamol 1 g", wilaya, null, null), Instant.parse(date)));
    }

    @Test
    void publie_un_besoin_ouvert() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);

        assertThat(b.patientId()).isEqualTo(PATIENT);
        assertThat(b.medicament()).isEqualTo("Insuline glargine 100 UI/ml");
        assertThat(b.wilayaCode()).isEqualTo("16");
        assertThat(b.statut()).isEqualTo(StatutBesoin.OUVERT);
        assertThat(b.publieLe()).isNotNull();
        assertThat(besoins.parId(b.id())).contains(b);
        assertThat(service.mesBesoins(PATIENT)).containsExactly(b);
        assertThat(service.besoinsOuverts("16")).containsExactly(b);
        assertThat(service.nombreReponses(b.id())).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void refuse_un_besoin_sans_medicament_ou_sans_wilaya() {
        assertThatThrownBy(() -> service.publier(PATIENT, new DemandeBesoin(" ", "16", null, null)))
                .isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> service.publier(PATIENT, new DemandeBesoin("Paracetamol", null, null, null)))
                .isInstanceOf(BesoinInvalideException.class);
        assertThat(service.mesBesoins(PATIENT)).isEmpty();
    }

    @Test
    void mes_besoins_sont_les_miens_tous_statuts_les_plus_recents_d_abord() {
        BesoinMedicament ancien = publieLe(PATIENT, "16", "2025-01-10T09:00:00Z");
        BesoinMedicament recent = publieLe(PATIENT, "31", "2025-03-01T09:00:00Z");
        publieLe(UUID.randomUUID(), "16", "2025-02-01T09:00:00Z"); // un autre patient
        BesoinMedicament cloture = service.cloturer(PATIENT, ancien.id());

        assertThat(service.mesBesoins(PATIENT)).containsExactly(recent, cloture);
        assertThat(service.mesBesoins(UUID.randomUUID())).isEmpty();
    }

    @Test
    void cloture_un_besoin_ouvert() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);

        BesoinMedicament cloture = service.cloturer(PATIENT, b.id());

        assertThat(cloture.id()).isEqualTo(b.id());
        assertThat(cloture.statut()).isEqualTo(StatutBesoin.CLOTURE);
        assertThat(cloture.clotureLe()).isNotNull();
        assertThat(besoins.parId(b.id()).orElseThrow().statut()).isEqualTo(StatutBesoin.CLOTURE);
        assertThat(service.besoinsOuverts("16")).isEmpty();
    }

    @Test
    void cloturer_est_refuse_a_un_autre_patient_deux_fois_et_pour_un_besoin_inconnu() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);

        assertThatThrownBy(() -> service.cloturer(UUID.randomUUID(), b.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(besoins.parId(b.id()).orElseThrow().estOuvert()).isTrue();
        service.cloturer(PATIENT, b.id());
        assertThatThrownBy(() -> service.cloturer(PATIENT, b.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.cloturer(PATIENT, UUID.randomUUID())).isInstanceOf(BesoinIntrouvableException.class);
    }

    @Test
    void les_besoins_ouverts_d_une_wilaya_excluent_les_clotures_et_les_autres_wilayas() {
        BesoinMedicament ancien = publieLe(PATIENT, "16", "2025-01-10T09:00:00Z");
        BesoinMedicament recent = publieLe(UUID.randomUUID(), "16", "2025-03-01T09:00:00Z");
        BesoinMedicament cloture = publieLe(UUID.randomUUID(), "16", "2025-02-01T09:00:00Z");
        publieLe(UUID.randomUUID(), "31", "2025-02-15T09:00:00Z"); // une autre wilaya
        service.cloturer(cloture.patientId(), cloture.id());

        assertThat(service.besoinsOuverts("16")).containsExactly(recent, ancien);
        assertThat(service.besoinsOuverts(" 16 ")).containsExactly(recent, ancien);
        assertThat(service.besoinsOuverts("31")).hasSize(1);
        assertThat(service.besoinsOuverts("25")).isEmpty();
    }

    @Test
    void les_besoins_ouverts_exigent_une_wilaya() {
        assertThatThrownBy(() -> service.besoinsOuverts(null)).isInstanceOf(BesoinInvalideException.class);
        assertThatThrownBy(() -> service.besoinsOuverts("  ")).isInstanceOf(BesoinInvalideException.class);
    }

    @Test
    void une_pharmacie_repond_et_le_patient_est_prevenu_sans_detail() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);

        ReponsePharmacie r = service.repondre(PHARMACIE, b.id(), REPONSE);

        assertThat(r.besoinId()).isEqualTo(b.id());
        assertThat(r.pharmacieId()).isEqualTo(PHARMACIE);
        assertThat(r.nomPharmacie()).isEqualTo("Pharmacie El Amel");
        assertThat(r.disponible()).isTrue();
        assertThat(r.prixDa()).isEqualTo(850);
        assertThat(r.commentaire()).isEqualTo("En stock ce matin.");
        assertThat(r.repondueLe()).isNotNull();
        assertThat(service.nombreReponses(b.id())).isEqualTo(1);
        assertThat(service.reponsesPourPatient(PATIENT, b.id())).containsExactly(r);
        assertThat(service.reponsesPourPharmacie(b.id())).containsExactly(r);
        assertThat(notifieur.appels).hasSize(1);
        assertThat(notifieur.pour(PATIENT).get(0).sujet()).isEqualTo("Reponse d'une pharmacie");
        assertThat(notifieur.pour(PATIENT).get(0).message()).isEqualTo("Une pharmacie a repondu a votre demande de medicament.");
        assertThat(notifieur.pour(PATIENT).get(0).message()).doesNotContain("El Amel");
    }

    @Test
    void repondre_a_un_besoin_cloture_est_refuse() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);
        service.cloturer(PATIENT, b.id());

        assertThatThrownBy(() -> service.repondre(PHARMACIE, b.id(), REPONSE)).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.nombreReponses(b.id())).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void une_pharmacie_ne_repond_qu_une_fois_par_besoin() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);
        service.repondre(PHARMACIE, b.id(), REPONSE);

        assertThatThrownBy(() -> service.repondre(PHARMACIE, b.id(), new DemandeReponse("Pharmacie El Amel", false, null, null)))
                .isInstanceOf(TransitionInvalideException.class);
        ReponsePharmacie autre = service.repondre(UUID.randomUUID(), b.id(), new DemandeReponse("Pharmacie Nour", false, null, "Rupture."));

        assertThat(service.nombreReponses(b.id())).isEqualTo(2);
        assertThat(service.reponsesPourPharmacie(b.id()).get(1)).isEqualTo(autre);
        assertThat(notifieur.pour(PATIENT)).hasSize(2);
    }

    @Test
    void repondre_refuse_une_reponse_incomplete_un_besoin_inconnu_sans_rien_enregistrer() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);

        assertThatThrownBy(() -> service.repondre(PHARMACIE, b.id(), new DemandeReponse(" ", true, null, null)))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> service.repondre(PHARMACIE, b.id(), new DemandeReponse("Pharmacie El Amel", true, -5, null)))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> service.repondre(PHARMACIE, UUID.randomUUID(), REPONSE))
                .isInstanceOf(BesoinIntrouvableException.class);
        assertThat(service.nombreReponses(b.id())).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void les_reponses_sont_reservees_au_patient_proprietaire_ou_a_une_pharmacie() {
        BesoinMedicament b = service.publier(PATIENT, DEMANDE);
        service.repondre(PHARMACIE, b.id(), REPONSE);

        assertThatThrownBy(() -> service.reponsesPourPatient(UUID.randomUUID(), b.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(service.reponsesPourPatient(PATIENT, b.id())).hasSize(1);
        assertThat(service.reponsesPourPharmacie(b.id())).hasSize(1);
        assertThatThrownBy(() -> service.reponsesPourPatient(PATIENT, UUID.randomUUID())).isInstanceOf(BesoinIntrouvableException.class);
        assertThatThrownBy(() -> service.reponsesPourPharmacie(UUID.randomUUID())).isInstanceOf(BesoinIntrouvableException.class);
    }
}
