package dz.tabibi.backend.commun.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Presentation lisible d'une date pour les messages adresses aux utilisateurs
 * (notifications), a l'heure d'Algerie.
 */
public final class FormatDate {

    /** Fuseau de reference de la plateforme (UTC+1, sans heure d'ete). */
    public static final ZoneId FUSEAU = ZoneId.of("Africa/Algiers");

    private static final DateTimeFormatter DATE_HEURE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm").withZone(FUSEAU);

    private FormatDate() { }

    /** Par exemple {@code 07/12/2026 a 10:00} pour 2026-12-07T09:00:00Z. */
    public static String lisible(Instant instant) {
        return DATE_HEURE.format(instant);
    }
}
