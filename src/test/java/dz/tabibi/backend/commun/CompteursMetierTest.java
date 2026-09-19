package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.CompteursMetier;
import dz.tabibi.backend.commun.domain.Compteurs;
import dz.tabibi.backend.commun.domain.CompteursNeutres;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * L'adaptateur Micrometer du port Compteurs : chaque increment se retrouve dans le registre,
 * sous le nom du compteur metier, et l'implementation neutre ne compte rien.
 */
class CompteursMetierTest {

    private final MeterRegistry registre = new SimpleMeterRegistry();
    private final Compteurs compteurs = new CompteursMetier(registre);

    @Test
    void chaque_increment_se_retrouve_dans_le_registre() {
        compteurs.incrementer(Compteurs.RENDEZVOUS_RESERVES);
        compteurs.incrementer(Compteurs.RENDEZVOUS_RESERVES);
        compteurs.incrementer(Compteurs.RENDEZVOUS_ANNULES);

        assertThat((long) registre.counter(Compteurs.RENDEZVOUS_RESERVES).count()).isEqualTo(2);
        assertThat((long) registre.counter(Compteurs.RENDEZVOUS_ANNULES).count()).isEqualTo(1);
    }

    @Test
    void un_compteur_jamais_incremente_vaut_zero() {
        assertThat((long) registre.counter(Compteurs.AVIS_DEPOSES).count()).isZero();
    }

    @Test
    void les_six_compteurs_metier_portent_le_prefixe_tabibi() {
        for (String compteur : new String[] {
                Compteurs.RENDEZVOUS_RESERVES, Compteurs.RENDEZVOUS_ANNULES, Compteurs.ORDONNANCES_EMISES,
                Compteurs.TELECONSULTATIONS_DEMARREES, Compteurs.AVIS_DEPOSES, Compteurs.LIMITE_DEPASSEMENTS}) {
            assertThat(compteur).startsWith("tabibi.");
            compteurs.incrementer(compteur);
            assertThat((long) registre.counter(compteur).count()).isEqualTo(1);
        }
    }

    @Test
    void le_meme_compteur_n_est_cree_qu_une_fois() {
        compteurs.incrementer(Compteurs.ORDONNANCES_EMISES);
        compteurs.incrementer(Compteurs.ORDONNANCES_EMISES);
        compteurs.incrementer(Compteurs.ORDONNANCES_EMISES);

        assertThat((long) registre.counter(Compteurs.ORDONNANCES_EMISES).count()).isEqualTo(3);
    }

    @Test
    void l_implementation_neutre_ne_compte_rien_et_ne_tombe_pas() {
        assertThatCode(() -> CompteursNeutres.INSTANCE.incrementer(Compteurs.AVIS_DEPOSES))
                .doesNotThrowAnyException();
        assertThat((long) registre.counter(Compteurs.AVIS_DEPOSES).count()).isZero();
    }
}
