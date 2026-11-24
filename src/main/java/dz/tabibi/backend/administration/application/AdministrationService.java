package dz.tabibi.backend.administration.application;

import dz.tabibi.backend.administration.domain.AttributionRoleMedecin;
import dz.tabibi.backend.administration.domain.CandidatureIntrouvableException;
import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatistiquesAdministration;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.commun.domain.Courriel;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage de l'administration : depot d'une candidature par un medecin, examen par
 * l'administrateur (validation = publication dans l'annuaire, refus motive), statistiques.
 * Le medecin est prevenu de la decision par le port Notifieur.
 */
@Service
public class AdministrationService {

    private final CandidatureRepository candidatures;
    private final MedecinRepository medecins;
    private final Notifieur notifieur;
    private final AttributionRoleMedecin roles;
    private final String siteDomaine;

    /** Profils : langue (FR/AR) du destinataire des notifications. */
    private ProfilRepository profils;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

    public AdministrationService(CandidatureRepository candidatures, MedecinRepository medecins, Notifieur notifieur,
                                 AttributionRoleMedecin roles,
                                 @Value("${SITE_DOMAINE:ndawik.com}") String siteDomaine) {
        this.candidatures = candidatures;
        this.medecins = medecins;
        this.notifieur = notifieur;
        this.roles = roles;
        this.siteDomaine = siteDomaine;
    }

    /** E-mail aux administrateurs (en plus de la notification), si un serveur SMTP est configure. */
    private Courriel courriel;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCourriel(Courriel courriel) {
        this.courriel = courriel;
    }

    /** Annuaire des comptes : sert a prevenir les administrateurs d'une nouvelle candidature (P-79). */
    private ComptesUtilisateurs comptes;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setComptes(ComptesUtilisateurs comptes) {
        this.comptes = comptes;
    }

    /** Avis : sert a compter les avis signales en attente de moderation. */
    private dz.tabibi.backend.avis.domain.AvisRepository avis;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAvis(dz.tabibi.backend.avis.domain.AvisRepository avis) {
        this.avis = avis;
    }

    /** Liste d'attente : reportee de la fiche importee vers la fiche du praticien quand il la reclame. */
    private dz.tabibi.backend.listeattente.application.ListeAttenteService listeAttente;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setListeAttente(dz.tabibi.backend.listeattente.application.ListeAttenteService listeAttente) {
        this.listeAttente = listeAttente;
    }

    /** Rendez-vous : la suspension annule les rendez-vous a venir et supprime les creneaux libres. */
    private dz.tabibi.backend.rendezvous.application.RendezVousService rendezVous;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRendezVous(dz.tabibi.backend.rendezvous.application.RendezVousService rendezVous) {
        this.rendezVous = rendezVous;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdministrationService.class);

