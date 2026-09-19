package dz.tabibi.backend.listeattente;

import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.InscriptionIntrouvableException;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListeAttenteServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();
        private final Messages messages = Messages.partagees();

        /** Rend les cles avec le vrai catalogue, en francais : les textes verifies ici sont ceux servis. */
        @Override
        public void notifier(UUID destinataireId, String cleSujet, String cleMessage, Object... params) {
            appels.add(new Appel(destinataireId,
                    messages.message(Langue.FR, cleSujet), messages.message(Langue.FR, cleMessage, params)));
        }

        List<Appel> pour(UUID destinataireId) {
            return appels.stream().filter(a -> a.destinataireId().equals(destinataireId)).toList();
        }
    }

    private final ListeAttenteRepository repository = new EnMemoireListeAttenteRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final ListeAttenteService service = new ListeAttenteService(repository, notifieur);

    /** Inscription datee, enregistree directement (sans passer par le service). */
    private InscriptionAttente inscritLe(UUID patient, UUID medecin, String date) {
        return repository.enregistrer(InscriptionAttente.inscrire(patient, medecin, Instant.parse(date)));
    }

    @Test
    void inscrit_le_patient_sur_la_liste_d_un_medecin() {
        InscriptionAttente i = service.inscrire(PATIENT, MEDECIN);

        assertThat(i.id()).isNotNull();
        assertThat(i.patientId()).isEqualTo(PATIENT);
        assertThat(i.medecinId()).isEqualTo(MEDECIN);
        assertThat(i.inscritLe()).isNotNull();
        assertThat(repository.parId(i.id())).contains(i);
        assertThat(service.mesInscriptions(PATIENT)).containsExactly(i);
        assertThat(service.listeDuMedecin(MEDECIN)).containsExactly(i);
        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void refuse_une_seconde_inscription_sur_la_meme_liste() {
        service.inscrire(PATIENT, MEDECIN);

        assertThatThrownBy(() -> service.inscrire(PATIENT, MEDECIN)).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.mesInscriptions(PATIENT)).hasSize(1);
        assertThat(service.listeDuMedecin(MEDECIN)).hasSize(1);
    }

    @Test
    void un_patient_peut_s_inscrire_chez_plusieurs_medecins() {
        UUID autreMedecin = UUID.randomUUID();

        InscriptionAttente premiere = service.inscrire(PATIENT, MEDECIN);
        InscriptionAttente seconde = service.inscrire(PATIENT, autreMedecin);

        assertThat(service.mesInscriptions(PATIENT)).containsExactly(premiere, seconde);
        assertThat(service.listeDuMedecin(MEDECIN)).containsExactly(premiere);
        assertThat(service.listeDuMedecin(autreMedecin)).containsExactly(seconde);
    }

    @Test
    void mes_inscriptions_sont_les_miennes_les_plus_anciennes_d_abord() {
        InscriptionAttente recente = inscritLe(PATIENT, UUID.randomUUID(), "2026-03-01T09:00:00Z");
        InscriptionAttente ancienne = inscritLe(PATIENT, MEDECIN, "2026-01-10T09:00:00Z");
        inscritLe(UUID.randomUUID(), MEDECIN, "2026-02-01T09:00:00Z"); // un autre patient

        assertThat(service.mesInscriptions(PATIENT)).containsExactly(ancienne, recente);
        assertThat(service.mesInscriptions(UUID.randomUUID())).isEmpty();
    }

    @Test
    void la_liste_du_medecin_contient_ses_inscrits_les_plus_anciens_d_abord() {
        InscriptionAttente recente = inscritLe(UUID.randomUUID(), MEDECIN, "2026-03-01T09:00:00Z");
        InscriptionAttente ancienne = inscritLe(PATIENT, MEDECIN, "2026-01-10T09:00:00Z");
        inscritLe(UUID.randomUUID(), UUID.randomUUID(), "2026-02-01T09:00:00Z"); // un autre medecin

        assertThat(service.listeDuMedecin(MEDECIN)).containsExactly(ancienne, recente);
        assertThat(service.listeDuMedecin(UUID.randomUUID())).isEmpty();
    }

    @Test
    void retire_une_inscription_du_patient_qui_peut_ensuite_se_reinscrire() {
        InscriptionAttente i = service.inscrire(PATIENT, MEDECIN);

        service.retirer(PATIENT, i.id());

        assertThat(repository.parId(i.id())).isEmpty();
        assertThat(service.mesInscriptions(PATIENT)).isEmpty();
        assertThat(service.listeDuMedecin(MEDECIN)).isEmpty();
        assertThat(service.inscrire(PATIENT, MEDECIN).medecinId()).isEqualTo(MEDECIN);
    }

    @Test
    void retirer_est_refuse_a_un_autre_patient_et_pour_une_inscription_inconnue() {
        InscriptionAttente i = service.inscrire(PATIENT, MEDECIN);

        assertThatThrownBy(() -> service.retirer(UUID.randomUUID(), i.id())).isInstanceOf(AccesRefuseException.class);
        assertThat(repository.parId(i.id())).contains(i);
        assertThatThrownBy(() -> service.retirer(PATIENT, UUID.randomUUID())).isInstanceOf(InscriptionIntrouvableException.class);
        service.retirer(PATIENT, i.id());
        assertThatThrownBy(() -> service.retirer(PATIENT, i.id())).isInstanceOf(InscriptionIntrouvableException.class);
    }

    @Test
    void un_creneau_libere_previent_chaque_inscrit_de_la_liste_du_medecin() {
        UUID autrePatient = UUID.randomUUID();
        UUID patientDUnAutreMedecin = UUID.randomUUID();
        service.inscrire(PATIENT, MEDECIN);
        service.inscrire(autrePatient, MEDECIN);
        service.inscrire(patientDUnAutreMedecin, UUID.randomUUID());

        service.creneauLibere(MEDECIN, Instant.parse("2026-12-07T09:00:00Z"));

        assertThat(notifieur.appels).hasSize(2);
        assertThat(notifieur.pour(PATIENT)).hasSize(1);
        assertThat(notifieur.pour(PATIENT).get(0).sujet()).isEqualTo("Creneau disponible");
        assertThat(notifieur.pour(PATIENT).get(0).message())
                .isEqualTo("Un creneau vient de se liberer chez votre medecin le 07/12/2026 a 10:00. Reservez vite.");
        assertThat(notifieur.pour(autrePatient)).hasSize(1);
        assertThat(notifieur.pour(patientDUnAutreMedecin)).isEmpty();
    }

    @Test
    void un_creneau_libere_chez_un_medecin_sans_liste_ne_previent_personne() {
        service.inscrire(PATIENT, MEDECIN);

        service.creneauLibere(UUID.randomUUID(), Instant.parse("2026-12-07T09:00:00Z"));

        assertThat(notifieur.appels).isEmpty();
    }

    @Test
    void un_patient_retire_de_la_liste_n_est_plus_prevenu() {
        InscriptionAttente i = service.inscrire(PATIENT, MEDECIN);
        service.retirer(PATIENT, i.id());

        service.creneauLibere(MEDECIN, Instant.parse("2026-12-07T09:00:00Z"));

        assertThat(notifieur.appels).isEmpty();
    }
}
