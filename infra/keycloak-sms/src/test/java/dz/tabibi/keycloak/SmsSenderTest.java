package dz.tabibi.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Choix du fournisseur SMS et adaptateur BudgetSMS, contre une fausse passerelle locale. */
class SmsSenderTest {

    private HttpServer server;
    private final AtomicReference<String> reply = new AtomicReference<>("OK 123456 0.089 1 60302 41.2");
    private final AtomicReference<Map<String, String>> received = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sendsms/", ex -> {
            var params = new HashMap<String, String>();
            for (String p : ex.getRequestURI().getRawQuery().split("&")) {
                String[] kv = p.split("=", 2);
                params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            received.set(params);
            byte[] body = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Map<String, String> budgetEnv() {
        var env = new HashMap<String, String>();
        env.put("TABIBI_SMS_MODE", "budgetsms");
        env.put("TABIBI_BUDGETSMS_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/sendsms/");
        env.put("TABIBI_BUDGETSMS_USERNAME", "tabibi");
        env.put("TABIBI_BUDGETSMS_USERID", "21547");
        env.put("TABIBI_BUDGETSMS_HANDLE", "1e753da74");
        return env;
    }

    @Test
    void budgetSmsEnvoieLeNumeroSansPlusEtLeTexteArabe() {
        SmsSender sms = SmsSender.fromEnv(budgetEnv());
        assertInstanceOf(SmsSender.BudgetSms.class, sms);
        sms.send("+213550123456", "سلعة: رمزك هو 482913. لا تعطه لأي شخص.");
        Map<String, String> p = received.get();
        assertEquals("213550123456", p.get("to"));
        assertEquals("Tabibi", p.get("from"), "expéditeur par défaut");
        assertEquals("سلعة: رمزك هو 482913. لا تعطه لأي شخص.", p.get("msg"));
        assertEquals("tabibi", p.get("username"));
        assertEquals("21547", p.get("userid"));
        assertEquals("1e753da74", p.get("handle"));
    }

    @Test
    void budgetSmsRefusEstUneErreurSansLeHandle() {
        reply.set("ERR 1001");
        SmsSender sms = SmsSender.fromEnv(budgetEnv());
        var e = assertThrows(IllegalStateException.class, () -> sms.send("+213550123456", "Tabibi : votre code est 482913."));
        assertEquals("Passerelle SMS : 1001", e.getMessage());
        assertTrue(!e.getMessage().contains("1e753da74"));
    }

    @Test
    void budgetSmsReponseInattendueEstUneErreur() {
        reply.set("<html>maintenance</html>");
        SmsSender sms = SmsSender.fromEnv(budgetEnv());
        assertThrows(IllegalStateException.class, () -> sms.send("+213550123456", "code"));
    }

    @Test
    void configurationIncompleteOuInvalideEmpecheLeDemarrage() {
        var env = budgetEnv();
        env.remove("TABIBI_BUDGETSMS_HANDLE");
        assertThrows(IllegalStateException.class, () -> SmsSender.fromEnv(env));
        var badId = budgetEnv();
        badId.put("TABIBI_BUDGETSMS_USERID", "abc");
        assertThrows(IllegalStateException.class, () -> SmsSender.fromEnv(badId));
        var longSender = budgetEnv();
        longSender.put("TABIBI_SMS_SENDER", "TabibiTransport");
        assertThrows(IllegalStateException.class, () -> SmsSender.fromEnv(longSender));
        assertThrows(IllegalStateException.class, () -> SmsSender.fromEnv(Map.of("TABIBI_SMS_MODE", "budget")), "mode inconnu");
        assertThrows(IllegalStateException.class, () -> SmsSender.fromEnv(Map.of()), "http par défaut, URL obligatoire");
    }

    @Test
    void numeroMasqueDansLesJournaux() {
        assertEquals("+2135501…56", SmsSender.BudgetSms.masked("+213550123456"));
    }
}
