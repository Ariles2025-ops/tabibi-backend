package dz.tabibi.backend.teleconsultation.domain;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Attribue a chaque teleconsultation un nom de salle non devinable :
 * {@code tabibi-} suivi de 32 caracteres hexadecimaux (128 bits tires par SecureRandom).
 * Le nom seul donne acces a la salle chez le fournisseur video : il ne circule que par l'API,
 * apres controle du proprietaire et du consentement du patient.
 */
public final class GenerateurSalle {

    public static final String PREFIXE = "tabibi-";

    /** 16 octets, soit 32 caracteres hexadecimaux. */
    private static final int OCTETS = 16;

    private final SecureRandom alea = new SecureRandom();

    public String generer() {
        byte[] octets = new byte[OCTETS];
        alea.nextBytes(octets);
        return PREFIXE + HexFormat.of().formatHex(octets);
    }
}
