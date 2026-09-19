package dz.tabibi.backend.ordonnances.domain;

/**
 * Version imprimable d'une ordonnance : le document PDF et le code de verification qu'il porte,
 * dont derive le nom du fichier remis a l'utilisateur ({@code ordonnance-<code>.pdf}).
 */
public record OrdonnanceImprimable(String codeVerification, byte[] contenu) {

    public String nomFichier() {
        return "ordonnance-" + codeVerification + ".pdf";
    }
}
