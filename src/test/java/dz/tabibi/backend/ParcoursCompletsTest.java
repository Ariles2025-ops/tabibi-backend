package dz.tabibi.backend;

import com.jayway.jsonpath.JsonPath;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Parcours complets, de bout en bout, par l'API reelle (contexte Spring complet, depots en memoire) :
 * chaque test joue une fonctionnalite comme le site web la jouerait, en enchainant les roles
 * (patient, medecin, secretaire, pharmacie, administrateur).
 */
@SpringBootTest(properties = "tabibi.rappels.actifs=false")
@AutoConfigureMockMvc
class ParcoursCompletsTest {

    @Autowired MockMvc mvc;
    @Autowired RendezVousRepository rendezVous;
    @MockBean JwtDecoder jwtDecoder;

    // ---------- outils ----------

    private static RequestPostProcessor compte(UUID id, String... roles) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("email", id + "@test.dz"))
                .authorities(Arrays.stream(roles).map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private MvcResult appel(MockHttpServletRequestBuilder req, RequestPostProcessor qui, String corps) throws Exception {
        if (qui != null) req = req.with(qui);
        if (corps != null) req = req.contentType(MediaType.APPLICATION_JSON).content(corps);
        return mvc.perform(req).andReturn();
    }

    private String ok(MockHttpServletRequestBuilder req, RequestPostProcessor qui, String corps) throws Exception {
        MvcResult r = appel(req, qui, corps);
        int s = r.getResponse().getStatus();
        assertThat(s).as(req.toString() + " -> " + r.getResponse().getContentAsString()).isBetween(200, 299);
        return r.getResponse().getContentAsString();
    }

    private int statut(MockHttpServletRequestBuilder req, RequestPostProcessor qui, String corps) throws Exception {
        return appel(req, qui, corps).getResponse().getStatus();
    }

    private static <T> T lire(String json, String chemin) {
        return JsonPath.read(json, chemin);
    }

    private static String demain(int heure) {
        return Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(heure, ChronoUnit.HOURS).toString();
    }

    private String ouvrirCreneau(UUID medecin, String debut) throws Exception {
        String c = ok(post("/api/medecin/creneaux"), compte(medecin, "MEDECIN"),
                "{\"debut\":\"" + debut + "\",\"dureeMinutes\":30}");
        return lire(c, "$.id");
    }

    private String reserver(UUID patient, String creneauId) throws Exception {
        return lire(ok(post("/api/creneaux/" + creneauId + "/reserver"), compte(patient, "PATIENT"), null), "$.id");
    }

    private int nbNotifications(UUID qui, String role) throws Exception {
        List<?> l = lire(ok(get("/api/notifications/mes"), compte(qui, role), null), "$");
        return l.size();
    }

    /** Rendez-vous confirme deja passe (pour les parcours qui suivent une consultation). */
    private RendezVous rdvPasse(UUID patient, UUID medecin) {
        return rendezVous.enregistrer(new RendezVous(UUID.randomUUID(), patient, medecin,
                Instant.now().minus(2, ChronoUnit.HOURS), StatutRdv.CONFIRME));
    }

    // ---------- parcours ----------

    @Test
    void rendez_vous_ouverture_reservation_agenda_annulation() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        String creneau = ouvrirCreneau(medecin, demain(9));

        String libres = ok(get("/api/medecins/" + medecin + "/creneaux"), null, null);
        assertThat((List<?>) lire(libres, "$[?(@.id=='" + creneau + "')]")).hasSize(1);

        String rdv = reserver(patient, creneau);
        assertThat((List<?>) lire(ok(get("/api/rendezvous/mes"), compte(patient, "PATIENT"), null), "$[?(@.id=='" + rdv + "')]")).hasSize(1);
        assertThat((List<?>) lire(ok(get("/api/medecin/rendezvous"), compte(medecin, "MEDECIN"), null), "$[?(@.id=='" + rdv + "')]")).hasSize(1);
        assertThat((List<?>) lire(ok(get("/api/medecins/" + medecin + "/creneaux"), null, null), "$[?(@.id=='" + creneau + "')]")).isEmpty();
        assertThat(nbNotifications(patient, "PATIENT")).isGreaterThanOrEqualTo(1);
        assertThat(nbNotifications(medecin, "MEDECIN")).isGreaterThanOrEqualTo(1);

        // un second patient ne peut pas prendre le meme creneau
        assertThat(statut(post("/api/creneaux/" + creneau + "/reserver"), compte(UUID.randomUUID(), "PATIENT"), null)).isEqualTo(409);

        ok(post("/api/rendezvous/" + rdv + "/annuler"), compte(patient, "PATIENT"), null);
        assertThat((List<?>) lire(ok(get("/api/medecins/" + medecin + "/creneaux"), null, null), "$[?(@.id=='" + creneau + "')]")).hasSize(1);

        // un creneau dans le passe ne s'ouvre pas
        assertThat(statut(post("/api/medecin/creneaux"), compte(medecin, "MEDECIN"),
                "{\"debut\":\"" + Instant.now().minus(1, ChronoUnit.DAYS) + "\",\"dureeMinutes\":30}")).isBetween(400, 499);
    }

    @Test
    void un_rendez_vous_futur_ne_peut_pas_etre_marque_honore() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        String rdv = reserver(patient, ouvrirCreneau(medecin, demain(10)));

        assertThat(statut(post("/api/rendezvous/" + rdv + "/honorer"), compte(medecin, "MEDECIN"), null)).isEqualTo(409);
    }

    @Test
    void liste_d_attente_alerte_puis_desinscription_apres_reservation() throws Exception {
        UUID medecin = UUID.randomUUID(), p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
        String creneau = ouvrirCreneau(medecin, demain(11));
        String rdv = reserver(p1, creneau);

        ok(post("/api/medecins/" + medecin + "/liste-attente"), compte(p2, "PATIENT"), null);
        assertThat((List<?>) lire(ok(get("/api/medecin/liste-attente"), compte(medecin, "MEDECIN"), null), "$")).hasSize(1);
        int avant = nbNotifications(p2, "PATIENT");

        ok(post("/api/rendezvous/" + rdv + "/annuler"), compte(p1, "PATIENT"), null);
        assertThat(nbNotifications(p2, "PATIENT")).isGreaterThan(avant);

        // p2 reserve le creneau libere : il n'a plus besoin d'etre sur la liste d'attente de ce medecin
        reserver(p2, creneau);
        assertThat((List<?>) lire(ok(get("/api/liste-attente/mes"), compte(p2, "PATIENT"), null), "$")).isEmpty();
    }

    @Test
    void ordonnance_redaction_lecture_verification_et_notification() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        RendezVous rdv = rdvPasse(patient, medecin);
        int avant = nbNotifications(patient, "PATIENT");

        String o = ok(post("/api/ordonnances"), compte(medecin, "MEDECIN"),
                "{\"patientId\":\"" + patient + "\",\"rendezVousId\":\"" + rdv.id() + "\",\"lignes\":[{\"medicament\":\"Paracetamol 1 g\",\"posologie\":\"3 fois par jour\",\"duree\":\"5 jours\"}]}");
        String code = lire(o, "$.codeVerification");

        assertThat((List<?>) lire(ok(get("/api/ordonnances/mes"), compte(patient, "PATIENT"), null), "$")).hasSize(1);
        assertThat((List<?>) lire(ok(get("/api/medecin/ordonnances"), compte(medecin, "MEDECIN"), null), "$")).hasSize(1);
        ok(get("/api/ordonnances/verifier/" + code), null, null);
        assertThat(nbNotifications(patient, "PATIENT")).as("le patient est prevenu de sa nouvelle ordonnance").isGreaterThan(avant);

        // un autre medecin ne peut pas prescrire a ce patient
        assertThat(statut(post("/api/ordonnances"), compte(UUID.randomUUID(), "MEDECIN"),
                "{\"patientId\":\"" + patient + "\",\"lignes\":[{\"medicament\":\"X\"}]}")).isEqualTo(403);
    }

    @Test
    void teleconsultation_planification_consentement_session() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        String rdv = reserver(patient, ouvrirCreneau(medecin, demain(14)));

        String t = ok(post("/api/medecin/teleconsultations"), compte(medecin, "MEDECIN"), "{\"rendezVousId\":\"" + rdv + "\"}");
        String id = lire(t, "$.id");
        String vuePatient = ok(get("/api/teleconsultations/" + id), compte(patient, "PATIENT"), null);
        assertThat((Object) lire(vuePatient, "$.lienSalle")).isNull();

        int avantMedecin = nbNotifications(medecin, "MEDECIN");
        ok(post("/api/teleconsultations/" + id + "/consentir"), compte(patient, "PATIENT"), null);
        assertThat((String) lire(ok(get("/api/teleconsultations/" + id), compte(patient, "PATIENT"), null), "$.lienSalle")).isNotBlank();
        assertThat(nbNotifications(medecin, "MEDECIN")).as("le medecin sait que le patient a consenti").isGreaterThan(avantMedecin);

        ok(post("/api/teleconsultations/" + id + "/demarrer"), compte(medecin, "MEDECIN"), null);
        ok(post("/api/teleconsultations/" + id + "/terminer"), compte(medecin, "MEDECIN"), null);
        assertThat((String) lire(ok(get("/api/teleconsultations/mes"), compte(patient, "PATIENT"), null), "$[0].statut")).isEqualTo("TERMINEE");
    }

    @Test
    void messagerie_entre_patient_et_medecin() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        reserver(patient, ouvrirCreneau(medecin, demain(15)));

        String conv = lire(ok(post("/api/conversations"), compte(patient, "PATIENT"), "{\"medecinId\":\"" + medecin + "\"}"), "$.id");
        ok(post("/api/conversations/" + conv + "/messages"), compte(patient, "PATIENT"), "{\"contenu\":\"Bonjour docteur\"}");

        assertThat((List<?>) lire(ok(get("/api/conversations"), compte(medecin, "MEDECIN"), null), "$")).hasSize(1);
        assertThat((List<?>) lire(ok(get("/api/conversations/" + conv + "/messages"), compte(medecin, "MEDECIN"), null), "$")).hasSize(1);
        ok(post("/api/conversations/" + conv + "/messages"), compte(medecin, "MEDECIN"), "{\"contenu\":\"Bonjour, je vous ecoute\"}");
        assertThat((List<?>) lire(ok(get("/api/conversations/" + conv + "/messages"), compte(patient, "PATIENT"), null), "$")).hasSize(2);

        // sans rendez-vous commun, pas de conversation ; un tiers ne lit pas le fil
        assertThat(statut(post("/api/conversations"), compte(UUID.randomUUID(), "PATIENT"), "{\"medecinId\":\"" + medecin + "\"}")).isEqualTo(403);
        assertThat(statut(get("/api/conversations/" + conv + "/messages"), compte(UUID.randomUUID(), "PATIENT"), null)).isEqualTo(403);
    }

    @Test
    void avis_depot_affichage_signalement_moderation() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID(), admin = UUID.randomUUID();
        RendezVous rdv = rdvPasse(patient, medecin);
        ok(post("/api/rendezvous/" + rdv.id() + "/honorer"), compte(medecin, "MEDECIN"), null);

        String avis = lire(ok(post("/api/avis"), compte(patient, "PATIENT"),
                "{\"rendezVousId\":\"" + rdv.id() + "\",\"note\":2,\"commentaire\":\"Attente trop longue\"}"), "$.id");
        assertThat((Integer) lire(ok(get("/api/medecins/" + medecin + "/avis"), null, null), "$.nombre")).isEqualTo(1);

        ok(post("/api/avis/" + avis + "/signaler"), compte(medecin, "MEDECIN"), null);
        assertThat((Integer) lire(ok(get("/api/medecins/" + medecin + "/avis"), null, null), "$.nombre")).isEqualTo(1);

        ok(post("/api/admin/avis/" + avis + "/masquer"), compte(admin, "ADMIN"), null);
        assertThat((Integer) lire(ok(get("/api/medecins/" + medecin + "/avis"), null, null), "$.nombre")).isEqualTo(0);

        // un seul avis par rendez-vous
        assertThat(statut(post("/api/avis"), compte(patient, "PATIENT"),
                "{\"rendezVousId\":\"" + rdv.id() + "\",\"note\":5}")).isEqualTo(409);
    }

    @Test
    void dawini_demande_reponse_pharmacie_et_notification() throws Exception {
        UUID patient = UUID.randomUUID(), pharmacie = UUID.randomUUID();
        String besoin = lire(ok(post("/api/dawini/besoins"), compte(patient, "PATIENT"),
                "{\"medicament\":\"Amoxicilline 1 g\",\"wilayaCode\":\"16\",\"commune\":\"Bab Ezzouar\"}"), "$.id");

        assertThat((List<?>) lire(ok(get("/api/dawini/besoins?wilaya=16"), compte(pharmacie, "PHARMACIE"), null), "$[?(@.id=='" + besoin + "')]")).hasSize(1);
        int avant = nbNotifications(patient, "PATIENT");
        ok(post("/api/dawini/besoins/" + besoin + "/reponses"), compte(pharmacie, "PHARMACIE"),
                "{\"nomPharmacie\":\"Pharmacie du Centre\",\"disponible\":true,\"prixDa\":450}");
        assertThat((List<?>) lire(ok(get("/api/dawini/besoins/" + besoin + "/reponses"), compte(patient, "PATIENT"), null), "$")).hasSize(1);
        assertThat(nbNotifications(patient, "PATIENT")).isGreaterThan(avant);

        ok(post("/api/dawini/besoins/" + besoin + "/cloturer"), compte(patient, "PATIENT"), null);
        assertThat((List<?>) lire(ok(get("/api/dawini/besoins?wilaya=16"), compte(pharmacie, "PHARMACIE"), null), "$[?(@.id=='" + besoin + "')]")).isEmpty();
    }

    @Test
    void secretaire_gere_l_agenda_du_medecin() throws Exception {
        UUID medecin = UUID.randomUUID(), secretaire = UUID.randomUUID(), patient = UUID.randomUUID();
        ok(post("/api/medecin/secretaires"), compte(medecin, "MEDECIN"), "{\"secretaireId\":\"" + secretaire + "\"}");
        assertThat((List<?>) lire(ok(get("/api/secretaire/medecins"), compte(secretaire, "SECRETAIRE"), null), "$")).hasSize(1);

        String creneau = lire(ok(post("/api/secretaire/medecins/" + medecin + "/creneaux"), compte(secretaire, "SECRETAIRE"),
                "{\"debut\":\"" + demain(16) + "\",\"dureeMinutes\":30}"), "$.id");
        String rdv = reserver(patient, creneau);
        assertThat((List<?>) lire(ok(get("/api/secretaire/medecins/" + medecin + "/rendezvous"), compte(secretaire, "SECRETAIRE"), null), "$")).hasSize(1);
        ok(post("/api/secretaire/rendezvous/" + rdv + "/annuler"), compte(secretaire, "SECRETAIRE"), null);

        // une secretaire non rattachee n'a pas acces
        assertThat(statut(get("/api/secretaire/medecins/" + medecin + "/rendezvous"), compte(UUID.randomUUID(), "SECRETAIRE"), null)).isEqualTo(403);
    }

    @Test
    void candidature_puis_validation_par_l_administrateur() throws Exception {
        UUID praticien = UUID.randomUUID(), admin = UUID.randomUUID();
        String c = lire(ok(post("/api/medecin/candidature"), compte(praticien, "PATIENT"),
                "{\"nomComplet\":\"Dr Karim Test\",\"specialiteSlug\":\"medecin-generaliste\",\"specialiteFr\":\"Médecin généraliste\","
                        + "\"wilayaCode\":\"16\",\"wilayaFr\":\"Alger\",\"ville\":\"Alger\",\"numeroOrdre\":\"12345\",\"telephone\":\"0550123456\"}"), "$.id");
        assertThat((List<?>) lire(ok(get("/api/admin/candidatures"), compte(admin, "ADMIN"), null), "$[?(@.id=='" + c + "')]")).hasSize(1);

        ok(post("/api/admin/candidatures/" + c + "/valider"), compte(admin, "ADMIN"), null);
        assertThat((String) lire(ok(get("/api/medecins/" + praticien), null, null), "$.nomComplet")).isEqualTo("Dr Karim Test");
        assertThat(nbNotifications(praticien, "PATIENT")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void profil_enregistrement_et_relecture() throws Exception {
        UUID patient = UUID.randomUUID();
        ok(put("/api/moi/profil"), compte(patient, "PATIENT"),
                "{\"nomComplet\":\"Amina Belkacem\",\"telephone\":\"0550123456\",\"dateNaissance\":\"1990-05-12\",\"wilayaCode\":\"16\",\"langue\":\"fr\"}");
        assertThat((String) lire(ok(get("/api/moi/profil"), compte(patient, "PATIENT"), null), "$.nomComplet")).isEqualTo("Amina Belkacem");
    }

    @Test
    void agenda_des_creneaux_du_medecin_liste_suppression_chevauchement() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        String libre = ouvrirCreneau(medecin, demain(8));
        String pris = ouvrirCreneau(medecin, demain(12));
        reserver(patient, pris);

        assertThat((List<?>) lire(ok(get("/api/medecin/creneaux"), compte(medecin, "MEDECIN"), null), "$")).hasSize(2);
        // un creneau qui chevauche un creneau ouvert est refuse
        assertThat(statut(post("/api/medecin/creneaux"), compte(medecin, "MEDECIN"),
                "{\"debut\":\"" + Instant.parse(demain(8)).plus(10, ChronoUnit.MINUTES) + "\",\"dureeMinutes\":30}")).isEqualTo(400);
        // un creneau reserve ne se supprime pas, un libre oui ; un autre medecin ne touche pas a l'agenda
        assertThat(statut(delete("/api/medecin/creneaux/" + pris), compte(medecin, "MEDECIN"), null)).isEqualTo(409);
        assertThat(statut(delete("/api/medecin/creneaux/" + libre), compte(UUID.randomUUID(), "MEDECIN"), null)).isEqualTo(403);
        assertThat(statut(delete("/api/medecin/creneaux/" + libre), compte(medecin, "MEDECIN"), null)).isEqualTo(204);
        assertThat((List<?>) lire(ok(get("/api/medecin/creneaux"), compte(medecin, "MEDECIN"), null), "$")).hasSize(1);
    }

    @Test
    void un_patient_ne_bloque_pas_tout_l_agenda() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        reserver(patient, ouvrirCreneau(medecin, demain(9)));
        String second = ouvrirCreneau(medecin, demain(10));
        assertThat(statut(post("/api/creneaux/" + second + "/reserver"), compte(patient, "PATIENT"), null)).isEqualTo(409);
    }

    @Test
    void le_cabinet_voit_ses_patients_par_leur_nom() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID(), secretaire = UUID.randomUUID();
        ok(put("/api/moi/profil"), compte(patient, "PATIENT"),
                "{\"nomComplet\":\"Yacine Haddad\",\"telephone\":\"0661000000\",\"wilayaCode\":\"16\",\"langue\":\"fr\"}");
        reserver(patient, ouvrirCreneau(medecin, demain(13)));

        String patients = ok(get("/api/medecin/patients"), compte(medecin, "MEDECIN"), null);
        assertThat((String) lire(patients, "$[0].nomComplet")).isEqualTo("Yacine Haddad");
        // un autre medecin ne voit pas ce patient
        assertThat((List<?>) lire(ok(get("/api/medecin/patients"), compte(UUID.randomUUID(), "MEDECIN"), null), "$")).isEmpty();
        // la secretaire rattachee le voit, pas une autre
        ok(post("/api/medecin/secretaires"), compte(medecin, "MEDECIN"), "{\"secretaireId\":\"" + secretaire + "\"}");
        assertThat((List<?>) lire(ok(get("/api/secretaire/medecins/" + medecin + "/patients"), compte(secretaire, "SECRETAIRE"), null), "$")).hasSize(1);
        assertThat(statut(get("/api/secretaire/medecins/" + medecin + "/patients"), compte(UUID.randomUUID(), "SECRETAIRE"), null)).isEqualTo(403);
        assertThat((List<?>) lire(ok(get("/api/secretaire/medecins/" + medecin + "/creneaux"), compte(secretaire, "SECRETAIRE"), null), "$")).hasSize(1);
    }

    @Test
    void terminer_la_teleconsultation_rend_le_rendez_vous_honore() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        RendezVous rdv = rendezVous.enregistrer(new RendezVous(UUID.randomUUID(), patient, medecin,
                Instant.now().plus(2, ChronoUnit.HOURS), StatutRdv.CONFIRME));
        String t = lire(ok(post("/api/medecin/teleconsultations"), compte(medecin, "MEDECIN"), "{\"rendezVousId\":\"" + rdv.id() + "\"}"), "$.id");
        ok(post("/api/teleconsultations/" + t + "/consentir"), compte(patient, "PATIENT"), null);
        ok(post("/api/teleconsultations/" + t + "/demarrer"), compte(medecin, "MEDECIN"), null);
        // la consultation se tient a l'heure du rendez-vous : on simule l'heure passee
        RendezVous enCours = rendezVous.enregistrer(new RendezVous(rdv.id(), patient, medecin,
                Instant.now().minus(5, ChronoUnit.MINUTES), StatutRdv.CONFIRME));
        ok(post("/api/teleconsultations/" + t + "/terminer"), compte(medecin, "MEDECIN"), null);
        assertThat(rendezVous.parId(enCours.id()).orElseThrow().statut()).isEqualTo(StatutRdv.HONORE);

        // une teleconsultation ne se propose pas sur un rendez-vous deja passe
        assertThat(statut(post("/api/medecin/teleconsultations"), compte(medecin, "MEDECIN"),
                "{\"rendezVousId\":\"" + rdvPasse(patient, medecin).id() + "\"}")).isEqualTo(409);
    }

    @Test
    void ordonnance_annulee_par_le_medecin_ou_delivree_une_seule_fois_en_pharmacie() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID(), pharmacie = UUID.randomUUID();
        rdvPasse(patient, medecin);
        String corps = "{\"patientId\":\"" + patient + "\",\"lignes\":[{\"medicament\":\"Ibuprofene 400 mg\"}]}";

        String o1 = ok(post("/api/ordonnances"), compte(medecin, "MEDECIN"), corps);
        String code1 = lire(o1, "$.codeVerification");
        ok(post("/api/pharmacie/ordonnances/" + code1 + "/delivrer"), compte(pharmacie, "PHARMACIE"), null);
        assertThat((String) lire(ok(get("/api/ordonnances/verifier/" + code1), null, null), "$.statut")).isEqualTo("DELIVREE");
        assertThat(statut(post("/api/pharmacie/ordonnances/" + code1 + "/delivrer"), compte(pharmacie, "PHARMACIE"), null)).isEqualTo(409);

        String o2 = ok(post("/api/ordonnances"), compte(medecin, "MEDECIN"), corps);
        String id2 = lire(o2, "$.id"), code2 = lire(o2, "$.codeVerification");
        assertThat(statut(post("/api/medecin/ordonnances/" + id2 + "/annuler"), compte(UUID.randomUUID(), "MEDECIN"), "{}")).isEqualTo(403);
        ok(post("/api/medecin/ordonnances/" + id2 + "/annuler"), compte(medecin, "MEDECIN"), "{\"motif\":\"Erreur de dosage\"}");
        assertThat((Boolean) lire(ok(get("/api/ordonnances/verifier/" + code2), null, null), "$.valide")).isFalse();
        assertThat(statut(post("/api/pharmacie/ordonnances/" + code2 + "/delivrer"), compte(pharmacie, "PHARMACIE"), null)).isEqualTo(409);
    }

    @Test
    void patient_absent_et_notifications_avec_lien() throws Exception {
        UUID medecin = UUID.randomUUID(), patient = UUID.randomUUID();
        String futur = reserver(patient, ouvrirCreneau(medecin, demain(17)));
        assertThat(statut(post("/api/medecin/rendezvous/" + futur + "/absent"), compte(medecin, "MEDECIN"), null)).isEqualTo(409);

        RendezVous passe = rdvPasse(patient, medecin);
        ok(post("/api/medecin/rendezvous/" + passe.id() + "/absent"), compte(medecin, "MEDECIN"), null);
        assertThat(rendezVous.parId(passe.id()).orElseThrow().statut()).isEqualTo(StatutRdv.ABSENT);

        String notifs = ok(get("/api/notifications/mes"), compte(patient, "PATIENT"), null);
        assertThat((List<?>) lire(notifs, "$[?(@.lien=='/mes-rendez-vous')]")).isNotEmpty();
    }
}
