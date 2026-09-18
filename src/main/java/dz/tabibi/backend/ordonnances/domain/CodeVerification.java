package dz.tabibi.backend.ordonnances.domain;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Code court imprime sur l'ordonnance et saisi par le pharmacien pour la verifier.
 * Huit caracteres tires au hasard (SecureRandom) parmi les lettres majuscules et
 * les chiffres, sans O/0 ni I/1 pour eviter les confusions a la lecture.
 */
public final class CodeVerification {

    public static final int LONGUEUR = 8;

    /** Lettres et chiffres autorises (ni O, ni 0, ni I, ni 1). */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom ALEA = new SecureRandom();

    private CodeVerification() { }

    public static String generer() {
        StringBuilder code = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            code.append(ALPHABET.charAt(ALEA.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Forme canonique d'un code saisi : espaces retires, lettres en majuscules. */
    public static String normaliser(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }
}
