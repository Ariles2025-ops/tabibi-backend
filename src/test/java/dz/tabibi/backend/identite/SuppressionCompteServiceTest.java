package dz.tabibi.backend.identite;

import dz.tabibi.backend.commun.FauxComptes;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuppressionCompteServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();

    record Appel(UUID destinataire, String sujet) {}

    private final List<Appel> notifications = new ArrayList<>();
    private final EnMemoireCreneauRepository creneaux = new EnMemoireCreneauRepository();
    private final EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
    private final EnMemoireListeAttenteRepository attente = new EnMemoireListeAttenteRepository();
    private final EnMemoireProfilRepository profils = new EnMemoireProfilRepository();
    private final FauxComptes comptes = new FauxComptes().avec(PATIENT, "Amina Belkacem", "amina@example.dz", "0550123456");
    private final ListeAttenteService listeAttente = new ListeAttenteService(attente,
            (d, s, m) -> notifications.add(new Appel(d, s)));
    private final RendezVousService rendezVous = new RendezVousService(rdvs, creneaux,
            (d, s, m) -> notifications.add(new Appel(d, s)), listeAttente);
    private final SuppressionCompteService service = new SuppressionCompteService(rendezVous, listeAttente, profils, comptes);

    @Test
    void annule_les_rdv_a_venir_retire_des_listes_anonymise_le_profil_et_supprime_le_compte() {
        Creneau creneau = creneaux.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN,
                Instant.now().plusSeconds(86_400), 20, true));
        RendezVous aVenir = rendezVous.reserverCreneau(PATIENT, creneau.id());
        RendezVous passe = rdvs.enregistrer(new RendezVous(UUID.randomUUID(), PATIENT, MEDECIN,
                Instant.now().minusSeconds(86_400), StatutRdv.HONORE));
        attente.enregistrer(InscriptionAttente.inscrire(PATIENT, UUID.randomUUID(), Instant.now()));
        profils.enregistrer(Profil.renseigner(PATIENT,
                new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "ar"), Instant.now()));
        notifications.clear();

        service.supprimer(PATIENT);

        assertThat(rdvs.parId(aVenir.id()).orElseThrow().statut()).isEqualTo(StatutRdv.ANNULE);
        assertThat(creneaux.parId(creneau.id()).orElseThrow().disponible()).isTrue();
        assertThat(rdvs.parId(passe.id()).orElseThrow().statut()).isEqualTo(StatutRdv.HONORE);
        assertThat(notifications).contains(new Appel(MEDECIN, "Rendez-vous annulé"));
        assertThat(attente.parPatient(PATIENT)).isEmpty();
        Profil anonyme = profils.parUtilisateur(PATIENT).orElseThrow();
        assertThat(anonyme.nomComplet()).isEqualTo("Compte supprimé");
        assertThat(anonyme.telephone()).isNull();
        assertThat(anonyme.dateNaissance()).isNull();
        assertThat(anonyme.wilayaCode()).isNull();
        assertThat(anonyme.langue()).isEqualTo("ar");
        assertThat(comptes.supprimes).containsExactly(PATIENT);
    }

    @Test
    void sans_profil_ni_rendez_vous_ecrit_quand_meme_un_profil_anonyme() {
        service.supprimer(PATIENT);

        assertThat(profils.parUtilisateur(PATIENT).orElseThrow().nomComplet()).isEqualTo("Compte supprimé");
        assertThat(comptes.supprimes).containsExactly(PATIENT);
    }

    @Test
    void si_keycloak_refuse_l_erreur_remonte() {
        comptes.panne = new IllegalStateException("Keycloak injoignable");

        assertThatThrownBy(() -> service.supprimer(PATIENT)).isInstanceOf(IllegalStateException.class);
    }
}
