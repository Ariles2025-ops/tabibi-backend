package dz.tabibi.backend.annuaire.adapter;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Annuaire en memoire avec quelques praticiens de demonstration. */
@Repository
@Profile("!postgres")
public class EnMemoireMedecinRepository implements MedecinRepository {

    private final List<Medecin> donnees = List.of(
            new Medecin(UUID.randomUUID(), "Dr Amina Belkacem", "cardiologue", "Cardiologue", "16", "Alger", "Alger-Centre"),
            new Medecin(UUID.randomUUID(), "Dr Karim Haddad", "generaliste", "Medecin generaliste", "16", "Alger", "Bab Ezzouar"),
            new Medecin(UUID.randomUUID(), "Dr Yacine Meziane", "dermatologue", "Dermatologue", "31", "Oran", "Oran"),
            new Medecin(UUID.randomUUID(), "Dr Lila Cherif", "pediatre", "Pediatre", "25", "Constantine", "Constantine"),
            new Medecin(UUID.randomUUID(), "Dr Sofiane Brahimi", "cardiologue", "Cardiologue", "31", "Oran", "Es Senia")
    );

    @Override
    public List<Medecin> rechercher(CritereRecherche c) {
        return donnees.stream()
                .filter(m -> c.specialite() == null || m.specialiteSlug().equalsIgnoreCase(c.specialite()))
                .filter(m -> c.wilaya() == null || m.wilayaCode().equals(c.wilaya()))
                .filter(m -> c.texte() == null
                        || m.nomComplet().toLowerCase(Locale.ROOT).contains(c.texte().toLowerCase(Locale.ROOT)))
                .toList();
    }
}
