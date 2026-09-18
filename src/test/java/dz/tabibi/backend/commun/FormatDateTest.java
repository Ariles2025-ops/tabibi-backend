package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.domain.FormatDate;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Les dates des messages sont presentees a l'heure d'Algerie (UTC+1, sans heure d'ete). */
class FormatDateTest {

    @Test
    void presente_la_date_et_l_heure_a_l_heure_d_algerie() {
        assertThat(FormatDate.lisible(Instant.parse("2026-12-07T09:00:00Z"))).isEqualTo("07/12/2026 a 10:00");
        assertThat(FormatDate.lisible(Instant.parse("2026-07-01T23:30:00Z"))).isEqualTo("02/07/2026 a 00:30");
    }
}
