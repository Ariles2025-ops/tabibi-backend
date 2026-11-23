package dz.tabibi.backend.rendezvous.domain;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.UUID;

/**
 * Un rendez-vous entre un patient et un medecin, sur un creneau donne.
 * Le comportement metier (confirmer, annuler, honorer, rappeler) vit dans l'entite.
 */
public class RendezVous {

    private final UUID id;
    private UUID patientId;
    private final UUID medecinId;
    private final Instant debut;
    /** Creneau de l'agenda reserve, ou null pour un rendez-vous pris sur un horaire libre. */
    private final UUID creneauId;
    private StatutRdv statut;
    /** Date d'envoi du rappel de la veille au patient, ou null tant qu'il n'a pas ete envoye. */
    private Instant rappelEnvoyeLe;
    /** Origine du rendez-vous : application (patient connecte) ou telephone (saisi par le cabinet). */
    private final SourceRdv source;
    /** Nom saisi par le cabinet : uniquement pour un rendez-vous pris par telephone (donnee personnelle). */
    private String nomPatient;
    /** Telephone saisi par le cabinet : uniquement pour un rendez-vous pris par telephone (donnee personnelle). */
    private String telephonePatient;
    /** Motif facultatif saisi par le cabinet (rendez-vous pris par telephone). */
    private String motif;
    /** Nombre d'envois SMS de rappel refuses par la passerelle (0 si aucun echec). */
    private int smsEchecs;
    /** Vrai tant que le SMS de rappel a echoue et doit etre repris (tant que le rendez-vous est a venir). */
    private boolean smsAReprendre;

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut) {
        this(id, patientId, medecinId, debut, statut, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId) {
        this(id, patientId, medecinId, debut, statut, creneauId, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId,
                      Instant rappelEnvoyeLe) {
        this(id, patientId, medecinId, debut, statut, creneauId, rappelEnvoyeLe, SourceRdv.APPLICATION, null, null, null);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId,
                      Instant rappelEnvoyeLe, SourceRdv source, String nomPatient, String telephonePatient,
                      String motif) {
        this(id, patientId, medecinId, debut, statut, creneauId, rappelEnvoyeLe, source, nomPatient,
                telephonePatient, motif, 0, false);
    }

    public RendezVous(UUID id, UUID patientId, UUID medecinId, Instant debut, StatutRdv statut, UUID creneauId,
                      Instant rappelEnvoyeLe, SourceRdv source, String nomPatient, String telephonePatient,
                      String motif, int smsEchecs, boolean smsAReprendre) {
        this.smsEchecs = smsEchecs;
        this.smsAReprendre = smsAReprendre;
        this.id = id;
        this.source = source == null ? SourceRdv.APPLICATION : source;
        this.nomPatient = nomPatient;
        this.telephonePatient = telephonePatient;
        this.motif = motif;
        this.patientId = patientId;
        this.medecinId = medecinId;
        this.debut = debut;
        this.statut = statut;
        this.creneauId = creneauId;
        this.rappelEnvoyeLe = rappelEnvoyeLe;
    }

    /** Cree un rendez-vous confirme sur un horaire libre (hors agenda). */
    public static RendezVous confirmer(UUID patientId, UUID medecinId, Instant debut) {
        return confirmer(patientId, medecinId, debut, null);
    }

    /** Cree un rendez-vous confirme sur un creneau de l'agenda du medecin. */
    public static RendezVous confirmer(UUID patientId, UUID medecinId, Instant debut, UUID creneauId) {
        return new RendezVous(UUID.randomUUID(), patientId, medecinId, debut, StatutRdv.CONFIRME, creneauId);
    }

    /**
     * Cree un rendez-vous confirme pris par telephone pour un patient sans compte : aucun compte lie,
     * nom et telephone saisis par le cabinet.
     */
    public static RendezVous parTelephone(UUID medecinId, Instant debut, UUID creneauId, String nomPatient,
                                          String telephonePatient, String motif) {
        return new RendezVous(UUID.randomUUID(), null, medecinId, debut, StatutRdv.CONFIRME, creneauId, null,
                SourceRdv.TELEPHONE, nomPatient, telephonePatient, motif);
    }

    /** Efface les donnees personnelles saisies par le cabinet (suppression / anonymisation). */
    public void anonymiser(String nomAnonyme) {
        if (source == SourceRdv.TELEPHONE) {
            this.nomPatient = nomAnonyme;
            this.telephonePatient = null;
            this.motif = null;
        }
    }

    public boolean estTelephonique() {
        return source == SourceRdv.TELEPHONE;
    }

    public void annuler() {
        this.statut = StatutRdv.ANNULE;
    }

    /**
     * Le cabinet (le medecin ou une secretaire rattachee) annule le rendez-vous : uniquement depuis CONFIRME,
     * a la difference de l'annulation par le patient, idempotente.
     * @throws TransitionInvalideException s'il est deja annule ou honore.
     */
    public void annulerParCabinet() {
        if (statut != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirmé peut être annulé par le cabinet (statut actuel : " + statut.libelle() + ").");
        }
        if (!debut.isAfter(Instant.now())) {
            throw new TransitionInvalideException(
                    "Ce rendez-vous est déjà passé : il ne peut plus être annulé par le cabinet.");
        }
        this.statut = StatutRdv.ANNULE;
    }

    /**
     * Suppression du compte du patient : le rendez-vous reste dans l'agenda du medecin (trace) mais n'est plus
     * lie a aucun compte et n'a plus aucune donnee personnelle (nom remplace, telephone et motif effaces).
     * Irreversible : l'identifiant du patient est perdu.
     */
    public void anonymiserPatient(String nomAnonyme) {
        this.patientId = null;
        this.nomPatient = nomAnonyme;
        this.telephonePatient = null;
        this.motif = null;
    }

    /**
     * Le patient est venu : le rendez-vous passe a HONORE.
     * @throws TransitionInvalideException s'il n'est pas confirme (annule ou deja honore).
     */
    public void honorer() {
        if (statut != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous confirmé peut être honoré (statut actuel : " + statut.libelle() + ").");
        }
        if (debut.isAfter(Instant.now())) {
            throw new TransitionInvalideException("Ce rendez-vous n'a pas encore eu lieu : il ne peut pas être marqué comme honoré.");
        }
        this.statut = StatutRdv.HONORE;
    }

    /**
     * Cloture automatique (tache planifiee) : un rendez-vous confirme que le cabinet n'a jamais marque passe a HONORE.
     * Sans effet s'il n'est plus confirme. @return vrai si le statut a change.
     */
    public boolean cloturerAutomatiquement() {
        if (statut != StatutRdv.CONFIRME) {
            return false;
        }
        this.statut = StatutRdv.HONORE;
        return true;
    }

    /**
     * Le patient n'est pas venu : le cabinet le constate une fois l'heure passee.
     * @throws TransitionInvalideException s'il n'est pas confirme ou n'a pas encore commence.
     */
    public void marquerAbsent() {
        if (statut != StatutRdv.CONFIRME) {
            throw new TransitionInvalideException("Seul un rendez-vous confirmé peut être marqué « absent ».");
        }
        if (debut.isAfter(Instant.now())) {
            throw new TransitionInvalideException("Ce rendez-vous n'a pas encore eu lieu : le patient ne peut pas être marqué absent.");
        }
        this.statut = StatutRdv.ABSENT;
    }

    public boolean appartientA(UUID unPatientId) {
        return patientId != null && patientId.equals(unPatientId);
    }

    /** Vrai si le rendez-vous est dans l'agenda de ce medecin. */
    public boolean estAvec(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }

    public boolean estAnnule() {
        return statut == StatutRdv.ANNULE;
    }

    /** Le rappel de la veille a ete envoye au patient a cette date : il ne le sera plus. */
    public void marquerRappelEnvoye(Instant quand) {
        this.rappelEnvoyeLe = quand;
    }

    public boolean rappelEnvoye() {
        return rappelEnvoyeLe != null;
    }

    /** Le SMS de rappel a ete refuse : il sera repris au passage suivant. */
    public void marquerSmsEnEchec() {
        this.smsEchecs++;
        this.smsAReprendre = true;
    }

    /** Le SMS de rappel est parti (ou n'est plus a envoyer) : plus de reprise. */
    public void marquerSmsEnvoye() {
        this.smsAReprendre = false;
    }

    public int smsEchecs() { return smsEchecs; }
    public boolean smsAReprendre() { return smsAReprendre; }

    public SourceRdv source() { return source; }
    public String nomPatient() { return nomPatient; }
    public String telephonePatient() { return telephonePatient; }
    public String motif() { return motif; }
    public UUID id() { return id; }
    /** Null pour un rendez-vous pris par telephone (patient sans compte). */
    public UUID patientId() { return patientId; }
    public UUID medecinId() { return medecinId; }
    public Instant debut() { return debut; }
    /** Peut etre null (rendez-vous pris hors agenda). */
    public UUID creneauId() { return creneauId; }
    public StatutRdv statut() { return statut; }
    /** Null tant que le rappel de la veille n'a pas ete envoye. */
    public Instant rappelEnvoyeLe() { return rappelEnvoyeLe; }
}
