package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.LimiteurDebit;
import dz.tabibi.backend.commun.adapter.LimiteurDebit.Decision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Seau a jetons par cle : capacite, recharge continue, cles independantes, purge des entrees inactives. */
class LimiteurDebitTest {

    private final HorlogeReglable horloge = new HorlogeReglable(Instant.parse("2026-03-01T09:00:00Z"));

    private static int accepteesDeSuite(LimiteurDebit limiteur, String cle, int essais) {
        int acceptees = 0;
        for (int i = 0; i < essais; i++) {
            if (limiteur.tenter(cle).autorise()) {
                acceptees++;
            }
        }
        return acceptees;
    }

    @Test
    void accepte_la_capacite_puis_refuse() {
        LimiteurDebit limiteur = LimiteurDebit.parMinute(3, horloge);

        assertThat(limiteur.tenter("ip").autorise()).isTrue();
        assertThat(limiteur.tenter("ip").autorise()).isTrue();
        assertThat(limiteur.tenter("ip").autorise()).isTrue();
        Decision refus = limiteur.tenter("ip");

        assertThat(refus.autorise()).isFalse();
        assertThat(refus.attenteSecondes()).isEqualTo(20L); // 60 s / 3 jetons : un jeton toutes les 20 s
    }

    @Test
    void se_recharge_au_fil_du_temps_sans_depasser_la_capacite() {
        LimiteurDebit limiteur = LimiteurDebit.parMinute(60, horloge); // un jeton par seconde
        assertThat(accepteesDeSuite(limiteur, "ip", 100)).isEqualTo(60);

        horloge.avancer(Duration.ofSeconds(5));
        assertThat(accepteesDeSuite(limiteur, "ip", 100)).isEqualTo(5);

        horloge.avancer(Duration.ofMinutes(10)); // bien plus qu'une periode : le seau est plein, pas plus
        assertThat(accepteesDeSuite(limiteur, "ip", 100)).isEqualTo(60);
    }

    @Test
    void l_attente_annoncee_est_le_delai_avant_le_prochain_jeton_au_moins_une_seconde() {
        LimiteurDebit limiteur = LimiteurDebit.parMinute(120, horloge); // un jeton toutes les 500 ms
        accepteesDeSuite(limiteur, "ip", 120);

        assertThat(limiteur.tenter("ip").attenteSecondes()).isEqualTo(1L);

        horloge.avancer(Duration.ofMillis(500));
        assertThat(limiteur.tenter("ip").autorise()).isTrue();
        assertThat(limiteur.tenter("ip").autorise()).isFalse();
    }

    @Test
    void les_cles_sont_independantes() {
        LimiteurDebit limiteur = LimiteurDebit.parMinute(2, horloge);
        accepteesDeSuite(limiteur, "192.168.1.10", 2);

        assertThat(limiteur.tenter("192.168.1.10").autorise()).isFalse();
        assertThat(limiteur.tenter("192.168.1.11").autorise()).isTrue();
        assertThat(limiteur.tenter("192.168.1.11").autorise()).isTrue();
        assertThat(limiteur.tenter("192.168.1.11").autorise()).isFalse();
        assertThat(limiteur.nombreDeCles()).isEqualTo(2);
    }

    @Test
    void purge_les_cles_inactives_depuis_une_periode_entiere() {
        LimiteurDebit limiteur = new LimiteurDebit(5, Duration.ofSeconds(30), horloge);
        limiteur.tenter("ancienne");
        horloge.avancer(Duration.ofSeconds(20));
        limiteur.tenter("recente");

        horloge.avancer(Duration.ofSeconds(10)); // ancienne : 30 s sans requete ; recente : 10 s
        limiteur.purger();

        assertThat(limiteur.nombreDeCles()).isEqualTo(1);
        assertThat(accepteesDeSuite(limiteur, "ancienne", 10)).isEqualTo(5); // une cle purgee revient avec un seau plein
    }

    @Test
    void la_purge_se_declenche_d_elle_meme_au_plus_une_fois_par_periode() {
        LimiteurDebit limiteur = new LimiteurDebit(5, Duration.ofSeconds(30), horloge);
        for (int i = 0; i < 1000; i++) {
            limiteur.tenter("ip-" + i);
        }
        assertThat(limiteur.nombreDeCles()).isEqualTo(1000);

        horloge.avancer(Duration.ofSeconds(29));
        limiteur.tenter("encore");
        assertThat(limiteur.nombreDeCles()).isEqualTo(1001); // pas encore une periode

        horloge.avancer(Duration.ofSeconds(1));
        limiteur.tenter("encore");
        assertThat(limiteur.nombreDeCles()).isEqualTo(1); // les 1000 cles inactives sont parties, "encore" reste
    }

    @Test
    void refuse_une_capacite_ou_une_periode_invalide() {
        assertThatThrownBy(() -> new LimiteurDebit(0, Duration.ofMinutes(1), horloge))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiteurDebit(10, Duration.ZERO, horloge))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimiteurDebit(10, Duration.ofSeconds(-1), horloge))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
