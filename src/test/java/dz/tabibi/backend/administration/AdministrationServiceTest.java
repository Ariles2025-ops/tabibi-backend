package dz.tabibi.backend.administration;

import dz.tabibi.backend.administration.adapter.EnMemoireCandidatureRepository;
import dz.tabibi.backend.administration.application.AdministrationService;
import dz.tabibi.backend.administration.domain.CandidatureIntrouvableException;
import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatistiquesAdministration;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.adapter.EnMemoireNotificationRepository;
import dz.tabibi.backend.notifications.application.NotifieurInterne;
import dz.tabibi.backend.notifications.domain.Notification;
import dz.tabibi.backend.notifications.domain.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministrationServiceTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final DemandeCandidature DEMANDE = new DemandeCandidature(
            "Dr Nadia Bensalem", "cardiologue", "Cardiologue", "16", "Alger", "Hydra", "16-12345", "0550000000");

    private final CandidatureRepository candidatures = new EnMemoireCandidatureRepository();
    private final MedecinRepository medecins = new EnMemoireMedecinRepository();
    private final NotificationRepository notifications = new EnMemoireNotificationRepository();
    private final AdministrationService service =
            new AdministrationService(candidatures, medecins, new NotifieurInterne(notifications, Messages.partagees(), utilisateur -> Langue.FR));

    /** Candidature deposee a une date choisie, enregistree directement (sans passer par le service). */
    private CandidatureMedecin deposeeLe(UUID medecin, String date) {
        return candidatures.enregistrer(CandidatureMedecin.deposer(medecin, DEMANDE, Instant.parse(date)));
    }

    private List<Notification> notificationsDe(UUID destinataire) {
        return notifications.parDestinataire(destinataire);
    }

    @Test
    void depose_une_candidature_en_attente() {
        CandidatureMedecin c = service.deposer(MEDECIN, DEMANDE);

        assertThat(c.medecinId()).isEqualTo(MEDECIN);
        assertThat(c.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        assertThat(c.deposeeLe()).isNotNull();
        assertThat(candidatures.parId(c.id())).contains(c);
        assertThat(service.maCandidature(MEDECIN)).isEqualTo(c);
        assertThat(medecins.parId(MEDECIN)).isEmpty(); // pas encore dans l'annuaire
    }

    @Test
    void refuse_une_candidature_incomplete() {
        DemandeCandidature sansNumeroOrdre = new DemandeCandidature(
                "Dr X", "cardiologue", null, "16", null, null, " ", null);

        assertThatThrownBy(() -> service.deposer(MEDECIN, sansNumeroOrdre))
                .isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> service.maCandidature(MEDECIN))
                .isInstanceOf(CandidatureIntrouvableException.class);
    }

    @Test
    void refuse_un_second_depot_tant_qu_une_candidature_est_en_attente_ou_validee() {
        CandidatureMedecin enAttente = service.deposer(MEDECIN, DEMANDE);

        assertThatThrownBy(() -> service.deposer(MEDECIN, DEMANDE)).isInstanceOf(TransitionInvalideException.class);

        service.valider(enAttente.id());

        assertThatThrownBy(() -> service.deposer(MEDECIN, DEMANDE)).isInstanceOf(TransitionInvalideException.class);
        assertThat(service.lister(Optional.empty())).hasSize(1);
    }

    @Test
    void permet_de_redeposer_apres_un_refus() {
        CandidatureMedecin premiere = service.deposer(MEDECIN, DEMANDE);
        service.refuser(premiere.id(), "Numero d'ordre illisible.");

        CandidatureMedecin seconde = service.deposer(MEDECIN, DEMANDE);

        assertThat(seconde.id()).isNotEqualTo(premiere.id());
        assertThat(seconde.statut()).isEqualTo(StatutCandidature.EN_ATTENTE);
        assertThat(service.maCandidature(MEDECIN).id()).isEqualTo(seconde.id());
        assertThat(service.lister(Optional.empty())).hasSize(2);
    }

    @Test
    void ma_candidature_est_introuvable_sans_depot() {
        assertThatThrownBy(() -> service.maCandidature(UUID.randomUUID()))
                .isInstanceOf(CandidatureIntrouvableException.class);
    }

    @Test
    void valider_publie_le_medecin_dans_l_annuaire_et_le_previent() {
        CandidatureMedecin c = service.deposer(MEDECIN, DEMANDE);

        CandidatureMedecin validee = service.valider(c.id());

        assertThat(validee.statut()).isEqualTo(StatutCandidature.VALIDEE);
        assertThat(validee.traiteeLe()).isNotNull();
        assertThat(candidatures.parId(c.id()).orElseThrow().statut()).isEqualTo(StatutCandidature.VALIDEE);
        Medecin publie = medecins.parId(MEDECIN).orElseThrow();
        assertThat(publie).isEqualTo(new Medecin(MEDECIN, "Dr Nadia Bensalem", "cardiologue", "Cardiologue", "16", "Alger", "Hydra"));
        assertThat(medecins.rechercher(CritereRecherche.de("cardiologue", "16", "nadia"))).containsExactly(publie);
        assertThat(notificationsDe(MEDECIN)).hasSize(1);
        assertThat(notificationsDe(MEDECIN).get(0).sujet()).isEqualTo("Candidature validee");
    }

    @Test
    void valider_complete_les_libelles_absents_par_les_codes() {
        CandidatureMedecin c = service.deposer(MEDECIN,
                new DemandeCandidature("Dr Nadia Bensalem", "cardiologue", null, "16", null, null, "16-12345", null));

        service.valider(c.id());

        Medecin publie = medecins.parId(MEDECIN).orElseThrow();
        assertThat(publie.specialiteFr()).isEqualTo("cardiologue");
        assertThat(publie.wilayaFr()).isEqualTo("16");
        assertThat(publie.ville()).isNull();
    }

    @Test
    void valider_une_candidature_inconnue_ou_deja_traitee_est_refuse() {
        CandidatureMedecin c = service.deposer(MEDECIN, DEMANDE);
        service.valider(c.id());

        assertThatThrownBy(() -> service.valider(UUID.randomUUID())).isInstanceOf(CandidatureIntrouvableException.class);
        assertThatThrownBy(() -> service.valider(c.id())).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(() -> service.refuser(c.id(), "Trop tard.")).isInstanceOf(TransitionInvalideException.class);
        assertThat(notificationsDe(MEDECIN)).hasSize(1);
    }

    @Test
    void refuser_avec_motif_previent_le_medecin_du_motif() {
        CandidatureMedecin c = service.deposer(MEDECIN, DEMANDE);

        CandidatureMedecin refusee = service.refuser(c.id(), "Numero d'ordre illisible.");

        assertThat(refusee.statut()).isEqualTo(StatutCandidature.REFUSEE);
        assertThat(refusee.motifRefus()).isEqualTo("Numero d'ordre illisible.");
        assertThat(refusee.traiteeLe()).isNotNull();
        assertThat(medecins.parId(MEDECIN)).isEmpty();
        assertThat(notificationsDe(MEDECIN)).hasSize(1);
        assertThat(notificationsDe(MEDECIN).get(0).sujet()).isEqualTo("Candidature refusee");
        assertThat(notificationsDe(MEDECIN).get(0).message()).contains("Numero d'ordre illisible.");
    }

    @Test
    void refuser_sans_motif_est_invalide() {
        CandidatureMedecin c = service.deposer(MEDECIN, DEMANDE);

        assertThatThrownBy(() -> service.refuser(c.id(), " ")).isInstanceOf(CandidatureInvalideException.class);
        assertThatThrownBy(() -> service.refuser(c.id(), null)).isInstanceOf(CandidatureInvalideException.class);
        assertThat(candidatures.parId(c.id()).orElseThrow().estEnAttente()).isTrue();
        assertThat(notificationsDe(MEDECIN)).isEmpty();
    }

    @Test
    void liste_les_candidatures_les_plus_anciennes_d_abord_avec_filtre_par_statut() {
        CandidatureMedecin recente = deposeeLe(UUID.randomUUID(), "2026-03-01T09:00:00Z");
        CandidatureMedecin ancienne = deposeeLe(UUID.randomUUID(), "2026-01-10T09:00:00Z");
        CandidatureMedecin milieu = deposeeLe(UUID.randomUUID(), "2026-02-01T09:00:00Z");
        CandidatureMedecin validee = service.valider(milieu.id());

        assertThat(service.lister(Optional.empty())).containsExactly(ancienne, validee, recente);
        assertThat(service.lister(Optional.of(StatutCandidature.EN_ATTENTE))).containsExactly(ancienne, recente);
        assertThat(service.lister(Optional.of(StatutCandidature.VALIDEE))).containsExactly(validee);
        assertThat(service.lister(Optional.of(StatutCandidature.REFUSEE))).isEmpty();
    }

    @Test
    void statistiques_comptent_les_candidatures_par_statut() {
        CandidatureMedecin a = deposeeLe(UUID.randomUUID(), "2026-01-10T09:00:00Z");
        CandidatureMedecin b = deposeeLe(UUID.randomUUID(), "2026-01-11T09:00:00Z");
        deposeeLe(UUID.randomUUID(), "2026-01-12T09:00:00Z");
        deposeeLe(UUID.randomUUID(), "2026-01-13T09:00:00Z");
        service.valider(a.id());
        service.refuser(b.id(), "Motif.");

        StatistiquesAdministration stats = service.statistiques();

        assertThat(stats.candidaturesEnAttente()).isEqualTo(2);
        assertThat(stats.candidaturesValidees()).isEqualTo(1);
        assertThat(stats.candidaturesRefusees()).isEqualTo(1);
    }
}
