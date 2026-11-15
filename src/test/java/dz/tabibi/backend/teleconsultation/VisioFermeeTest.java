package dz.tabibi.backend.teleconsultation;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.teleconsultation.application.TeleconsultationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Garde : par defaut (production au lancement), aucune teleconsultation ne peut etre proposee. */
@SpringBootTest(properties = "tabibi.rappels.actifs=false")
class VisioFermeeTest {

    @Autowired
    TeleconsultationService service;

    @Test
    void la_visio_est_fermee_par_defaut() {
        assertThatThrownBy(() -> service.planifier(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(TransitionInvalideException.class)
                .hasMessageContaining("bientôt");
    }
}
