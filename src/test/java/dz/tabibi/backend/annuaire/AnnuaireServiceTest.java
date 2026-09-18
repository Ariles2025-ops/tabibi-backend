package dz.tabibi.backend.annuaire;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.annuaire.application.AnnuaireService;
import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnuaireServiceTest {

    private final AnnuaireService service = new AnnuaireService(new EnMemoireMedecinRepository());

    @Test
    void filtre_par_specialite() {
        List<Medecin> res = service.rechercher(CritereRecherche.de("cardiologue", null, null));
        assertThat(res).isNotEmpty();
        assertThat(res).allMatch(m -> m.specialiteSlug().equals("cardiologue"));
    }

    @Test
    void filtre_par_wilaya_et_specialite() {
        List<Medecin> res = service.rechercher(CritereRecherche.de("cardiologue", "31", null));
        assertThat(res).allMatch(m -> m.wilayaCode().equals("31") && m.specialiteSlug().equals("cardiologue"));
    }

    @Test
    void filtre_par_texte_sur_le_nom() {
        List<Medecin> res = service.rechercher(CritereRecherche.de(null, null, "amina"));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).nomComplet()).contains("Amina");
    }

    @Test
    void sans_critere_retourne_tout() {
        assertThat(service.rechercher(CritereRecherche.de(null, null, null))).isNotEmpty();
    }
}
