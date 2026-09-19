package dz.tabibi.backend.audit;

import dz.tabibi.backend.audit.adapter.FiltreAudit;
import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le filtre d'audit journalise chaque requete de l'API (sujet, methode, chemin, statut, IP tronquee,
 * heure, duree), jamais le corps ni les parametres, ignore la supervision, et n'echoue jamais la
 * requete quand le journal est indisponible.
 */
class FiltreAuditTest {

    private static final Instant MAINTENANT = Instant.parse("2026-03-01T09:00:00Z");
    private static final UUID PATIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Faux port : garde les entrees, ou echoue sur demande. */
    static class FauxAuditRepository implements AuditRepository {
        final List<EntreeAudit> entrees = new ArrayList<>();
        boolean enPanne;

        @Override
        public EntreeAudit enregistrer(EntreeAudit entree) {
            if (enPanne) {
                throw new IllegalStateException("base indisponible");
            }
            entrees.add(entree);
            return entree;
        }

        @Override
        public List<EntreeAudit> recents(int limite) {
            return List.copyOf(entrees);
        }

        @Override
        public List<EntreeAudit> parSujet(UUID sujet, int limite) {
            return entrees.stream().filter(e -> sujet.equals(e.sujet())).toList();
        }
    }

    /** Requete dont le corps et les parametres ne doivent jamais etre lus par le filtre. */
    static class RequeteAuCorpsInterdit extends HttpServletRequestWrapper {
        RequeteAuCorpsInterdit(HttpServletRequest requete) {
            super(requete);
        }

        @Override public ServletInputStream getInputStream() { throw new AssertionError("corps lu"); }
        @Override public BufferedReader getReader() { throw new AssertionError("corps lu"); }
        @Override public String getParameter(String nom) { throw new AssertionError("parametres lus"); }
        @Override public Map<String, String[]> getParameterMap() { throw new AssertionError("parametres lus"); }
        @Override public String[] getParameterValues(String nom) { throw new AssertionError("parametres lus"); }
        @Override public String getQueryString() { throw new AssertionError("parametres lus"); }
    }

    private final FauxAuditRepository repository = new FauxAuditRepository();
    private final FiltreAudit filtre = new FiltreAudit(new AuditService(repository), Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @AfterEach
    void nettoyerLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    private static void connecte(UUID sujet, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(sujet.toString(), null, "ROLE_" + role));
    }

