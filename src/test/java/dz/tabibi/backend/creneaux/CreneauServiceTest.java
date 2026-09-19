package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.annuaire.adapter.EnMemoireMedecinRepository;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreneauServiceTest {

    /** Fausse alerte de la liste d'attente : memorise les creneaux liberes signales. */
    static class FausseAlerteCreneau implements AlerteCreneau {
        record Alerte(UUID medecinId, Instant debut) {}

        final List<Alerte> alertes = new ArrayList<>();

        @Override
        public void creneauLibere(UUID medecinId, Instant debut) {
            alertes.add(new Alerte(medecinId, debut));
        }
    }

    private final FausseAlerteCreneau alerte = new FausseAlerteCreneau();
    private final CreneauService service = new CreneauService(new EnMemoireCreneauRepository(), alerte);

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

    @Test
    void ouvre_un_creneau_futur_propose_aux_patients() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.now().plus(2, ChronoUnit.DAYS);

        Creneau creneau = service.ouvrir(medecin, debut, 30);

        assertThat(creneau.id()).isNotNull();
        assertThat(creneau.medecinId()).isEqualTo(medecin);
        assertThat(creneau.debut()).isEqualTo(debut);
        assertThat(creneau.dureeMinutes()).isEqualTo(30);
        assertThat(creneau.disponible()).isTrue();
        assertThat(service.disponiblesPour(medecin)).containsExactly(creneau);
    }

    @Test
    void ouvrir_un_creneau_alerte_la_liste_d_attente_du_medecin() {
        UUID medecin = UUID.randomUUID();
        Instant debut = Instant.now().plus(2, ChronoUnit.DAYS);

        service.ouvrir(medecin, debut, 30);

        assertThat(alerte.alertes).containsExactly(new FausseAlerteCreneau.Alerte(medecin, debut));
    }

    @Test
    void refuse_un_creneau_dans_le_passe() {
        Instant passe = Instant.now().minus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> service.ouvrir(UUID.randomUUID(), passe, 20))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrir(UUID.randomUUID(), null, 20))
                .isInstanceOf(CreneauInvalideException.class);
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void refuse_une_duree_hors_bornes() {
        UUID medecin = UUID.randomUUID();
        Instant futur = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.ouvrir(medecin, futur, 4)).isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrir(medecin, futur, 121)).isInstanceOf(CreneauInvalideException.class);
        assertThat(service.disponiblesPour(medecin)).isEmpty();
        assertThat(alerte.alertes).isEmpty();
    }

    @Test
    void accepte_les_durees_limites() {
        UUID medecin = UUID.randomUUID();
        Instant futur = Instant.now().plus(1, ChronoUnit.DAYS);

        service.ouvrir(medecin, futur, 5);
        service.ouvrir(medecin, futur.plus(1, ChronoUnit.HOURS), 120);

        assertThat(service.disponiblesPour(medecin)).hasSize(2);
    }
}
