package dz.tabibi.backend.dawini.domain;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.UUID;

/**
 * Besoin de medicament publie par un patient (Dawini) : les pharmacies de la wilaya le voient, sans
 * l'identite du patient, et y repondent ; le patient le cloture quand il est satisfait.
 * Valeur immuable : la cloture produit une copie datee.
 */
public record BesoinMedicament(
        UUID id,
        UUID patientId,
        String medicament,
        String wilayaCode,
        String commune,
        String precision,
        StatutBesoin statut,
        Instant publieLe,
        Instant clotureLe
) {

    public static final int LONGUEUR_MAX_MEDICAMENT = 200;
    public static final int LONGUEUR_MAX_WILAYA = 4;
    public static final int LONGUEUR_MAX_COMMUNE = 120;
    public static final int LONGUEUR_MAX_PRECISION = 500;

    /**
     * Publie un besoin ouvert. Regles : medicament (au plus {@value #LONGUEUR_MAX_MEDICAMENT} caracteres) et
     * code de wilaya obligatoires ; commune et precision (au plus {@value #LONGUEUR_MAX_PRECISION} caracteres)
     * facultatives ; les espaces autour sont retires.
     * @throws BesoinInvalideException si une donnee obligatoire manque ou si une donnee est trop longue.
     */
    public static BesoinMedicament publier(UUID patientId, DemandeBesoin demande, Instant publieLe) {
        if (patientId == null) {
            throw new BesoinInvalideException("Le patient est obligatoire.");
        }
        if (demande == null) {
            throw new BesoinInvalideException("Le besoin est vide.");
        }
        return new BesoinMedicament(
                UUID.randomUUID(),
                patientId,
                borne(obligatoire(demande.medicament(), "Le medicament est obligatoire."),
                        LONGUEUR_MAX_MEDICAMENT, "Le nom du medicament ne peut pas depasser "),
                borne(obligatoire(demande.wilayaCode(), "La wilaya est obligatoire."),
                        LONGUEUR_MAX_WILAYA, "Le code de wilaya ne peut pas depasser "),
                borne(facultatif(demande.commune()), LONGUEUR_MAX_COMMUNE, "La commune ne peut pas depasser "),
                borne(facultatif(demande.precision()), LONGUEUR_MAX_PRECISION, "La precision ne peut pas depasser "),
                StatutBesoin.OUVERT,
                publieLe,
                null);
    }

    /**
     * Le patient cloture son besoin : les pharmacies ne le voient plus et ne peuvent plus y repondre.
     * @throws TransitionInvalideException s'il est deja cloture.
     */
    public BesoinMedicament cloturer(Instant quand) {
        if (!estOuvert()) {
            throw new TransitionInvalideException("Ce besoin est deja cloture.");
        }
        return new BesoinMedicament(id, patientId, medicament, wilayaCode, commune, precision,
                StatutBesoin.CLOTURE, publieLe, quand);
    }

    public boolean estOuvert() {
        return statut == StatutBesoin.OUVERT;
    }

    /** Vrai si le besoin a ete publie par ce patient. */
    public boolean estDe(UUID unPatientId) {
        return patientId.equals(unPatientId);
    }

    private static String obligatoire(String valeur, String message) {
        if (valeur == null || valeur.isBlank()) {
            throw new BesoinInvalideException(message);
        }
        return valeur.strip();
    }

    private static String facultatif(String valeur) {
        return (valeur == null || valeur.isBlank()) ? null : valeur.strip();
    }

    private static String borne(String valeur, int longueurMax, String debutMessage) {
        if (valeur != null && valeur.length() > longueurMax) {
            throw new BesoinInvalideException(debutMessage + longueurMax + " caracteres.");
        }
        return valeur;
    }
}
