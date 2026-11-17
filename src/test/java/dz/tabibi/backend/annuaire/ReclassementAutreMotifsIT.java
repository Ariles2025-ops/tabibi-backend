package dz.tabibi.backend.annuaire;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Migration 024 (motifs supplementaires) : le vrai SQL du changeset, sur un vrai PostgreSQL. */
@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers
@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class ReclassementAutreMotifsIT {

    private static final String SQL_024 = "db/changelog/sql/024-reclassement-autre-motifs.sql";

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    private UUID fiche(String nom, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into medecin (id, nom_complet, nom_tri, specialite_slug, specialite_fr, wilaya_code, wilaya_fr) "
                + "values (?, ?, ?, ?, ?, '15', 'Tizi Ouzou')", id, nom, nom.toLowerCase(), slug,
                "autre".equals(slug) ? "Autre" : slug);
        return id;
    }

    private Map<String, Object> lire(UUID id) {
        return jdbc.queryForMap("select nom_complet, specialite_slug, specialite_fr from medecin where id = ?", id);
    }

    private void attendu(UUID id, String nom, String slug) {
        assertThat(lire(id)).containsEntry("nom_complet", nom).containsEntry("specialite_slug", slug);
    }

    private void resteAutre(UUID id, String nomInchange) {
        assertThat(lire(id)).containsEntry("nom_complet", nomInchange).containsEntry("specialite_slug", "autre");
    }

    @Test
    void reclasse_10_exemples_et_laisse_5_fiches_autre() throws Exception {
        UUID gyneco = fiche("Benali Karim, Gynéco-Obstétrique À Alger", "autre");
        UUID cardio = fiche("Hamdi Sara, Cardio", "autre");
        UUID kine = fiche("Kaci Amine, Kiné À Oran", "autre");
        UUID orl = fiche("Ouali Nadia, Oto-Rhino-Laryngologiste", "autre");
        UUID dentiste = fiche("Zerrouki Imene, Chirurgien Dentiste", "autre");
        UUID sageFemme = fiche("Saidi Lila, Sages-Femmes À Blida", "autre");
        UUID ophtalmo = fiche("Brahimi Yacine, Ophtalmo", "autre");
        UUID arDentiste = fiche("بن علي كريم، طبيب أسنان", "autre");
        UUID arPediatre = fiche("مرابط سارة، طبيب أطفال", "autre");
        UUID arCardio = fiche("حداد أمينة، أخصائية أمراض القلب", "autre");

        UUID multi = fiche("Amrani Reda, Cardiologue Et Pédiatre", "autre");
        UUID pharmacie = fiche("Pharmacie El Chifa, Gynéco", "autre");
        UUID neuroChir = fiche("Bouzid Sofiane, Neuro-Chirurgien", "autre");
        UUID infirmier = fiche("Cherif Nassim, Infirmier En Cardiologie", "autre");
        UUID sansVirgule = fiche("Dr Mebarki Kamel Cardiologue", "autre");
        UUID dejaClasse = fiche("Saadi Omar, Cardio", "pediatre");

        String sql = new ClassPathResource(SQL_024).getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);

        attendu(gyneco, "Benali Karim", "gynecologue");
        attendu(cardio, "Hamdi Sara", "cardiologue");
        attendu(kine, "Kaci Amine", "kinesitherapeute");
        attendu(orl, "Ouali Nadia", "ORL");
        attendu(dentiste, "Zerrouki Imene", "dentiste");
        attendu(sageFemme, "Saidi Lila", "sage-femme");
        attendu(ophtalmo, "Brahimi Yacine", "ophtalmologue");
        attendu(arDentiste, "بن علي كريم", "dentiste");
        attendu(arPediatre, "مرابط سارة", "pediatre");
        attendu(arCardio, "حداد أمينة", "cardiologue");
        assertThat(lire(gyneco)).containsEntry("specialite_fr", "Gynécologue");
        assertThat(lire(orl)).containsEntry("specialite_fr", "ORL");

        resteAutre(multi, "Amrani Reda, Cardiologue Et Pédiatre");           // plusieurs specialites
        resteAutre(pharmacie, "Pharmacie El Chifa, Gynéco");                 // pharmacie : traitement inchange
        resteAutre(neuroChir, "Bouzid Sofiane, Neuro-Chirurgien");           // chirurgien : pas de slug sur
        resteAutre(infirmier, "Cherif Nassim, Infirmier En Cardiologie");    // pas un medecin
        resteAutre(sansVirgule, "Dr Mebarki Kamel Cardiologue");             // pas de segment apres virgule
        attendu(dejaClasse, "Saadi Omar, Cardio", "pediatre");               // jamais touche : deja classe

        // Rejouee : plus rien a changer.
        jdbc.execute(sql);
        attendu(gyneco, "Benali Karim", "gynecologue");
        resteAutre(multi, "Amrani Reda, Cardiologue Et Pédiatre");
    }

    /** Tous les slugs de 024 doivent deja exister dans le referentiel du site (ceux de 023). */
    @Test
    void les_slugs_de_024_existent_dans_le_referentiel_023() throws Exception {
        Pattern p = Pattern.compile("\\('[^']+', '([^']+)', '[^']+'\\)");
        Set<String> ref = slugs(p, "db/changelog/sql/023-reclassement-autre.sql");
        Set<String> nouveaux = slugs(p, SQL_024);
        assertThat(nouveaux).isNotEmpty();
        assertThat(ref).containsAll(nouveaux);
    }

    private Set<String> slugs(Pattern p, String chemin) throws Exception {
        Matcher m = p.matcher(new ClassPathResource(chemin).getContentAsString(StandardCharsets.UTF_8));
        Set<String> s = new HashSet<>();
        while (m.find()) s.add(m.group(1));
        return s;
    }
}
