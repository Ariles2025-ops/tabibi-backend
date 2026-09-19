package dz.tabibi.backend.avis.domain;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.UUID;

/**
 * Avis verifie d'un patient sur un medecin, adosse a un rendez-vous honore (un seul par rendez-vous).
 * Valeur immuable : signaler, masquer ou retablir produit une copie. Le public ne voit que la note,
 * le commentaire et la date ; l'identite du patient ne sort jamais du module.
 */
public record Avis(
        UUID id,
        UUID rendezVousId,
        UUID patientId,
        UUID medecinId,
        int note,
        String commentaire,
        StatutAvis statut,
        Instant deposeLe
) {

    public static final int NOTE_MIN = 1;
    public static final int NOTE_MAX = 5;
    /** Longueur maximale du commentaire, en caracteres. */
    public static final int LONGUEUR_MAX_COMMENTAIRE = 500;

    /**
     * Depose un avis publie. Regles : note entre {@value #NOTE_MIN} et {@value #NOTE_MAX} ; commentaire
     * facultatif, au plus {@value #LONGUEUR_MAX_COMMENTAIRE} caracteres une fois les espaces autour retires.
     * @throws AvisInvalideException si la note est hors bornes ou le commentaire trop long.
     */
    public static Avis deposer(UUID rendezVousId, UUID patientId, UUID medecinId, int note, String commentaire,
                               Instant deposeLe) {
        if (note < NOTE_MIN || note > NOTE_MAX) {
            throw new AvisInvalideException("La note doit etre comprise entre " + NOTE_MIN + " et " + NOTE_MAX + ".");
        }
        String texte = (commentaire == null || commentaire.isBlank()) ? null : commentaire.strip();
        if (texte != null && texte.length() > LONGUEUR_MAX_COMMENTAIRE) {
            throw new AvisInvalideException(
                    "Le commentaire ne peut pas depasser " + LONGUEUR_MAX_COMMENTAIRE + " caracteres.");
        }
        return new Avis(UUID.randomUUID(), rendezVousId, patientId, medecinId, note, texte, StatutAvis.PUBLIE, deposeLe);
    }

    /**
     * Le medecin concerne signale un avis publie a l'administrateur.
     * @throws TransitionInvalideException s'il n'est pas publie (deja signale ou masque).
     */
    public Avis signaler() {
        if (statut != StatutAvis.PUBLIE) {
            throw new TransitionInvalideException(
                    "Seul un avis publie peut etre signale (statut actuel : " + statut + ").");
        }
        return avecStatut(StatutAvis.SIGNALE);
    }

    /**
     * L'administrateur retire l'avis de la vue publique.
     * @throws TransitionInvalideException s'il est deja masque.
     */
    public Avis masquer() {
        if (statut == StatutAvis.MASQUE) {
            throw new TransitionInvalideException("Cet avis est deja masque.");
        }
        return avecStatut(StatutAvis.MASQUE);
    }

    /**
     * L'administrateur remet l'avis en ligne (apres signalement ou masquage).
     * @throws TransitionInvalideException s'il est deja publie.
     */
    public Avis retablir() {
        if (statut == StatutAvis.PUBLIE) {
            throw new TransitionInvalideException("Cet avis est deja publie.");
        }
        return avecStatut(StatutAvis.PUBLIE);
    }

    public boolean estPublie() {
        return statut == StatutAvis.PUBLIE;
    }

    /** Vrai si l'avis a ete depose par ce patient. */
    public boolean estDe(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    /** Vrai si l'avis porte sur ce medecin. */
    public boolean concerne(UUID unMedecinId) {
        return medecinId.equals(unMedecinId);
    }

    private Avis avecStatut(StatutAvis nouveau) {
        return new Avis(id, rendezVousId, patientId, medecinId, note, commentaire, nouveau, deposeLe);
    }
}
