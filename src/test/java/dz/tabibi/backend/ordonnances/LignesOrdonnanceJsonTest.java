package dz.tabibi.backend.ordonnances;

import com.fasterxml.jackson.databind.ObjectMapper;
import dz.tabibi.backend.ordonnances.adapter.LignesOrdonnanceJson;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Le codec JSON de la colonne lignes_json restitue exactement les lignes. */
class LignesOrdonnanceJsonTest {

    private final LignesOrdonnanceJson codec = new LignesOrdonnanceJson(new ObjectMapper());

    @Test
    void aller_retour_des_lignes() {
        List<LigneOrdonnance> lignes = List.of(
                new LigneOrdonnance("Paracetamol 1 g", "1 comprime matin et soir", "5 jours"),
                new LigneOrdonnance("Sirop antitussif", "1 cuillere le soir", null));

        String json = codec.versJson(lignes);

        assertThat(json).contains("Paracetamol 1 g");
        assertThat(codec.depuisJson(json)).containsExactlyElementsOf(lignes);
    }

    @Test
    void colonne_vide_ou_nulle_vaut_aucune_ligne() {
        assertThat(codec.depuisJson(null)).isEmpty();
        assertThat(codec.depuisJson("  ")).isEmpty();
        assertThat(codec.depuisJson("[]")).isEmpty();
    }
}
