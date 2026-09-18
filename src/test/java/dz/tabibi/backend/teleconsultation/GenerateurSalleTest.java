package dz.tabibi.backend.teleconsultation;

import dz.tabibi.backend.teleconsultation.domain.GenerateurSalle;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateurSalleTest {

    private final GenerateurSalle generateur = new GenerateurSalle();

    @Test
    void genere_un_nom_de_salle_prefixe_suivi_de_32_caracteres_hexadecimaux() {
        for (int i = 0; i < 200; i++) {
            String salle = generateur.generer();
            assertThat(salle).hasSize(GenerateurSalle.PREFIXE.length() + 32);
            assertThat(salle).matches("tabibi-[0-9a-f]{32}");
        }
    }

    @Test
    void genere_des_noms_de_salle_distincts() {
        Set<String> salles = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            salles.add(generateur.generer());
        }
        assertThat(salles).hasSize(500);
    }
}