    private static MockHttpServletRequest requete(String methode, String chemin) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setRemoteAddr("192.168.1.37");
        return requete;
    }

    private static FilterChain chaineRepondant(int statut) {
        return (req, res) -> ((HttpServletResponse) res).setStatus(statut);
    }

    @Test
    void enregistre_une_requete_de_l_api_avec_le_sujet_du_jeton_et_l_ip_tronquee() throws Exception {
        connecte(PATIENT, "PATIENT");
        MockHttpServletRequest requete = requete("POST", "/api/rendezvous");
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        filtre.doFilter(requete, reponse, chaineRepondant(201));

        assertThat(reponse.getStatus()).isEqualTo(201);
        assertThat(repository.entrees).hasSize(1);
        EntreeAudit entree = repository.entrees.get(0);
        assertThat(entree.sujet()).isEqualTo(PATIENT);
        assertThat(entree.methode()).isEqualTo("POST");
        assertThat(entree.chemin()).isEqualTo("/api/rendezvous");
        assertThat(entree.statut()).isEqualTo(201);
        assertThat(entree.adresseIp()).isEqualTo("192.168.1.0");
        assertThat(entree.horodatage()).isEqualTo(MAINTENANT);
        assertThat(entree.dureeMs()).isGreaterThanOrEqualTo(0L);
        assertThat(entree.id()).isNotNull();
    }

    @Test
    void sans_jeton_le_sujet_est_nul() throws Exception {
        filtre.doFilter(requete("GET", "/api/medecins"), new MockHttpServletResponse(), chaineRepondant(200));

        assertThat(repository.entrees).hasSize(1);
        assertThat(repository.entrees.get(0).sujet()).isNull();
        assertThat(repository.entrees.get(0).anonyme()).isTrue();
        assertThat(repository.entrees.get(0).statut()).isEqualTo(200);
    }

    @Test
    void un_utilisateur_anonyme_de_spring_security_est_journalise_sans_sujet() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "cle", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        filtre.doFilter(requete("GET", "/api/medecins"), new MockHttpServletResponse(), chaineRepondant(200));

        assertThat(repository.entrees.get(0).sujet()).isNull();
    }

    @Test
    void un_sujet_qui_n_est_pas_un_uuid_est_journalise_sans_sujet() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("service-technique", null, "ROLE_ADMIN"));

        filtre.doFilter(requete("GET", "/api/admin/audit"), new MockHttpServletResponse(), chaineRepondant(200));

        assertThat(repository.entrees).hasSize(1);
        assertThat(repository.entrees.get(0).sujet()).isNull();
    }

    @Test
    void ne_lit_jamais_le_corps_ni_les_parametres_et_n_enregistre_pas_la_chaine_de_requete() throws Exception {
        connecte(PATIENT, "PATIENT");
        MockHttpServletRequest brute = requete("POST", "/api/dawini/besoins");
        brute.setQueryString("medicament=insuline");
        brute.addParameter("medicament", "insuline");
        brute.setContent("{\"medicament\":\"insuline\"}".getBytes(StandardCharsets.UTF_8));
        RequeteAuCorpsInterdit requete = new RequeteAuCorpsInterdit(brute);
        List<Object> requeteVueParLaChaine = new ArrayList<>();

        filtre.doFilter(requete, new MockHttpServletResponse(), (req, res) -> requeteVueParLaChaine.add(req));

        assertThat(requeteVueParLaChaine).containsExactly(requete); // la requete passe telle quelle, sans relecture
        EntreeAudit entree = repository.entrees.get(0);
        assertThat(entree.chemin()).isEqualTo("/api/dawini/besoins");
        assertThat(entree.chemin()).doesNotContain("insuline");
        assertThat(entree.toString()).doesNotContain("insuline");
    }

    @Test
    void ignore_la_supervision_et_tout_ce_qui_n_est_pas_l_api() throws Exception {
        for (String chemin : List.of("/actuator/health", "/actuator/info", "/swagger-ui.html", "/v3/api-docs", "/error", "/")) {
            List<Object> chaineAppelee = new ArrayList<>();
            filtre.doFilter(requete("GET", chemin), new MockHttpServletResponse(), (req, res) -> chaineAppelee.add(req));
            assertThat(chaineAppelee).hasSize(1); // la requete continue, sans trace
        }

        assertThat(repository.entrees).isEmpty();
    }

    @Test
    void un_journal_indisponible_ne_fait_pas_echouer_la_requete() throws Exception {
        repository.enPanne = true;
        List<Object> chaineAppelee = new ArrayList<>();
        MockHttpServletResponse reponse = new MockHttpServletResponse();

        filtre.doFilter(requete("GET", "/api/moi"), reponse, (req, res) -> {
            chaineAppelee.add(req);
            ((HttpServletResponse) res).setStatus(200);
        });

        assertThat(chaineAppelee).hasSize(1);
        assertThat(reponse.getStatus()).isEqualTo(200);
        assertThat(repository.entrees).isEmpty();
    }

    @Test
    void un_refus_de_pre_authorize_est_journalise_403_et_remonte() {
        connecte(PATIENT, "PATIENT");
        ServletException refus = new ServletException("Request processing failed",
                new AccessDeniedException("Access Denied"));

        assertThatThrownBy(() -> filtre.doFilter(requete("GET", "/api/medecin/rendezvous"), new MockHttpServletResponse(),
                (req, res) -> { throw refus; }))
                .isSameAs(refus);

        assertThat(repository.entrees).hasSize(1);
        assertThat(repository.entrees.get(0).statut()).isEqualTo(403);
        assertThat(repository.entrees.get(0).sujet()).isEqualTo(PATIENT);
    }

    @Test
    void une_authentification_insuffisante_est_journalisee_401_et_une_autre_erreur_500() {
        RuntimeException panne = new IllegalStateException("panne");

        assertThatThrownBy(() -> filtre.doFilter(requete("GET", "/api/moi"), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException(new InsufficientAuthenticationException("jeton absent")); }))
                .isInstanceOf(ServletException.class);
        assertThatThrownBy(() -> filtre.doFilter(requete("GET", "/api/moi"), new MockHttpServletResponse(),
                (req, res) -> { throw panne; }))
                .isSameAs(panne);

        assertThat(repository.entrees).hasSize(2);
        assertThat(repository.entrees.get(0).statut()).isEqualTo(401);
        assertThat(repository.entrees.get(1).statut()).isEqualTo(500);
    }

    @Test
    void un_chemin_trop_long_est_abrege() throws Exception {
        String chemin = "/api/medecins/" + "x".repeat(1000);

        filtre.doFilter(requete("GET", chemin), new MockHttpServletResponse(), chaineRepondant(404));

        assertThat(repository.entrees.get(0).chemin()).hasSize(512);
        assertThat(repository.entrees.get(0).chemin()).startsWith("/api/medecins/x");
    }

    @Test
    void pose_un_identifiant_de_requete_dans_le_mdc_et_le_renvoie_en_en_tete() throws Exception {
        MockHttpServletRequest requete = requete("GET", "/api/moi");
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        List<String> vusDansLeMdc = new ArrayList<>();

        filtre.doFilter(requete, reponse, (req, res) -> vusDansLeMdc.add(MDC.get(FiltreAudit.CLE_MDC)));

        assertThat(vusDansLeMdc).hasSize(1);
        assertThat(vusDansLeMdc.get(0)).isNotBlank();
        assertThat(reponse.getHeader(FiltreAudit.EN_TETE_REQUETE)).isEqualTo(vusDansLeMdc.get(0));
        assertThat(MDC.get(FiltreAudit.CLE_MDC)).isNull(); // efface a la sortie
    }

    @Test
    void reprend_l_identifiant_de_requete_du_proxy_s_il_est_raisonnable() throws Exception {
        MockHttpServletRequest requete = requete("GET", "/api/moi");
        requete.addHeader(FiltreAudit.EN_TETE_REQUETE, "abc-123");

        assertThat(FiltreAudit.identifiantDe(requete)).isEqualTo("abc-123");
    }

    @Test
    void ignore_un_identifiant_de_requete_absent_vide_trop_long_ou_avec_des_espaces() {
        assertThat(FiltreAudit.identifiantDe(requete("GET", "/api/moi"))).isNotBlank();
        for (String suspect : List.of(" ", "a".repeat(65), "abc def", "abc\ndef")) {
            MockHttpServletRequest requete = requete("GET", "/api/moi");
            requete.addHeader(FiltreAudit.EN_TETE_REQUETE, suspect);
            assertThat(FiltreAudit.identifiantDe(requete)).isNotEqualTo(suspect);
        }
    }

    @Test
    void efface_l_identifiant_du_mdc_meme_si_la_requete_echoue() {
        FilterChain quiEchoue = (req, res) -> {
            throw new IllegalStateException("panne");
        };

        assertThatThrownBy(() -> filtre.doFilter(requete("GET", "/api/moi"), new MockHttpServletResponse(), quiEchoue))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(FiltreAudit.CLE_MDC)).isNull();
    }
}
