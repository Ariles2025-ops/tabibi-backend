package dz.tabibi.backend.referentiel.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.referentiel.domain.ReferentielRepository;
import dz.tabibi.backend.referentiel.domain.Specialite;
import dz.tabibi.backend.referentiel.domain.Wilaya;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Wilayas et specialites lues directement dans Supabase (tables publiques {@code wilayas} et
 * {@code specialties}, lisibles par la cle anon). Actif sous le profil « supabase ».
 */
@Repository
@Profile("supabase")
public class SupabaseReferentielRepository implements ReferentielRepository {

    private final String url;
    private final String anon;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public SupabaseReferentielRepository(
            @Value("${tabibi.supabase.url:https://pudugodhiofqrctcdwfl.supabase.co}") String url,
            @Value("${tabibi.supabase.anon-key:eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InB1ZHVnb2RoaW9mcXJjdGNkd2ZsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgxNzUwNzAsImV4cCI6MjA5Mzc1MTA3MH0.XUmkPhXN8W0bX9L2-MVPuqWVjOsNP69zDqTF2XpR0U4}") String anon) {
        this.url = url.replaceAll("/+$", "");
        this.anon = anon;
    }

    @Override
    public List<Wilaya> wilayas() {
        JsonNode arr = get("/rest/v1/wilayas?select=code,name_fr&is_active=eq.true&order=name_fr");
        List<Wilaya> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n.hasNonNull("code") && n.hasNonNull("name_fr")) {
                    out.add(new Wilaya(String.valueOf(n.get("code").asInt()), n.get("name_fr").asText()));
                }
            }
        }
        return out;
    }

    @Override
    public List<Specialite> specialites() {
        JsonNode arr = get("/rest/v1/specialties?select=slug,name_fr&is_active=eq.true&order=name_fr");
        List<Specialite> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n.hasNonNull("slug") && n.hasNonNull("name_fr")) {
                    out.add(new Specialite(n.get("slug").asText(), n.get("name_fr").asText()));
                }
            }
        }
        return out;
    }

    private JsonNode get(String chemin) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + chemin))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", anon)
                    .header("Authorization", "Bearer " + anon)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                return null;
            }
            return json.readTree(resp.body());
        } catch (Exception e) {
            return null;
        }
    }
}
