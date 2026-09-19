package dz.tabibi.backend.ordonnances.domain;

/**
 * Port de sortie : produit la version imprimable (PDF) d'une ordonnance. Le domaine ne connait
 * aucune bibliotheque de PDF ; l'adaptateur (OpenPDF + ZXing) le realise. Les noms sont fournis
 * par le cas d'usage : l'ordonnance elle-meme ne porte que des identifiants.
 */
public interface GenerateurPdfOrdonnance {

    /**
     * @param ordonnance      l'ordonnance a imprimer (lignes, date d'emission, code de verification)
     * @param nomMedecin      nom du medecin auteur tel qu'il figure dans l'annuaire
     * @param nomPatient      nom du patient tel qu'il figure sur son profil
     * @param urlVerification adresse publique de verification du code, encodee dans le QR code
     * @return le document PDF complet
     */
    byte[] generer(Ordonnance ordonnance, String nomMedecin, String nomPatient, String urlVerification);
}
