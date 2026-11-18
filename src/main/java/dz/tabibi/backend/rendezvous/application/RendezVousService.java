package dz.tabibi.backend.rendezvous.application;

import dz.tabibi.backend.cabinet.domain.Rattachement;
import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rappels.domain.EnvoiSms;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des rendez-vous. Contient la regle metier, pas le client.
 * Le patient, le medecin et ses secretaires sont prevenus (port Notifieur) a la reservation, le medecin et ses
 * secretaires a l'annulation par le patient, le patient (et par SMS) a l'annulation par le cabinet ;
 * un creneau remis a disposition est signale au port AlerteCreneau (liste d'attente du medecin).
 */
@Service
public class RendezVousService {

    private final RendezVousRepository repository;
    private final CreneauRepository creneaux;
    private final Notifieur notifieur;
    private final AlerteCreneau alerteCreneau;

    public RendezVousService(RendezVousRepository repository, CreneauRepository creneaux, Notifieur notifieur,
                             AlerteCreneau alerteCreneau) {
        this.repository = repository;
        this.creneaux = creneaux;
        this.notifieur = notifieur;
        this.alerteCreneau = alerteCreneau;
    }

    private static final Logger log = LoggerFactory.getLogger(RendezVousService.class);

    /** Secretaires du cabinet : prevenues des reservations et annulations des patients. */
    private RattachementRepository rattachements;
    /** SMS au patient quand le cabinet annule (numero verifie du compte), si une passerelle est configuree. */
    private EnvoiSms sms;
    private ComptesUtilisateurs comptes;

    /** Profils : un medecin dont le compte est supprime (profil anonyme) n'est plus reservable. */
    private ProfilRepository profils;

    @Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

    /** Vrai si le compte de ce medecin a ete supprime (son profil porte le nom anonyme ecrit a la suppression). */
    private boolean compteSupprime(UUID medecinId) {
        return profils != null && profils.parUtilisateur(medecinId)
                .map(p -> NOM_ANONYME.equals(p.nomComplet())).orElse(false);
    }

    private void verifierMedecinActif(UUID medecinId) {
        if (compteSupprime(medecinId)) {
            throw new CreneauDejaReserveException("Ce médecin n'est plus disponible : son compte a été supprimé.");
        }
    }

    @Autowired(required = false)
    public void setCabinet(RattachementRepository rattachements) {
        this.rattachements = rattachements;
    }

    @Autowired(required = false)
    public void setSms(EnvoiSms sms, ComptesUtilisateurs comptes) {
        this.sms = sms;
        this.comptes = comptes;
    }

    /**
     * Reserve un creneau de l'agenda d'un medecin : le rendez-vous est cree sur
     * l'horaire du creneau, qui cesse d'etre propose aux autres patients.
     * @throws CreneauIntrouvableException si le creneau n'existe pas.
     * @throws CreneauDejaReserveException si le creneau n'est plus disponible.
     */
    @Transactional
    public RendezVous reserverCreneau(UUID patientId, UUID creneauId) {
        Creneau creneau = creneaux.parId(creneauId)
                .orElseThrow(() -> new CreneauIntrouvableException(creneauId));
        verifierMedecinActif(creneau.medecinId());
        if (!creneau.disponible()) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        if (!creneau.debut().isAfter(Instant.now())) {
            throw new CreneauDejaReserveException("Ce créneau est déjà passé.");
        }
        if (creneau.medecinId().equals(patientId)) {
            throw new dz.tabibi.backend.creneaux.domain.CreneauInvalideException(
                    "Vous ne pouvez pas réserver un rendez-vous avec vous-même.");
        }
        verifierPasDeRendezVousAVenir(patientId, creneau.medecinId());
        // Prise atomique (UPDATE ... where disponible = true) : de deux reservations simultanees, une seule passe.
        // L'index unique partiel sur rendez_vous(creneau_id) (rendez-vous non annules) garde la base coherente.
        if (!creneaux.reserverSiDisponible(creneau.id())) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        RendezVous rdv = repository.enregistrer(
                RendezVous.confirmer(patientId, creneau.medecinId(), creneau.debut(), creneau.id()));
        alerteCreneau.patientAReserve(rdv.patientId(), rdv.medecinId());
        notifierReservation(rdv);
        return rdv;
    }

    /** Longueurs maximales des donnees saisies par le cabinet (colonnes de la base). */
    private static final int NOM_MAX = 200;
    private static final int TELEPHONE_MAX = 40;
    private static final int MOTIF_MAX = 500;
    /** Nom affiche a la place du nom saisi, une fois les donnees effacees. */
    public static final String NOM_ANONYME = "Compte supprimé";

    /**
     * Le cabinet prend un rendez-vous pour un patient qui a appele (sans compte) : meme prise atomique
     * du creneau que la reservation patient (un seul gagnant en cas de course). Le rendez-vous est de source
     * TELEPHONE, lie a aucun compte ; aucun message n'est envoye (ni au patient, ni de rappel). L'autorisation
     * (medecin ou secretaire rattachee) est controlee par l'appelant.
     * @throws CreneauInvalideException si le nom ou le telephone manque ou est trop long.
     * @throws CreneauIntrouvableException si le creneau n'existe pas ou n'est pas dans l'agenda de ce medecin.
     * @throws CreneauDejaReserveException si le creneau n'est plus disponible ou est passe.
     */
    @Transactional
    public RendezVous prendreParTelephone(UUID medecinId, UUID creneauId, String nomPatient,
                                          String telephonePatient, String motif) {
        String nom = nomPatient == null ? "" : nomPatient.strip();
        String telephone = telephonePatient == null ? "" : telephonePatient.strip();
        String motifNet = motif == null || motif.isBlank() ? null : motif.strip();
        if (creneauId == null) {
            throw new CreneauInvalideException("Le créneau est obligatoire.");
        }
        if (nom.isEmpty() || nom.length() > NOM_MAX) {
            throw new CreneauInvalideException("Le nom du patient est obligatoire (" + NOM_MAX + " caractères au plus).");
        }
        if (telephone.isEmpty() || telephone.length() > TELEPHONE_MAX) {
            throw new CreneauInvalideException("Le téléphone du patient est obligatoire (" + TELEPHONE_MAX + " caractères au plus).");
        }
        if (motifNet != null && motifNet.length() > MOTIF_MAX) {
            throw new CreneauInvalideException("Le motif est trop long (" + MOTIF_MAX + " caractères au plus).");
        }
        Creneau creneau = creneaux.parId(creneauId)
                .filter(c -> c.medecinId().equals(medecinId))
                .orElseThrow(() -> new CreneauIntrouvableException(creneauId));
        verifierMedecinActif(medecinId);
        if (!creneau.disponible()) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        if (!creneau.debut().isAfter(Instant.now())) {
            throw new CreneauDejaReserveException("Ce créneau est déjà passé.");
        }
        // Meme prise atomique que la reservation patient (UPDATE ... where disponible = true).
        if (!creneaux.reserverSiDisponible(creneau.id())) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        return repository.enregistrer(
                RendezVous.parTelephone(medecinId, creneau.debut(), creneau.id(), nom, telephone, motifNet));
    }

    /**
     * Efface le nom, le telephone et le motif des rendez-vous pris par telephone dans l'agenda de ce medecin
     * (suppression de son compte). @return le nombre de rendez-vous anonymises.
     */
    @Transactional
    public int anonymiserRendezVousTelephoniques(UUID medecinId) {
        List<RendezVous> telephoniques = repository.parMedecin(medecinId).stream()
                .filter(RendezVous::estTelephonique).toList();
        telephoniques.forEach(r -> {
            r.anonymiser(NOM_ANONYME);
            repository.enregistrer(r);
        });
        return telephoniques.size();
    }

    /**
     * Un patient ne garde qu'un rendez-vous a venir par medecin : il ne peut pas bloquer tout l'agenda.
     * @throws CreneauDejaReserveException s'il a deja un rendez-vous confirme a venir avec ce medecin.
     */
    private void verifierPasDeRendezVousAVenir(UUID patientId, UUID medecinId) {
        Instant maintenant = Instant.now();
        boolean dejaUn = repository.parPatient(patientId).stream()
                .anyMatch(r -> r.estAvec(medecinId) && r.statut() == StatutRdv.CONFIRME && r.debut().isAfter(maintenant));
        if (dejaUn) {
            throw new CreneauDejaReserveException(
                    "Vous avez déjà un rendez-vous à venir avec ce médecin : annulez-le pour en choisir un autre.");
        }
    }

    /** Rendez-vous d'un patient, tous statuts, du plus proche au plus lointain. */
    public List<RendezVous> mesRendezVous(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /**
     * Annule un rendez-vous du patient et remet son creneau a disposition (la liste d'attente
     * du medecin est alors alertee). Annuler un rendez-vous deja annule ne change rien : son
     * creneau a pu etre repris entre-temps par un autre patient et ne doit pas etre libere a nouveau.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si le rendez-vous appartient a un autre patient.
     */
    @Transactional
    public RendezVous annuler(UUID patientId, UUID rendezVousId) {
        RendezVous rdv = repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.appartientA(patientId)) {
            throw new AccesRefuseException("Ce rendez-vous ne vous appartient pas.");
        }
        if (rdv.estAnnule()) {
            return rdv;
        }
        if (rdv.statut() == StatutRdv.HONORE) {
            throw new TransitionInvalideException("Ce rendez-vous a déjà eu lieu : il ne peut plus être annulé.");
        }
        if (!rdv.debut().isAfter(Instant.now())) {
            throw new TransitionInvalideException("Ce rendez-vous est déjà passé : il ne peut plus être annulé.");
        }
        rdv.annuler();
        libererCreneau(rdv);
        RendezVous annule = repository.enregistrer(rdv);
        String message = "Le rendez-vous du " + FormatDate.lisible(annule.debut()) + " a été annulé par le patient.";
        notifieur.notifier(annule.medecinId(), "Rendez-vous annulé", message, "/medecin/agenda");
        secretairesDe(annule.medecinId()).forEach(s ->
                notifieur.notifier(s, "Rendez-vous annulé", message, "/secretaire"));
        return annule;
    }

    /**
     * Suppression du compte du patient : chacun de ses rendez-vous confirmes a venir est annule comme s'il
     * l'annulait lui-meme (creneau libere, cabinet prevenu). Les rendez-vous passes restent (historique du cabinet).
     * @return le nombre de rendez-vous annules.
     */
    @Transactional
    public int annulerAVenirDuPatient(UUID patientId) {
        Instant maintenant = Instant.now();
        List<RendezVous> aVenir = repository.parPatient(patientId).stream()
                .filter(r -> r.statut() == StatutRdv.CONFIRME && r.debut().isAfter(maintenant))
                .toList();
        aVenir.forEach(r -> annuler(patientId, r.id()));
        return aVenir.size();
    }

    /**
     * Suppression du compte du patient (apres l'annulation de ses rendez-vous a venir) : tous les rendez-vous
     * qui lui restent (passes, honores, absents, annules) sont anonymises de facon irreversible. Le medecin
     * garde la trace de la consultation, sans plus aucun lien avec le patient. @return leur nombre.
     */
    @Transactional
    public int anonymiserRendezVousDuPatient(UUID patientId) {
        List<RendezVous> restants = repository.parPatient(patientId);
        restants.forEach(r -> {
            r.anonymiserPatient(NOM_ANONYME);
            repository.enregistrer(r);
        });
        return restants.size();
    }

    /**
     * Suppression du compte du medecin : chacun de ses rendez-vous confirmes a venir est annule « par le cabinet »
     * (le patient est prevenu dans l'appli et par SMS), puis tous ses creneaux libres sont supprimes. Son agenda
     * n'est plus reservable ; la liste d'attente n'est pas alertee (le medecin n'existe plus).
     * @return le nombre de rendez-vous annules.
     */
    @Transactional
    public int annulerAVenirDuMedecin(UUID medecinId) {
        Instant maintenant = Instant.now();
        List<RendezVous> aVenir = repository.parMedecin(medecinId).stream()
                .filter(r -> r.statut() == StatutRdv.CONFIRME && r.debut().isAfter(maintenant))
                .toList();
        for (RendezVous rdv : aVenir) {
            rdv.annulerParCabinet();
            if (rdv.creneauId() != null) {
                creneaux.parId(rdv.creneauId()).map(Creneau::liberer).ifPresent(creneaux::enregistrer);
            }
            RendezVous annule = repository.enregistrer(rdv);
            if (annule.patientId() != null) {
                notifierAnnulationParCabinet(annule);
            }
        }
        creneaux.supprimerLibresDuMedecin(medecinId);
        return aVenir.size();
    }

    /** Agenda d'un medecin : ses rendez-vous, tous statuts, du plus proche au plus lointain. */
    public List<RendezVous> agendaDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /**
     * Marque un rendez-vous comme honore (le patient est venu). Reserve au medecin du rendez-vous.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas confirme (annule ou deja honore).
     */
    @Transactional
    public RendezVous honorer(UUID medecinId, UUID rendezVousId) {
        RendezVous rdv = repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        rdv.honorer();
        return repository.enregistrer(rdv);
    }

    /**
     * Le patient n'est pas venu : le medecin (ou sa secretaire) le constate apres l'heure du rendez-vous.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas confirme ou pas encore commence.
     */
    @Transactional
    public RendezVous marquerAbsent(UUID medecinId, UUID rendezVousId) {
        return marquerAbsent(medecinId, rendezVousId, medecinId);
    }

    /**
     * Comme {@link #marquerAbsent(UUID, UUID)}, fait par {@code auteurId} (le medecin ou sa secretaire) :
     * quand c'est la secretaire, le medecin est prevenu.
     */
    @Transactional
    public RendezVous marquerAbsent(UUID medecinId, UUID rendezVousId, UUID auteurId) {
        RendezVous rdv = parId(rendezVousId);
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        rdv.marquerAbsent();
        RendezVous absent = repository.enregistrer(rdv);
        if (absent.patientId() != null) {
            notifieur.notifier(absent.patientId(), "Rendez-vous manqué",
                    "Vous n'êtes pas venu à votre rendez-vous du " + FormatDate.lisible(absent.debut())
                            + ". Pensez à annuler à l'avance pour libérer la place.", "/mes-rendez-vous");
        }
        if (!medecinId.equals(auteurId)) {
            notifieur.notifier(medecinId, "Patient absent",
                    "Votre secrétaire a marqué absent le patient du rendez-vous du " + FormatDate.lisible(absent.debut()) + ".",
                    "/medecin/agenda");
        }
        return absent;
    }

    /**
     * Un rendez-vous par son identifiant, pour les cas d'usage d'autres modules (cabinet) qui
     * appliquent ensuite leur propre regle d'acces.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     */
    public RendezVous parId(UUID rendezVousId) {
        return repository.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
    }

    /**
     * Le cabinet (le medecin, ou une secretaire rattachee agissant pour lui) annule un rendez-vous
     * confirme de l'agenda du medecin : son creneau est remis a disposition (la liste d'attente est
     * alertee) et le patient est prevenu.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il est deja annule ou honore.
     */
    @Transactional
    public RendezVous annulerParCabinet(UUID medecinId, UUID rendezVousId) {
        return annulerParCabinet(medecinId, rendezVousId, medecinId);
    }

    /**
     * Comme {@link #annulerParCabinet(UUID, UUID)}, fait par {@code auteurId} (le medecin ou sa secretaire) :
     * le patient est prevenu dans l'appli et par SMS ; quand c'est la secretaire, le medecin est prevenu aussi.
     */
    @Transactional
    public RendezVous annulerParCabinet(UUID medecinId, UUID rendezVousId, UUID auteurId) {
        RendezVous rdv = parId(rendezVousId);
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        rdv.annulerParCabinet();
        libererCreneau(rdv);
        RendezVous annule = repository.enregistrer(rdv);
        // Patient sans compte (rendez-vous pris par telephone) : aucun message, le cabinet le previent lui-meme.
        if (annule.patientId() != null) {
            notifierAnnulationParCabinet(annule);
        }
        if (!medecinId.equals(auteurId)) {
            notifieur.notifier(medecinId, "Rendez-vous annulé par votre secrétaire",
                    "Votre secrétaire a annulé le rendez-vous du " + FormatDate.lisible(annule.debut()) + ".", "/medecin/agenda");
        }
        return annule;
    }

    /**
     * Le patient deplace son rendez-vous vers un autre creneau du meme medecin, en une seule transaction :
     * le nouveau creneau est pris de facon atomique, l'ancien est libere (liste d'attente alertee), le medecin
     * et ses secretaires sont prevenus. Si le nouveau creneau est pris, rien ne change (409).
     * Le deplacement cree un nouveau rendez-vous (les rappels repartent donc de zero) et annule l'ancien.
     * @throws AccesRefuseException si le rendez-vous appartient a un autre patient.
     * @throws TransitionInvalideException s'il est annule, honore, absent ou passe.
     * @throws CreneauDejaReserveException si le nouveau creneau n'est plus disponible.
     * @throws CreneauInvalideException si le nouveau creneau est d'un autre medecin ou est l'actuel.
     */
    @Transactional
    public RendezVous deplacer(UUID patientId, UUID rendezVousId, UUID nouveauCreneauId) {
        RendezVous rdv = parId(rendezVousId);
        if (!rdv.appartientA(patientId)) {
            throw new AccesRefuseException("Ce rendez-vous ne vous appartient pas.");
        }
        RendezVous nouveau = deplacerRdv(rdv, nouveauCreneauId);
        String message = "Un patient a déplacé son rendez-vous du " + FormatDate.lisible(rdv.debut())
                + " au " + FormatDate.lisible(nouveau.debut()) + ".";
        notifieur.notifier(nouveau.medecinId(), "Rendez-vous déplacé", message, "/medecin/agenda");
        secretairesDe(nouveau.medecinId()).forEach(s -> notifieur.notifier(s, "Rendez-vous déplacé", message, "/secretaire"));
        return nouveau;
    }

    /**
     * Le cabinet (medecin, ou secretaire rattachee agissant pour lui : {@code auteurId}) deplace un rendez-vous
     * de l'agenda du medecin. Memes regles que {@link #deplacer} ; le patient est prevenu (appli + SMS) et,
     * si c'est la secretaire, le medecin aussi.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     */
    @Transactional
    public RendezVous deplacerParCabinet(UUID medecinId, UUID rendezVousId, UUID nouveauCreneauId, UUID auteurId) {
        RendezVous rdv = parId(rendezVousId);
        if (!rdv.estAvec(medecinId)) {
            throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
        }
        RendezVous nouveau = deplacerRdv(rdv, nouveauCreneauId);
        if (nouveau.patientId() != null) {
            String texte = "Votre rendez-vous du " + FormatDate.lisible(rdv.debut()) + " a été déplacé par le cabinet au "
                    + FormatDate.lisible(nouveau.debut()) + ".";
            notifieur.notifier(nouveau.patientId(), "Rendez-vous déplacé", texte, "/mes-rendez-vous");
            smsPatient(nouveau, "Ndawik : votre rendez-vous du " + FormatDate.lisible(rdv.debut())
                    + " a ete deplace au " + FormatDate.lisible(nouveau.debut()) + ".");
        }
        if (!medecinId.equals(auteurId)) {
            notifieur.notifier(medecinId, "Rendez-vous déplacé par votre secrétaire",
                    "Votre secrétaire a déplacé le rendez-vous du " + FormatDate.lisible(rdv.debut()) + " au "
                            + FormatDate.lisible(nouveau.debut()) + ".", "/medecin/agenda");
        }
        return nouveau;
    }

    /** Coeur du deplacement : controles, prise atomique du nouveau creneau, puis annulation de l'ancien rendez-vous. */
    private RendezVous deplacerRdv(RendezVous rdv, UUID nouveauCreneauId) {
        if (rdv.statut() != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirmé peut être déplacé (statut actuel : " + rdv.statut() + ").");
        }
        if (!rdv.debut().isAfter(Instant.now())) {
            throw new TransitionInvalideException("Ce rendez-vous est déjà passé : il ne peut plus être déplacé.");
        }
        if (nouveauCreneauId == null) {
            throw new CreneauInvalideException("Le nouveau créneau est obligatoire.");
        }
        Creneau creneau = creneaux.parId(nouveauCreneauId)
                .orElseThrow(() -> new CreneauIntrouvableException(nouveauCreneauId));
        if (!creneau.medecinId().equals(rdv.medecinId())) {
            throw new CreneauInvalideException("Le nouveau créneau doit être dans l'agenda du même médecin.");
        }
        if (nouveauCreneauId.equals(rdv.creneauId())) {
            throw new CreneauInvalideException("Le nouveau créneau est celui du rendez-vous actuel.");
        }
        verifierMedecinActif(creneau.medecinId());
        if (!creneau.disponible() || !creneau.debut().isAfter(Instant.now())) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        // Meme prise atomique que la reservation : de deux demandes simultanees, une seule passe. Si elle echoue,
        // l'exception annule la transaction : l'ancien rendez-vous n'est pas touche.
        if (!creneaux.reserverSiDisponible(creneau.id())) {
            throw new CreneauDejaReserveException("Ce créneau n'est plus disponible.");
        }
        RendezVous nouveau = repository.enregistrer(new RendezVous(UUID.randomUUID(), rdv.patientId(), rdv.medecinId(),
                creneau.debut(), StatutRdv.CONFIRME, creneau.id(), null, rdv.source(), rdv.nomPatient(),
                rdv.telephonePatient(), rdv.motif()));
        rdv.annuler();
        libererCreneau(rdv);
        repository.enregistrer(rdv);
        return nouveau;
    }

    /** Previent le patient (appli + SMS) que le cabinet a annule son rendez-vous. */
    private void notifierAnnulationParCabinet(RendezVous annule) {
        notifieur.notifier(annule.patientId(), "Rendez-vous annulé par le cabinet",
                "Votre rendez-vous du " + FormatDate.lisible(annule.debut())
                        + " a été annulé par le cabinet. Vous pouvez réserver un autre créneau.", "/medecins/" + annule.medecinId());
        smsAnnulationParCabinet(annule);
    }

    /**
     * SMS court (sans accents : un seul segment) au numero verifie du compte du patient. Un echec (numero
     * absent, passerelle) n'empeche jamais l'annulation ni la notification dans l'appli.
     */
    private void smsAnnulationParCabinet(RendezVous rdv) {
        smsPatient(rdv, "Ndawik : votre rendez-vous du " + FormatDate.lisible(rdv.debut())
                + " a ete annule par le cabinet. Reprenez un creneau sur tabibi.doctor");
    }

    /** SMS au numero verifie du compte du patient du rendez-vous ; un echec n'empeche jamais l'operation. */
    private void smsPatient(RendezVous rdv, String texte) {
        if (sms == null || comptes == null || !sms.disponible()) {
            return;
        }
        try {
            String tel = comptes.infos(rdv.patientId()).map(ComptesUtilisateurs.InfosCompte::telephone)
                    .map(EnvoiSms::normaliser).orElse(null);
            if (tel == null) {
                return;
            }
            sms.envoyer(tel, texte);
        } catch (RuntimeException e) {
            log.warn("SMS non envoye pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
        }
    }

    /** Secretaires qui ont accepte de gerer l'agenda de ce medecin. */
    private List<UUID> secretairesDe(UUID medecinId) {
        if (rattachements == null) {
            return List.of();
        }
        return rattachements.parMedecin(medecinId).stream()
                .filter(Rattachement::accepte).map(Rattachement::secretaireId).toList();
    }

    /**
     * Remet a disposition le creneau de l'agenda reserve par ce rendez-vous, s'il y en a un, et
     * alerte la liste d'attente du medecin ; sans effet pour un rendez-vous pris hors agenda.
     */
    private void libererCreneau(RendezVous rdv) {
        if (rdv.creneauId() == null || !rdv.debut().isAfter(Instant.now())) {
            return;
        }
        creneaux.parId(rdv.creneauId()).map(Creneau::liberer).ifPresent(creneau -> {
            creneaux.enregistrer(creneau);
            alerteCreneau.creneauLibere(creneau.medecinId(), creneau.debut());
        });
    }

    /** Previent le patient (confirmation) et le medecin (nouveau rendez-vous dans son agenda). */
    private void notifierReservation(RendezVous rdv) {
        String date = FormatDate.lisible(rdv.debut());
        notifieur.notifier(rdv.patientId(), "Rendez-vous confirmé",
                "Votre rendez-vous du " + date + " est confirmé.", "/mes-rendez-vous");
        String message = "Un patient a réservé un rendez-vous le " + date + ".";
        notifieur.notifier(rdv.medecinId(), "Nouveau rendez-vous", message, "/medecin/agenda");
        secretairesDe(rdv.medecinId()).forEach(s ->
                notifieur.notifier(s, "Nouveau rendez-vous", message, "/secretaire"));
    }
}
