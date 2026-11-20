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
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(FUSEAU);

    private static final DateTimeFormatter DATE_HEURE_AR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'الساعة' HH:mm").withZone(FUSEAU);

    private FormatDate() { }

    /** Par exemple {@code 07/12/2026 a 10:00} pour 2026-12-07T09:00:00Z. */
    public static String lisible(Instant instant) {
        return DATE_HEURE.format(instant);
    }

    /** Comme {@link #lisible(Instant)}, mais sans mot français en arabe : {@code 07/12/2026 الساعة 10:00}. */
    public static String lisible(Instant instant, String langue) {
        return "ar".equals(langue) ? DATE_HEURE_AR.format(instant) : DATE_HEURE.format(instant);
    }
}
