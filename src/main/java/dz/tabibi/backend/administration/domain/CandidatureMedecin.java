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
        Instant traiteeLe
) {

    /**
     * Depose une candidature en attente. Regle : nom complet, specialite, wilaya et numero d'ordre obligatoires.
     * @throws CandidatureInvalideException si une donnee obligatoire manque.
     */
    public static CandidatureMedecin deposer(UUID medecinId, DemandeCandidature demande, Instant deposeeLe) {
        if (medecinId == null) {
            throw new CandidatureInvalideException("Le medecin est obligatoire.");
        }
        if (demande == null) {
            throw new CandidatureInvalideException("La candidature est vide.");
        }
        return new CandidatureMedecin(
                UUID.randomUUID(),
                medecinId,
                obligatoire(demande.nomComplet(), "Le nom complet est obligatoire."),
                obligatoire(demande.specialiteSlug(), "La specialite est obligatoire."),
                facultatif(demande.specialiteFr()),
                obligatoire(demande.wilayaCode(), "La wilaya est obligatoire."),
                facultatif(demande.wilayaFr()),
                facultatif(demande.ville()),
                obligatoire(demande.numeroOrdre(), "Le numero d'inscription a l'ordre est obligatoire."),
                facultatif(demande.telephone()),
                StatutCandidature.EN_ATTENTE,
                null,
                deposeeLe,
                null);
    }

    /**
     * L'administrateur valide la candidature.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    public CandidatureMedecin valider(Instant quand) {
        exigerEnAttente("validee");
        return new CandidatureMedecin(id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
                ville, numeroOrdre, telephone, StatutCandidature.VALIDEE, null, deposeeLe, quand);
    }

    /**
     * L'administrateur refuse la candidature en motivant sa decision.
     * @throws CandidatureInvalideException si le motif manque.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    public CandidatureMedecin refuser(String motif, Instant quand) {
        String motifRenseigne = obligatoire(motif, "Le motif du refus est obligatoire.");
        exigerEnAttente("refusee");
        return new CandidatureMedecin(id, medecinId, nomComplet, specialiteSlug, specialiteFr, wilayaCode, wilayaFr,
                ville, numeroOrdre, telephone, StatutCandidature.REFUSEE, motifRenseigne, deposeeLe, quand);
    }

    public boolean estEnAttente() {
        return statut == StatutCandidature.EN_ATTENTE;
    }

    private void exigerEnAttente(String action) {
        if (!estEnAttente()) {
            throw new TransitionInvalideException(
                    "Seule une candidature en attente peut etre " + action + " (statut actuel : " + statut + ").");
        }
    }

    private static String obligatoire(String valeur, String message) {
        if (valeur == null || valeur.isBlank()) {
            throw new CandidatureInvalideException(message);
        }
        return valeur.strip();
    }

    private static String facultatif(String valeur) {
        return (valeur == null || valeur.isBlank()) ? null : valeur.strip();
    }
}
