package dz.tabibi.backend.administration.domain;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;

import java.time.Instant;
import java.util.UUID;

/**
 * Candidature d'un medecin a figurer dans l'annuaire, examinee par un administrateur.
 * Valeur immuable : valider ou refuser produit une copie datee.
 */
public record CandidatureMedecin(
        UUID id,
        UUID medecinId,
        String nomComplet,
        String specialiteSlug,
        String specialiteFr,
        String wilayaCode,
        String wilayaFr,
        String ville,
        String numeroOrdre,
        String telephone,
        StatutCandidature statut,
        String motifRefus,
        Instant deposeeLe,
        Instant traiteeLe,
        UUID ficheReclamee
) {

    /**
     * Depose une candidature en attente. Regle : nom complet, specialite, wilaya et numero d'ordre obligatoires.
     * @throws CandidatureInvalideException si une donnee obligatoire manque.
     */
    public static CandidatureMedecin deposer(UUID medecinId, DemandeCandidature demande, Instant deposeeLe) {
        if (medecinId == null) {
            throw new CandidatureInvalideException("Le médecin est obligatoire.");
        }
        if (demande == null) {
            throw new CandidatureInvalideException("La candidature est vide.");
        }
        return new CandidatureMedecin(
                UUID.randomUUID(),
                medecinId,
                obligatoire(demande.nomComplet(), "Le nom complet est obligatoire.", 160, "Le nom complet"),
                obligatoire(demande.specialiteSlug(), "La spécialité est obligatoire.", 60, "La spécialité"),
                facultatif(demande.specialiteFr()),
                obligatoire(demande.wilayaCode(), "La wilaya est obligatoire.", 4, "La wilaya"),
                facultatif(demande.wilayaFr()),
                facultatif(demande.ville(), 80, "La ville"),
                obligatoire(demande.numeroOrdre(), "Le numéro d'inscription à l'ordre est obligatoire.", 40,
                        "Le numéro d'inscription à l'ordre"),
                facultatif(demande.telephone(), 30, "Le téléphone"),
                StatutCandidature.EN_ATTENTE,
                null,
                deposeeLe,
                null,
                demande.ficheReclamee());
    }

    /**
     * L'administrateur valide la candidature.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    public CandidatureMedecin valider(Instant quand) {
        exigerEnAttente("validée");
        return new CandidatureMedecin(id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
                ville, numeroOrdre, telephone, StatutCandidature.VALIDEE, null, deposeeLe, quand, ficheReclamee);
    }

    /**
     * L'administrateur refuse la candidature en motivant sa decision.
     * @throws CandidatureInvalideException si le motif manque.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    public CandidatureMedecin refuser(String motif, Instant quand) {
        String motifRenseigne = obligatoire(motif, "Le motif du refus est obligatoire.");
        exigerEnAttente("refusée");
        return new CandidatureMedecin(id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
                ville, numeroOrdre, telephone, StatutCandidature.REFUSEE, motifRenseigne, deposeeLe, quand, ficheReclamee);
    }

    /** Vrai si le medecin reclame une fiche importee dans l'annuaire au lieu d'en creer une. */
    public boolean estUneReclamation() {
        return ficheReclamee != null;
    }

    public boolean estEnAttente() {
        return statut == StatutCandidature.EN_ATTENTE;
    }

    private void exigerEnAttente(String action) {
        if (!estEnAttente()) {
            throw new TransitionInvalideException(
                    "Seule une candidature en attente peut être " + action + " (statut actuel : " + statut.libelle() + ").");
        }
    }

    private static String obligatoire(String valeur, String message) {
        if (valeur == null || valeur.isBlank()) {
            throw new CandidatureInvalideException(message);
        }
        return valeur.strip();
    }

    private static String obligatoire(String valeur, String message, int max, String champ) {
        return limiter(obligatoire(valeur, message), max, champ);
    }

    private static String facultatif(String valeur, int max, String champ) {
        return limiter(facultatif(valeur), max, champ);
    }

    /** Evite une violation de contrainte en base (colonnes varchar) : message precis plutot qu'un faux conflit. */
    private static String limiter(String valeur, int max, String champ) {
        if (valeur != null && valeur.length() > max) {
            throw new CandidatureInvalideException(champ + " est trop long (" + max + " caractères au plus).");
        }
        return valeur;
    }

    private static String facultatif(String valeur) {
        return (valeur == null || valeur.isBlank()) ? null : valeur.strip();
    }
}
