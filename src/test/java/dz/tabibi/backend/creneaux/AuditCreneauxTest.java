package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauExistantException;
import dz.tabibi.backend.creneaux.domain.JourSemaine;
import dz.tabibi.backend.creneaux.domain.ResultatSemaineType;
import dz.tabibi.backend.creneaux.domain.SemaineType;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Audit du 17/11 : alerte unique de la semaine type, doublons de creneaux, suppression conditionnelle, annulation passee. */
class AuditCreneauxTest {

    private static final UUID MEDECIN = UUID.randomUUID();

    record Alerte(UUID medecinId, Instant debut) {}

    private final List<Alerte> alertes = new ArrayList<>();
    private final EnMemoireCreneauRepository depot = new EnMemoireCreneauRepository();
    private final CreneauService service = new CreneauService(depot, (m, d) -> alertes.add(new Alerte(m, d)));

    private static LocalDate lundi() {
        LocalDate d = LocalDate.now().plusDays(30);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static SemaineType modele() {
        return new SemaineType(List.of(JourSemaine.LUNDI, JourSemaine.MARDI),
                LocalTime.of(9, 0), LocalTime.of(10, 0), 20, lundi(), 2);
    }

    @Test
    void la_semaine_type_n_envoie_qu_une_alerte_liste_d_attente_par_appel() {
        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN, modele());

        assertThat(r.crees()).isEqualTo(12);
        assertThat(alertes).hasSize(1);
        assertThat(alertes.get(0).medecinId()).isEqualTo(MEDECIN);
    }

    @Test
    void une_semaine_type_qui_ne_cree_rien_n_alerte_personne() {
        service.ouvrirSemaineType(MEDECIN, modele());
        alertes.clear();

        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN, modele());

        assertThat(r.crees()).isZero();
        assertThat(alertes).isEmpty();
    }

    @Test
    void le_depot_refuse_deux_creneaux_a_la_meme_heure_pour_le_meme_medecin() {
        Instant debut = Instant.now().plusSeconds(86_400);
        depot.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, debut, 20, true));

        assertThatThrownBy(() -> depot.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, debut, 20, true)))
                .isInstanceOf(CreneauExistantException.class);
        // Un autre medecin a la meme heure, ou la mise a jour du meme creneau, restent permis.
        depot.enregistrer(new Creneau(UUID.randomUUID(), UUID.randomUUID(), debut, 20, true));
        Creneau premier = depot.aVenirPour(MEDECIN, Instant.now()).get(0);
        depot.enregistrer(premier.reserver());
    }

    @Test
    void la_semaine_type_ignore_un_creneau_deja_ouvert_entre_temps() {
        AtomicInteger appels = new AtomicInteger();
        EnMemoireCreneauRepository course = new EnMemoireCreneauRepository() {
            @Override
            public synchronized Creneau enregistrer(Creneau creneau) {
                if (appels.incrementAndGet() == 2) {
                    throw new CreneauExistantException("doublon");
                }
                return super.enregistrer(creneau);
            }
        };
        CreneauService s = new CreneauService(course, (m, d) -> alertes.add(new Alerte(m, d)));

        ResultatSemaineType r = s.ouvrirSemaineType(MEDECIN, modele());

        assertThat(r.crees()).isEqualTo(11);
        assertThat(r.ignores()).isEqualTo(1);
        assertThat(alertes).hasSize(1);
    }

    @Test
    void ouvrir_un_creneau_en_double_a_la_course_leve_une_exception_de_conflit() {
        EnMemoireCreneauRepository course = new EnMemoireCreneauRepository() {
            @Override
            public synchronized Creneau enregistrer(Creneau creneau) {
                throw new CreneauExistantException("doublon");
            }
        };
        CreneauService s = new CreneauService(course, (m, d) -> { });

        assertThatThrownBy(() -> s.ouvrir(MEDECIN, Instant.now().plusSeconds(86_400), 20))
                .isInstanceOf(CreneauExistantException.class);
    }

    @Test
    void supprimer_est_conditionnel_et_refuse_un_creneau_reserve_entre_temps() {
        Creneau libre = depot.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(86_400), 20, true));
        // Le creneau est lu libre, puis reserve avant la suppression : le delete conditionnel ne supprime rien.
        EnMemoireCreneauRepository course = new EnMemoireCreneauRepository() {
            @Override
            public boolean supprimerSiDisponible(UUID id) {
                reserverSiDisponible(id);
                return super.supprimerSiDisponible(id);
            }
        };
        course.enregistrer(libre);
        CreneauService s = new CreneauService(course, (m, d) -> { });

        assertThatThrownBy(() -> s.supprimer(MEDECIN, libre.id())).isInstanceOf(TransitionInvalideException.class);
        assertThat(course.parId(libre.id())).isPresent();
        assertThat(course.parId(libre.id()).orElseThrow().disponible()).isFalse();
    }

    @Test
    void supprimer_un_creneau_libre_le_supprime() {
        Creneau libre = depot.enregistrer(new Creneau(UUID.randomUUID(), MEDECIN, Instant.now().plusSeconds(86_400), 20, true));

        service.supprimer(MEDECIN, libre.id());

        assertThat(depot.parId(libre.id())).isEmpty();
    }

    @Test
    void le_cabinet_ne_peut_pas_annuler_un_rendez_vous_deja_passe() {
        RendezVous passe = new RendezVous(UUID.randomUUID(), UUID.randomUUID(), MEDECIN,
                Instant.now().minusSeconds(3600), StatutRdv.CONFIRME);

        assertThatThrownBy(passe::annulerParCabinet).isInstanceOf(TransitionInvalideException.class);
        assertThat(passe.statut()).isEqualTo(StatutRdv.CONFIRME);

        RendezVous aVenir = new RendezVous(UUID.randomUUID(), UUID.randomUUID(), MEDECIN,
                Instant.now().plusSeconds(3600), StatutRdv.CONFIRME);
        aVenir.annulerParCabinet();
        assertThat(aVenir.statut()).isEqualTo(StatutRdv.ANNULE);
    }
}
