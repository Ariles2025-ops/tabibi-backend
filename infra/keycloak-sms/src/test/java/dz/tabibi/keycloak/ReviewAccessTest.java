package dz.tabibi.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Accès de revue des stores : désactivé par défaut, jamais à moitié configuré, jamais ouvert indéfiniment. */
class ReviewAccessTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final String HASH = HexFormat.of().formatHex(ReviewAccess.sha256("482913"));

    private static Map<String, String> env(String phones, String hash, String until) {
        var m = new java.util.HashMap<String, String>();
        if (phones != null) m.put(ReviewAccess.PHONES, phones);
        if (hash != null) m.put(ReviewAccess.CODE_SHA256, hash);
        if (until != null) m.put(ReviewAccess.UNTIL, until);
        return m;
    }

    @Test
    void desactiveParDefaut() {
        ReviewAccess r = ReviewAccess.fromEnv(Map.of(), NOW);
        assertFalse(r.enabled());
        assertFalse(r.covers("+213550000001", NOW));
        assertFalse(r.accepts("482913"));
        assertFalse(ReviewAccess.fromEnv(env(" ", "", null), NOW).enabled(), "variables vides = désactivé");
    }

    @Test
    void numerosNormalisesCodeParEmpreinteEtDateDeFin() {
        ReviewAccess r = ReviewAccess.fromEnv(env("05 50 00 00 01, +213660000002", HASH, "2026-11-10"), NOW);
        assertEquals(Set.of("+213550000001", "+213660000002"), r.phones());
        assertTrue(r.covers("+213550000001", NOW));
        assertFalse(r.covers("+213550000009", NOW), "un autre numéro passe par le SMS normal");
        assertTrue(r.accepts("482913"));
        assertTrue(r.accepts(" 482913 "));
        assertFalse(r.accepts("482914"));
        assertFalse(r.accepts("48291"));
        assertFalse(r.accepts(null));
        // Valable jusqu'à la fin du 10 novembre, heure d'Alger (UTC+1), plus après.
        assertTrue(r.covers("+213550000001", Instant.parse("2026-11-10T22:59:00Z")));
        assertFalse(r.covers("+213550000001", Instant.parse("2026-11-10T23:00:00Z")));
        assertEquals(java.time.LocalDate.parse("2026-11-10"), r.lastDay());
    }

    @Test
    void configurationIncompleteOuDangereuseRefusee() {
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0550000001", HASH, null), NOW), "sans date de fin");
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0550000001", null, "2026-11-10"), NOW), "sans empreinte");
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0550000001", "482913", "2026-11-10"), NOW),
                "le code en clair n'est pas accepté");
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0312345678", HASH, "2026-11-10"), NOW), "numéro fixe");
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0550000001", HASH, "2026-10-26"), NOW), "date passée");
        assertThrows(IllegalStateException.class, () -> ReviewAccess.fromEnv(env("0550000001", HASH, "2027-06-01"), NOW), "plus de 31 jours");
    }
}