    /**
     * Suspend un medecin valide : role MEDECIN retire, fiche retiree de l'annuaire (plus reservable), rendez-vous
     * a venir annules « par le cabinet » (patients prevenus), creneaux libres supprimes, medecin prevenu avec le motif.
     * Les acces de l'administrateur sont traces par le journal d'audit ; le motif est aussi journalise.
     * @return le nombre de rendez-vous annules.
     * @throws CandidatureInvalideException si le motif manque.
     * @throws TransitionInvalideException si le medecin n'est pas valide ou deja suspendu.
     */
    @Transactional
    public int suspendre(UUID medecinId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new CandidatureInvalideException("Le motif de la suspension est obligatoire.");
        }
        candidatureValidee(medecinId);
        if (medecins.parId(medecinId).isEmpty()) {
            throw new TransitionInvalideException("Ce médecin est déjà suspendu.");
        }
        int annules = rendezVous == null ? 0 : rendezVous.annulerAVenirDuMedecin(medecinId);
        medecins.supprimer(medecinId);
        if (comptes != null) {
            comptes.retirerRole(medecinId, "MEDECIN");
        }
        String langue = Messages.langueDe(profils, medecinId);
        notifieur.notifier(medecinId, Messages.compteSuspenduSujet(langue),
                Messages.compteSuspenduMessage(langue, motif.strip()), "/medecin/candidature");
        log.info("Medecin {} suspendu ({} rendez-vous annules). Motif : {}", medecinId, annules, motif.strip());
        return annules;
    }

    /**
     * Retablit un medecin suspendu : fiche republiee (telle que validee), role MEDECIN rendu, medecin prevenu.
     * @throws TransitionInvalideException si le medecin n'est pas valide ou n'est pas suspendu.
     */
    @Transactional
    public void retablir(UUID medecinId) {
        CandidatureMedecin validee = candidatureValidee(medecinId);
        if (medecins.parId(medecinId).isPresent()) {
            throw new TransitionInvalideException("Ce médecin n'est pas suspendu.");
        }
        medecins.enregistrer(fichePublique(validee));
        roles.accorder(medecinId);
        String langue = Messages.langueDe(profils, medecinId);
        notifieur.notifier(medecinId, Messages.compteRetabliSujet(langue),
                Messages.compteRetabliMessage(langue), "/medecin/agenda");
        log.info("Medecin {} retabli.", medecinId);
    }

    private CandidatureMedecin candidatureValidee(UUID medecinId) {
        return candidatures.derniereDuMedecin(medecinId)
                .filter(c -> c.statut() == StatutCandidature.VALIDEE)
                .orElseThrow(() -> new TransitionInvalideException("Ce compte n'est pas un médecin validé."));
    }

    /**
     * Depose la candidature du medecin connecte. Une candidature refusee peut etre redeposee.
     * @throws CandidatureInvalideException si une donnee obligatoire manque.
     * @throws TransitionInvalideException si une candidature en attente ou validee existe deja.
     */
    @Transactional
    public CandidatureMedecin deposer(UUID medecinId, DemandeCandidature demande) {
        candidatures.derniereDuMedecin(medecinId)
                .filter(c -> c.statut() != StatutCandidature.REFUSEE)
                .ifPresent(c -> {
                    throw new TransitionInvalideException(c.estEnAttente()
                            ? "Une candidature est déjà en attente d'examen."
                            : "Votre candidature a déjà été validée.");
                });
        if (demande != null && demande.ficheReclamee() != null && !ficheReclamable(demande.ficheReclamee())) {
            throw new TransitionInvalideException("Cette fiche a déjà été réclamée ou n'existe plus.");
        }
        CandidatureMedecin deposee = candidatures.enregistrer(CandidatureMedecin.deposer(medecinId, demande, Instant.now()));
        prevenirLesAdministrateurs(deposee);
        return deposee;
    }

    /** Chaque administrateur recoit une notification (lien vers la liste des candidatures). */
    private void prevenirLesAdministrateurs(CandidatureMedecin c) {
        if (comptes == null) {
            return;
        }
        String quoi = c.ficheReclamee() != null ? "Réclamation de fiche" : "Nouvelle candidature médecin";
        String message = c.nomComplet() + " — " + (c.specialiteFr() != null ? c.specialiteFr() : c.specialiteSlug())
                + (c.wilayaFr() != null ? ", " + c.wilayaFr() : "") + " : à examiner.";
        for (UUID admin : comptes.avecRole("ADMIN")) {
            notifieur.notifier(admin, quoi, message, "/admin/candidatures");
            if (courriel != null && courriel.disponible()) {
                comptes.infos(admin).map(ComptesUtilisateurs.InfosCompte::email).filter(e -> e != null && !e.isBlank())
                        .ifPresent(email -> {
                            try {
                                courriel.envoyer(email, "Ndawik — " + quoi, message
                                        + "\n\nÀ examiner ici : https://" + siteDomaine + "/admin/candidatures");
                            } catch (RuntimeException e) {
                                org.slf4j.LoggerFactory.getLogger(AdministrationService.class)
                                        .warn("E-mail d'alerte candidature non envoyé : {}", e.getMessage());
                            }
                        });
            }
        }
    }

    /**
     * Vrai si la fiche existe et n'appartient encore a aucun compte : une fiche creee ou reclamee
     * porte l'identifiant du compte du praticien, qui a donc une candidature validee.
     */
    public boolean ficheReclamable(UUID ficheId) {
        return medecins.parId(ficheId).isPresent()
                && candidatures.derniereDuMedecin(ficheId)
                        .filter(c -> c.statut() == StatutCandidature.VALIDEE).isEmpty();
    }

    /**
     * La derniere candidature du medecin connecte.
     * @throws CandidatureIntrouvableException s'il n'en a depose aucune.
     */
    public CandidatureMedecin maCandidature(UUID medecinId) {
        return candidatures.derniereDuMedecin(medecinId)
                .orElseThrow(CandidatureIntrouvableException::aucuneDeposee);
    }

    /** Vrai si la candidature est validee mais que la fiche du medecin n'est plus dans l'annuaire (suspendu). */
    public boolean estSuspendu(CandidatureMedecin c) {
        return c.statut() == StatutCandidature.VALIDEE && medecins.parId(c.medecinId()).isEmpty();
    }

    /** Candidatures, eventuellement filtrees par statut, de la plus ancienne a la plus recente. */
    public List<CandidatureMedecin> lister(Optional<StatutCandidature> statut) {
        return candidatures.lister(statut);
    }

    /**
     * Valide une candidature : le medecin est publie dans l'annuaire et prevenu.
     * @throws CandidatureIntrouvableException si la candidature n'existe pas.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    @Transactional
    public CandidatureMedecin valider(UUID candidatureId) {
        CandidatureMedecin candidature = charger(candidatureId);
        if (candidature.estUneReclamation() && medecins.parId(candidature.ficheReclamee()).isEmpty()) {
            throw new TransitionInvalideException("Cette fiche a déjà été réclamée ou n'existe plus.");
        }
        CandidatureMedecin validee = candidatures.enregistrer(candidature.valider(Instant.now()));
        if (validee.estUneReclamation() && !validee.ficheReclamee().equals(validee.medecinId())) {
            // La fiche importee est remplacee par celle du praticien, sous l'identifiant de son compte ; son ancien
            // identifiant reste un alias (URL SEO /praticiens/{ancienId}) et sa liste d'attente suit le praticien.
            medecins.supprimer(validee.ficheReclamee());
            medecins.ajouterAlias(validee.ficheReclamee(), validee.medecinId());
            if (listeAttente != null) {
                listeAttente.reporter(validee.ficheReclamee(), validee.medecinId());
            }
        }
        medecins.enregistrer(fichePublique(validee));
        roles.accorder(validee.medecinId());
        String langue = Messages.langueDe(profils, validee.medecinId());
        notifieur.notifier(validee.medecinId(), Messages.candidatureValideeSujet(langue),
                Messages.candidatureValideeMessage(langue), "/medecin/agenda");
        return validee;
    }

    /**
     * Refuse une candidature en motivant la decision ; le medecin est prevenu avec le motif.
     * @throws CandidatureIntrouvableException si la candidature n'existe pas.
     * @throws CandidatureInvalideException si le motif manque.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    @Transactional
    public CandidatureMedecin refuser(UUID candidatureId, String motif) {
        CandidatureMedecin refusee = candidatures.enregistrer(charger(candidatureId).refuser(motif, Instant.now()));
        String langue = Messages.langueDe(profils, refusee.medecinId());
        notifieur.notifier(refusee.medecinId(), Messages.candidatureRefuseeSujet(langue),
                Messages.candidatureRefuseeMessage(langue, refusee.motifRefus()), "/medecin/candidature");
        return refusee;
    }

    public StatistiquesAdministration statistiques() {
        return new StatistiquesAdministration(
                candidatures.compter(StatutCandidature.EN_ATTENTE),
                candidatures.compter(StatutCandidature.VALIDEE),
                candidatures.compter(StatutCandidature.REFUSEE),
                avis == null ? 0 : avis.parStatut(dz.tabibi.backend.avis.domain.StatutAvis.SIGNALE).size(),
                demandesPraticien == null ? 0
                        : demandesPraticien.lister(Optional.of(dz.tabibi.backend.demandes.domain.StatutDemande.NOUVELLE)).size());
    }

    /** Demandes praticien : sert a compter les demandes nouvelles a traiter. */
    private dz.tabibi.backend.demandes.domain.DemandePraticienRepository demandesPraticien;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDemandesPraticien(dz.tabibi.backend.demandes.domain.DemandePraticienRepository demandesPraticien) {
        this.demandesPraticien = demandesPraticien;
    }

    private CandidatureMedecin charger(UUID candidatureId) {
        return candidatures.parId(candidatureId)
                .orElseThrow(() -> new CandidatureIntrouvableException(candidatureId));
    }

    /**
     * Fiche publique du praticien, sous l'identifiant du medecin (sujet de son jeton).
     * Les libelles facultatifs absents sont remplaces par le code correspondant : l'annuaire les exige.
     */
    private static Medecin fichePublique(CandidatureMedecin c) {
        return new Medecin(c.medecinId(), c.nomComplet(), c.specialiteSlug(),
                c.specialiteFr() != null ? c.specialiteFr() : c.specialiteSlug(),
                c.wilayaCode(),
                c.wilayaFr() != null ? c.wilayaFr() : c.wilayaCode(),
                c.ville());
    }
}
