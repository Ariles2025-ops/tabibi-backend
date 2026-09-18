package dz.tabibi.backend.ordonnances;

import dz.tabibi.backend.ordonnances.domain.CodeVerification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeVerificationTest {

    @Test
    void genere_8_caracteres_sans_lettre_ni_chiffre_ambigu() {
        for (int i = 0; i < 200; i++) {
            String code = CodeVerification.generer();
            assertThat(code).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
        }
    }

    @Test
    void normalise_un_code_saisi_a_la_main() {
        assertThat(CodeVerification.normaliser("  ab23cd45 ")).isEqualTo("AB23CD45");
        assertThat(CodeVerification.normaliser(null)).isEqualTo("");
    }
}
