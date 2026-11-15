package dz.tabibi.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PhonesTest {

    @Test
    void formatsAcceptes() {
        assertEquals("+213550123456", Phones.normalize("0550123456"));
        assertEquals("+213550123456", Phones.normalize("05 50 12 34 56"));
        assertEquals("+213661234567", Phones.normalize("+213 661 23 45 67"));
        assertEquals("+213771234567", Phones.normalize("00213771234567"));
        assertEquals("+213550123456", Phones.normalize("550123456"));
    }

    @Test
    void numerosRefuses() {
        assertNull(Phones.normalize("0450123456"));  // pas un mobile
        assertNull(Phones.normalize("021123456"));   // fixe
        assertNull(Phones.normalize("+33612345678")); // étranger
        assertNull(Phones.normalize("05501234"));     // trop court
        assertNull(Phones.normalize(null));
    }

    @Test
    void affichage() {
        assertEquals("05 50 12 34 56", Phones.display("+213550123456"));
    }
}
