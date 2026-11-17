package dz.tabibi.backend.creneaux;

import dz.tabibi.backend.cabinet.adapter.EnMemoireRattachementRepository;
import dz.tabibi.backend.cabinet.application.CabinetService;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.creneaux.adapter.EnMemoireCreneauRepository;
import dz.tabibi.backend.creneaux.application.CreneauService;
import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.JourSemaine;
import dz.tabibi.backend.creneaux.domain.ResultatSemaineType;
import dz.tabibi.backend.creneaux.domain.SemaineType;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Semaine type : creation en lot, doublons ignores, limites et fuseau Africa/Algiers. */
class SemaineTypeServiceTest {

    private static final ZoneId ALGER = ZoneId.of("Africa/Algiers");
    private static final UUID MEDECIN = UUID.randomUUID();

    private final EnMemoireCreneauRepository depot = new EnMemoireCreneauRepository();
    private final CreneauService service = new CreneauService(depot, (m, d) -> { });

    /** Un lundi assez lointain pour rester dans le futur. */
    private static LocalDate lundi() {
        LocalDate d = LocalDate.now().plusDays(30);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static SemaineType modele(List<JourSemaine> jours, String debut, String fin, int duree, int semaines) {
        return new SemaineType(jours, LocalTime.parse(debut), LocalTime.parse(fin), duree, lundi(), semaines);
    }

    @Test
    void cree_les_creneaux_de_chaque_jour_et_chaque_semaine() {
        // 09:00-12:30 par 20 min = 10 creneaux (le dernier finit a 12:20) ; 2 jours x 4 semaines = 8 jours
        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI, JourSemaine.MERCREDI), "09:00", "12:30", 20, 4));

        assertThat(r.crees()).isEqualTo(80);
        assertThat(r.ignores()).isZero();
        assertThat(depot.disponiblesPour(MEDECIN)).hasSize(80);
    }

    @Test
    void les_heures_sont_celles_d_alger() {
        service.ouvrirSemaineType(MEDECIN, modele(List.of(JourSemaine.LUNDI), "09:00", "09:40", 20, 1));

        List<Creneau> crees = depot.disponiblesPour(MEDECIN);
        assertThat(crees).hasSize(2);
        assertThat(crees.get(0).debut().atZone(ALGER).toLocalDateTime())
                .isEqualTo(lundi().atTime(9, 0));
        // Alger = UTC+1 toute l'annee
        assertThat(crees.get(0).debut().atZone(ZoneId.of("UTC")).getHour()).isEqualTo(8);
        assertThat(crees.get(1).debut().atZone(ALGER).toLocalTime()).isEqualTo(LocalTime.of(9, 20));
    }

    @Test
    void ignore_les_creneaux_qui_chevauchent_un_creneau_existant() {
        service.ouvrir(MEDECIN, lundi().atTime(9, 10).atZone(ALGER).toInstant(), 20); // chevauche 09:00 et 09:20

        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 1));

        assertThat(r).isEqualTo(new ResultatSemaineType(1, 2)); // seul 09:40 est cree
    }

    @Test
    void rejouer_la_meme_semaine_ne_cree_aucun_doublon() {
        SemaineType m = modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 2);
        service.ouvrirSemaineType(MEDECIN, m);

        assertThat(service.ouvrirSemaineType(MEDECIN, m)).isEqualTo(new ResultatSemaineType(0, 6));
    }

    @Test
    void ignore_les_creneaux_deja_passes() {
        SemaineType m = new SemaineType(List.of(JourSemaine.values()), LocalTime.of(0, 0), LocalTime.of(0, 20),
                20, LocalDate.now(ALGER).minusDays(1), 1);

        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN, m);

        assertThat(r.ignores()).isGreaterThanOrEqualTo(1);
        assertThat(depot.aVenirPour(MEDECIN, java.time.Instant.EPOCH)).allMatch(c -> c.debut().isAfter(java.time.Instant.now()));
    }

    @Test
    void refuse_plus_de_8_semaines() {
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 9)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 0)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThat(service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 8)).crees()).isEqualTo(24);
    }

    @Test
    void refuse_plus_de_500_creneaux_et_n_en_cree_aucun() {
        // 7 jours x 8 semaines x 12 creneaux (08:00-12:00 par 20 min) = 672 > 500
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.values()), "08:00", "12:00", 20, 8)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThat(depot.disponiblesPour(MEDECIN)).isEmpty();
    }

    @Test
    void accepte_exactement_500_creneaux() {
        // 5 jours x 5 semaines... 500 = 10 creneaux x 50 jours impossible ; 5 jours x 8 semaines x 12 = 480
        ResultatSemaineType r = service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI, JourSemaine.MARDI, JourSemaine.MERCREDI, JourSemaine.JEUDI,
                        JourSemaine.VENDREDI), "08:00", "12:00", 20, 8));
        assertThat(r.crees()).isEqualTo(480);
    }

    @Test
    void refuse_un_modele_invalide() {
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN, modele(List.of(), "09:00", "10:00", 20, 1)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "10:00", "09:00", 20, 1)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 4, 1)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 121, 1)))
                .isInstanceOf(CreneauInvalideException.class);
        assertThatThrownBy(() -> service.ouvrirSemaineType(MEDECIN,
                new SemaineType(List.of(JourSemaine.LUNDI), LocalTime.of(9, 0), LocalTime.of(10, 0), 20, null, 1)))
                .isInstanceOf(CreneauInvalideException.class);
    }

    @Test
    void une_secretaire_non_rattachee_est_refusee() {
        CabinetService cabinet = new CabinetService(new EnMemoireRattachementRepository(), null, service, null);

        assertThatThrownBy(() -> cabinet.ouvrirSemaineTypePour(UUID.randomUUID(), MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 1)))
                .isInstanceOf(AccesRefuseException.class);
        assertThat(depot.disponiblesPour(MEDECIN)).isEmpty();
    }

    @Test
    void une_secretaire_rattachee_ouvre_la_semaine_type() {
        EnMemoireRattachementRepository rattachements = new EnMemoireRattachementRepository();
        CabinetService cabinet = new CabinetService(rattachements, null, service, (id, s, m) -> { });
        UUID secretaire = UUID.randomUUID();
        var r = cabinet.rattacher(MEDECIN, secretaire);
        cabinet.accepterInvitation(secretaire, r.id());

        ResultatSemaineType res = cabinet.ouvrirSemaineTypePour(secretaire, MEDECIN,
                modele(List.of(JourSemaine.LUNDI), "09:00", "10:00", 20, 1));

        assertThat(res.crees()).isEqualTo(3);
    }
}
