package dz.tabibi.backend.annuaire.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import dz.tabibi.backend.annuaire.domain.StatsAnnuaire;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Annuaire lu directement dans Supabase (les 75 000+ fiches reelles), via la fonction
 * publique {@code chercher_praticiens} et la table {@code doctor_profiles}. C'est la meme
 * source que le site deploye tabibi.doctor. Actif sous le profil « supabase ».
 */
@Repository
@Profile("supabase")
public class SupabaseMedecinRepository implements MedecinRepository {

    private final String url;
    private final String anon;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public SupabaseMedecinRepository(
            @Value("${tabibi.supabase.url:https://pudugodhiofqrctcdwfl.supabase.co}") String url,
            @Value("${tabibi.supabase.anon-key:eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB1ZHVnb2RoaW9mcXJjdGNkd2ZsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgxNzUwNzAsImV4cCI6MjA5Mzc1MTA3MH0.XUmkPhXN8W0bX9L2-MVPuqWVjOsNP69zDqTF2XpR0U4}") String anon) {
        this.url = url.replaceAll("/+$", "");
        this.anon = anon;
    }

    @Override
    public List<Medecin> rechercher(CritereRecherche c) {
        String wilaya = c.wilaya();
        String specialite = c.specialite();
        String q = (c.texte() == null || c.texte().isBlank()) ? null : c.texte().trim();
        // La fonction exige au moins un filtre : par defaut on montre Alger (wilaya 16).
        if (wilaya == null && specialite == null && q == null) {
            wilaya = "16";
        }
        ObjectNode corps = json.createObjectNode();
        putOrNull(corps, "p_wilaya", wilaya);
        putOrNull(corps, "p_specialite", specialite);
        putOrNull(corps, "p_q", q);
        corps.putNull("p_type");
        corps.put("p_page", 1);
        corps.put("p_limite", 50);
        corps.putNull("p_reclamables");
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/rest/v1/rpc/chercher_praticiens"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anon)
                    .header("Authorization", "Bearer " + anon)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = json.readTree(resp.body());
            JsonNode lignes = root.get("lignes");
            List<Medecin> out = new ArrayList<>();
            if (lignes != null && lignes.isArray()) {
                for (JsonNode n : lignes) {
                    Medecin m = versMedecin(n);
                    if (m != null) {
                        out.add(m);
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Optional<Medecin> parId(UUID id) {
        // La table doctor_profiles n'est pas lisible par la cle anon (RLS) : on passe par
        // la fonction publique praticien(p_id) (SECURITY DEFINER), meme source que la fiche web.
        try {
            ObjectNode corps = json.createObjectNode();
            corps.put("p_id", id.toString());
            corps.putNull("p_legacy_id");
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/rest/v1/rpc/praticien"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anon)
                    .header("Authorization", "Bearer " + anon)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = json.readTree(resp.body());
            if (root.isArray()) {
                root = root.size() > 0 ? root.get(0) : null;
            }
            return Optional.ofNullable(versMedecin(root));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Medecin enregistrer(Medecin medecin) {
        // Annuaire Supabase en lecture seule ici : la publication passe par le flux Supabase.
        return medecin;
    }

    private Medecin versMedecin(JsonNode n) {
        if (n == null || !n.hasNonNull("id")) {
            return null;
        }
        String wilCode = n.hasNonNull("wilaya_code") ? String.valueOf(n.get("wilaya_code").asInt()) : null;
        return new Medecin(
                UUID.fromString(n.get("id").asText()),
                txt(n, "full_name"),
                txt(n, "specialty_slug"),
                txt(n, "specialty_fr"),
                wilCode,
                txt(n, "wilaya_fr"),
                txt(n, "city"));
    }

    private static String txt(JsonNode n, String champ) {
        return n.hasNonNull(champ) ? n.get(champ).asText() : null;
    }

    private static void putOrNull(ObjectNode node, String champ, String valeur) {
        if (valeur == null) {
            node.putNull(champ);
        } else {
            node.put(champ, valeur);
        }
    }

    @Override
    public StatsAnnuaire stats() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/rest/v1/rpc/stats_publiques"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anon)
                    .header("Authorization", "Bearer " + anon)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                return new StatsAnnuaire(0, 58);
            }
            JsonNode root = json.readTree(resp.body());
            if (root.isArray()) {
                root = root.size() > 0 ? root.get(0) : root;
            }
            if (root.has("stats_publiques")) {
                root = root.get("stats_publiques");
            }
            long total = root.hasNonNull("total") ? root.get("total").asLong() : 0;
            int wil = (root.get("wilayas") != null && root.get("wilayas").isArray())
                    ? root.get("wilayas").size() : 58;
            return new StatsAnnuaire(total, wil);
        } catch (Exception e) {
            return new StatsAnnuaire(0, 58);
        }
    }
}
