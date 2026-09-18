package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreneauServiceTest {

    private final CreneauService service = new CreneauService(new EnMemoireCreneauRepository());

    @Test
    void liste_les_creneaux_disponibles_d_un_medecin_seede() {
        UUID medecin = EnMemoireMedecinRepository.MEDECINS_DEMO.get(0).id();

        List<Creneau> res = service.disponiblesPour(medecin);

        assertThat(res).isNotEmpty();
        assertThat(res).allMatch(c -> c.medecinId().equals(medecin) && c.disponible());
        assertThat(res).isSortedAccordingTo(Comparator.comparing(Creneau::debut));
    }

    @Test
    void chaque_medecin_seede_a_des_creneaux() {
        for (var medecin : EnMemoireMedecinRepository.MEDECINS_DEMO) {
            assertThat(service.disponiblesPour(medecin.id())).isNotEmpty();
        }
    }

    @Test
    void medecin_inconnu_sans_creneau() {
        assertThat(service.disponiblesPour(UUID.randomUUID())).isEmpty();
    }
}
