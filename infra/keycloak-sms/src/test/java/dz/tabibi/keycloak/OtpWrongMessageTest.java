package dz.tabibi.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class OtpWrongMessageTest {

    @Test
    void singulierPourUnSeulEssai() {
        assertEquals("auth.otp.wrongOne", PhoneOtpAuthenticator.wrongKey(1));
        assertEquals("auth.otp.wrong", PhoneOtpAuthenticator.wrongKey(2));
        assertEquals("auth.otp.wrong", PhoneOtpAuthenticator.wrongKey(4));
    }

    @Test
    void messagesPresentsEnFrancaisEtArabe() throws Exception {
        for (String lang : new String[] {"fr", "ar"}) {
            Properties p = new Properties();
            try (var in = Files.newBufferedReader(Path.of("theme/tabibi/login/messages/messages_" + lang + ".properties"))) {
                p.load(in);
            }
            assertTrue(p.getProperty("auth.otp.wrongOne", "").contains("{0}") || lang.equals("ar"), lang);
            assertTrue(!p.getProperty("auth.otp.wrongOne", "").isBlank(), lang);
        }
    }
}
