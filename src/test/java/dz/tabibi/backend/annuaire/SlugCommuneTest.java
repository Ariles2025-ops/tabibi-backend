package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlugCommuneTest {

    @Test
    void normalise_accents_apostrophes_et_espaces() {
        assertThat(Medecin.slugCommune("Bab Ezzouar")).isEqualTo("bab-ezzouar");
        assertThat(Medecin.slugCommune("Alger-Centre")).isEqualTo("alger-centre");
        assertThat(Medecin.slugCommune("Béni  Messous")).isEqualTo("beni-messous");
        assertThat(Medecin.slugCommune("  M'Hamed  ")).isEqualTo("m-hamed");
        assertThat(Medecin.slugCommune(null)).isEmpty();
    }

    @Test
    void en_memoire_filtre_par_commune_et_liste_les_communes_de_3_fiches_ou_plus() {
        var repo = new EnMemoireMedecinRepository();
        for (int i = 0; i < 3; i++) {
            repo.enregistrer(new Medecin(UUID.randomUUID(), "Dr N" + i, "neuro", "Neuro", "16", "Alger", "Bab Ezzouar"));
        }
        repo.enregistrer(new Medecin(UUID.randomUUID(), "Dr Seul", "neuro", "Neuro", "16", "Alger", "Hydra"));
        repo.enregistrer(new Medecin(UUID.randomUUID(), "Dr Wil", "neuro", "Neuro", "16", "Alger", "Alger"));

        assertThat(repo.rechercher(CritereRecherche.de("neuro", "16", null, "bab-ezzouar"))).hasSize(3);
        assertThat(repo.communes("neuro", "16", 3)).hasSize(1)
                .first().satisfies(c -> assertThat(c.nombre()).isEqualTo(3));
    }
}
