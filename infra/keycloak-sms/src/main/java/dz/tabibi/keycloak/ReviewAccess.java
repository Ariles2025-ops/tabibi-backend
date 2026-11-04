package dz.tabibi.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Numéros de revue des stores (Google Play, App Store) : connexion avec un code fixe, sans SMS.
 *
 * <p>Désactivé par défaut. Actif seulement si les trois variables sont présentes :
 * <ul>
 *   <li>{@code TABIBI_REVIEW_PHONES} : un ou plusieurs numéros mobiles séparés par des virgules (ex. un client, un chauffeur) ;</li>
 *   <li>{@code TABIBI_REVIEW_CODE_SHA256} : empreinte SHA-256 du code à 6 chiffres (le code lui-même n'est jamais configuré) ;</li>
 *   <li>{@code TABIBI_REVIEW_UNTIL} : dernier jour de validité (AAAA-MM-JJ, heure d'Alger), 31 jours au plus après le démarrage.</li>
 * </ul>
 * Une configuration incomplète ou invalide empêche Keycloak de démarrer : jamais d'accès de revue activé par erreur
 * ni laissé ouvert indéfiniment. Chaque utilisation est journalisée par {@link PhoneOtpAuthenticator}.
 */
final class ReviewAccess {

    static final String PHONES = "TABIBI_REVIEW_PHONES";
    static final String CODE_SHA256 = "TABIBI_REVIEW_CODE_SHA256";
    static final String UNTIL = "TABIBI_REVIEW_UNTIL";
    static final Duration MAX_VALIDITY = Duration.ofDays(31);
    /** Au-delà, plus de connexion de revue sur ce numéro jusqu'au lendemain (protège le code fixe). */
    static final int MAX_FAILURES_PER_DAY = 10;
    private static final ZoneId ALGIERS = ZoneId.of("Africa/Algiers");

    static final ReviewAccess DISABLED = new ReviewAccess(Set.of(), new byte[0], Instant.EPOCH);

    private final Set<String> phones;
    private final byte[] codeHash;
    private final Instant until;

    private ReviewAccess(Set<String> phones, byte[] codeHash, Instant until) {
        this.phones = phones;
        this.codeHash = codeHash;
        this.until = until;
    }

    static ReviewAccess fromEnv(Map<String, String> env, Instant now) {
        String phones = blankToNull(env.get(PHONES));
        String hash = blankToNull(env.get(CODE_SHA256));
        String until = blankToNull(env.get(UNTIL));
        if (phones == null && hash == null && until == null) return DISABLED;
        if (phones == null || hash == null || until == null) {
            throw new IllegalStateException("Accès de revue incomplet : " + PHONES + ", " + CODE_SHA256 + " et " + UNTIL + " vont ensemble");
        }
        Set<String> normalized = Arrays.stream(phones.split(",")).map(String::trim).map(p -> {
            String n = Phones.normalize(p);
            if (n == null) throw new IllegalStateException(PHONES + " : numéro mobile invalide « " + p + " »");
            return n;
        }).collect(Collectors.toUnmodifiableSet());
        if (!hash.matches("[0-9a-fA-F]{64}")) throw new IllegalStateException(CODE_SHA256 + " : empreinte SHA-256 attendue (64 caractères hexadécimaux)");
        Instant end = LocalDate.parse(until).plusDays(1).atStartOfDay(ALGIERS).toInstant();
        if (!end.isAfter(now)) throw new IllegalStateException(UNTIL + " est déjà passé : retirer les variables de revue");
        if (end.isAfter(now.plus(MAX_VALIDITY).plus(Duration.ofDays(1)))) {
            throw new IllegalStateException(UNTIL + " : 31 jours au plus (le temps d'une revue)");
        }
        return new ReviewAccess(normalized, HexFormat.of().parseHex(hash.toLowerCase()), end);
    }

    boolean enabled() {
        return !phones.isEmpty();
    }

    /** Numéro de revue encore valide à cet instant. */
    boolean covers(String phone, Instant now) {
        return phone != null && phones.contains(phone) && now.isBefore(until);
    }

    /** Comparaison en temps constant de l'empreinte du code saisi. */
    boolean accepts(String code) {
        if (code == null || !code.trim().matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(sha256(code.trim()), codeHash);
    }

    Set<String> phones() {
        return phones;
    }

    Instant until() {
        return until;
    }

    /** Dernier jour de validité, heure d'Alger (pour les journaux). */
    LocalDate lastDay() {
        return until.atZone(ALGIERS).toLocalDate().minusDays(1);
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
