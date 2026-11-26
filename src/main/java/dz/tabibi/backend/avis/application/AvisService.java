package dz.tabibi.backend.avis.application;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisIntrouvableException;
import dz.tabibi.backend.avis.domain.AvisInvalideException;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage des avis : depot par le patient a l'issue d'un rendez-vous honore (avis verifie, un seul
 * par rendez-vous), synthese publique d'un medecin, signalement par le medecin concerne, moderation
 * (masquer / retablir) par l'administrateur.
 */
@Service
public class AvisService {

    private final AvisRepository avis;
    private final RendezVousRepository rendezVous;

    public AvisService(AvisRepository avis, RendezVousRepository rendezVous) {
        this.avis = avis;
        this.rendezVous = rendezVous;
    }

    /** Port de notification : previent les administrateurs d'un signalement et le medecin de la decision. */
    private Notifieur notifieur;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotifieur(Notifieur notifieur) {
        this.notifieur = notifieur;
    }

    /** Annuaire des comptes : sert a retrouver les administrateurs. */
    private ComptesUtilisateurs comptes;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setComptes(ComptesUtilisateurs comptes) {
        this.comptes = comptes;
    }

    /** Profils : langue (FR/AR) du medecin destinataire des decisions. */
    private ProfilRepository profils;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

    /**
     * Suppression du compte du patient : le commentaire de chacun de ses avis est efface (la note reste,
     * sans lien lisible avec une personne : son profil est anonymise). @return le nombre d'avis modifies.
     */
    @Transactional
    public int effacerCommentairesDuPatient(UUID patientId) {
        List<Avis> aEffacer = avis.parPatient(patientId).stream().filter(a -> a.commentaire() != null).toList();
        aEffacer.forEach(a -> avis.enregistrer(a.sansCommentaire()));
        return aEffacer.size();
    }

    /**
     * Depose l'avis du patient sur le medecin d'un de ses rendez-vous honores.
     * @throws AvisInvalideException si le rendez-vous manque, si la note est hors bornes ou le commentaire trop long.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si le rendez-vous est a un autre patient.
     * @throws TransitionInvalideException si le rendez-vous n'est pas honore ou s'il a deja un avis.
     */
    @Transactional
    public Avis deposer(UUID patientId, UUID rendezVousId, int note, String commentaire) {
        if (rendezVousId == null) {
            throw new AvisInvalideException("Le rendez-vous est obligatoire.");
        }
        RendezVous rdv = rendezVous.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.appartientA(patientId)) {
            throw new AccesRefuseException("Ce rendez-vous ne vous appartient pas.");
        }
        if (rdv.statut() != StatutRdv.HONORE) {
            throw new TransitionInvalideException(
                    "Vous pourrez donner votre avis une fois la consultation effectuée.");
        }
        if (avis.parRendezVous(rendezVousId).isPresent()) {
            throw new TransitionInvalideException("Un avis a déjà été déposé pour ce rendez-vous.");
        }
        return avis.enregistrer(Avis.deposer(rendezVousId, patientId, rdv.medecinId(), note, commentaire, Instant.now()));
    }

    /** Avis deposes par le patient, tous statuts, les plus recents d'abord. */
    public List<Avis> mesAvis(UUID patientId) {
        return avis.parPatient(patientId);
    }

    /**
     * Synthese publique d'un medecin : les avis publies et ceux signales par le medecin (ils restent visibles
     * tant que l'administrateur ne les a pas masques), jamais les avis masques.
     */
    public SyntheseAvis avisPublics(UUID medecinId) {
        return SyntheseAvis.de(avis.publiesPourMedecin(medecinId));
    }

    /**
     * Le medecin concerne signale un avis a l'administrateur.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws AccesRefuseException s'il porte sur un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas publie.
     */
    @Transactional
    public Avis signaler(UUID medecinId, UUID avisId) {
        Avis a = charger(avisId);
        if (!a.concerne(medecinId)) {
            throw new AccesRefuseException("Cet avis ne vous concerne pas.");
        }
        Avis signale = avis.enregistrer(a.signaler());
        prevenirLesAdministrateurs();
        return signale;
    }

    /** Chaque administrateur recoit une notification (lien vers la moderation des avis). */
    private void prevenirLesAdministrateurs() {
        if (notifieur == null || comptes == null) {
            return;
        }
        for (UUID admin : comptes.avecRole("ADMIN")) {
            notifieur.notifier(admin, "Avis signalé",
                    "Un médecin a signalé un avis : à modérer.", "/admin/avis");
        }
    }

    /** Avis pour l'administrateur, eventuellement filtres par statut, les plus anciens d'abord. */
    public List<Avis> lister(Optional<StatutAvis> statut) {
        return statut.map(avis::parStatut).orElseGet(avis::tous);
    }

    /**
     * L'administrateur masque un avis.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws TransitionInvalideException s'il est deja masque.
     */
    @Transactional
    public Avis masquer(UUID avisId) {
        Avis avant = charger(avisId);
        boolean etaitSignale = avant.statut() == StatutAvis.SIGNALE;
        Avis masque = avis.enregistrer(avant.masquer());
        prevenirLeMedecin(masque, etaitSignale, true);
        return masque;
    }

    private void prevenirLeMedecin(Avis a, boolean etaitSignale, boolean masque) {
        if (notifieur != null) {
            String langue = Messages.langueDe(profils, a.medecinId());
            String message = masque
                    ? Messages.avisMasqueMessage(langue, etaitSignale)
                    : Messages.avisRetabliMessage(langue);
            notifieur.notifier(a.medecinId(), Messages.avisDecisionSujet(langue, etaitSignale), message, "/medecin/avis");
        }
    }

    /**
     * L'administrateur remet un avis en ligne.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws TransitionInvalideException s'il est deja publie.
     */
    @Transactional
    public Avis retablir(UUID avisId) {
        Avis avant = charger(avisId);
        Avis retabli = avis.enregistrer(avant.retablir());
        boolean etaitSignale = avant.statut() == StatutAvis.SIGNALE;
        prevenirLeMedecin(retabli, etaitSignale, false);
        return retabli;
    }

    private Avis charger(UUID avisId) {
        return avis.parId(avisId).orElseThrow(() -> new AvisIntrouvableException(avisId));
    }
}
