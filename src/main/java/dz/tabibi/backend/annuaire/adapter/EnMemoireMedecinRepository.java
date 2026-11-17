package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Annuaire en memoire, pre-rempli de quelques praticiens de demonstration et enrichi
 * des candidatures validees par l'administrateur.
 */
@Repository
@Profile("!postgres")
public class EnMemoireMedecinRepository implements MedecinRepository {

    /**
     * Praticiens de demonstration. Leurs identifiants sont fixes afin que d'autres donnees de
     * demonstration (creneaux, compte Keycloak medecin.demo) puissent s'y rattacher de facon stable.
     */
    public static final List<Medecin> MEDECINS_DEMO = List.of(
            new Medecin(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Dr Amina Belkacem", "cardiologue", "Cardiologue", "16", "Alger", "Alger-Centre"),
            new Medecin(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Dr Karim Haddad", "generaliste", "Médecin généraliste", "16", "Alger", "Bab Ezzouar"),
            new Medecin(UUID.fromString("00000000-0000-0000-0000-000000000003"), "Dr Yacine Meziane", "dermatologue", "Dermatologue", "31", "Oran", "Oran"),
            new Medecin(UUID.fromString("00000000-0000-0000-0000-000000000004"), "Dr Lila Cherif", "pediatre", "Pediatre", "25", "Constantine", "Constantine"),
            new Medecin(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Dr Sofiane Brahimi", "cardiologue", "Cardiologue", "31", "Oran", "Es Senia")
    );

    /** Meme ordre que la requete JPQL de l'adaptateur JPA. */
    private static final Comparator<Medecin> PAR_NOM = Comparator.comparing((Medecin m) -> m.cleDeTri().isEmpty()).thenComparing(Medecin::cleDeTri);

    private final Map<UUID, Medecin> parId = new ConcurrentHashMap<>();

    /** Les pharmacies ne sont pas listees parmi les medecins sans specialite demandee (meme regle que JPA). */
    private static final String PHARMACIE = "pharmacie";

    public EnMemoireMedecinRepository() {
        MEDECINS_DEMO.forEach(m -> parId.put(m.id(), m));
    }

    @Override
    public List<Medecin> rechercher(CritereRecherche c) {
        return parId.values().stream()
                .filter(m -> c.specialite() == null
                        ? !PHARMACIE.equalsIgnoreCase(m.specialiteSlug())
                        : m.specialiteSlug().equalsIgnoreCase(c.specialite()))
                .filter(m -> c.wilaya() == null || m.wilayaCode().equals(c.wilaya()))
                .filter(m -> c.commune() == null || c.commune().equals(Medecin.slugCommune(m.ville())))
                .filter(m -> c.texte() == null
                        || m.nomComplet().toLowerCase(Locale.ROOT).contains(c.texte().toLowerCase(Locale.ROOT)))
                .sorted(PAR_NOM)
                .toList();
    }

    @Override
    public List<CommuneNombre> communes(String specialite, String wilaya, int minimum) {
        Map<String, List<Medecin>> parSlug = new java.util.HashMap<>();
        for (Medecin m : parId.values()) {
            if (specialite == null ? PHARMACIE.equalsIgnoreCase(m.specialiteSlug()) : !m.specialiteSlug().equalsIgnoreCase(specialite)) {
                continue;
            }
            if (wilaya != null && !m.wilayaCode().equals(wilaya)) {
                continue;
            }
            String slug = Medecin.slugCommune(m.ville());
            if (slug.isEmpty() || slug.equals(Medecin.slugCommune(m.wilayaFr()))) {
                continue;
            }
            parSlug.computeIfAbsent(slug, k -> new java.util.ArrayList<>()).add(m);
        }
        return parSlug.entrySet().stream()
                .filter(e -> e.getValue().size() >= minimum)
                .map(e -> new CommuneNombre(e.getKey(),
                        e.getValue().stream().map(Medecin::ville).min(Comparator.naturalOrder()).orElse(e.getKey()),
                        e.getValue().size()))
                .sorted(Comparator.comparingLong(CommuneNombre::nombre).reversed().thenComparing(CommuneNombre::slug))
                .toList();
    }

    @Override
    public Optional<Medecin> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Medecin enregistrer(Medecin medecin) {
        parId.put(medecin.id(), medecin);
        return medecin;
    }

    @Override
    public void supprimer(UUID id) {
        parId.remove(id);
    }

    @Override
    public List<Medecin> page(int numero, int taille) {
        return parId.values().stream().sorted(java.util.Comparator.comparing(Medecin::id))
                .skip((long) numero * taille).limit(taille).toList();
    }

    @Override
    public long nombre() {
        return parId.size();
    }

    private final Map<UUID, UUID> alias = new ConcurrentHashMap<>();

    @Override
    public void ajouterAlias(UUID ancienId, UUID nouvelId) {
        alias.put(ancienId, nouvelId);
    }

    @Override
    public Optional<UUID> aliasDe(UUID ancienId) {
        return Optional.ofNullable(alias.get(ancienId));
    }
}
