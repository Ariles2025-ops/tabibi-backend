package dz.tabibi.backend.avis;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisInvalideException;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regles du domaine : note bornee, commentaire facultatif et borne, transitions de statut, synthese publique. */
class AvisTest {

    private static final UUID RENDEZ_VOUS = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();
    private static final Instant DEPOSE_LE = Instant.parse("2026-09-18T10:00:00Z");

    private static Avis avis(int note, String commentaire) {
        return Avis.deposer(RENDEZ_VOUS, PATIENT, MEDECIN, note, commentaire, DEPOSE_LE);
    }

    @Test
    void deposer_cree_un_avis_publie_et_nettoie_le_commentaire() {
        Avis a = avis(4, "  Tres a l'ecoute.  ");

        assertThat(a.id()).isNotNull();
        assertThat(a.rendezVousId()).isEqualTo(RENDEZ_VOUS);
        assertThat(a.patientId()).isEqualTo(PATIENT);
        assertThat(a.medecinId()).isEqualTo(MEDECIN);
        assertThat(a.note()).isEqualTo(4);
        assertThat(a.commentaire()).isEqualTo("Tres a l'ecoute.");
        assertThat(a.statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThat(a.estPublie()).isTrue();
        assertThat(a.deposeLe()).isEqualTo(DEPOSE_LE);
        assertThat(a.estDe(PATIENT)).isTrue();
        assertThat(a.estDe(MEDECIN)).isFalse();
        assertThat(a.concerne(MEDECIN)).isTrue();
        assertThat(a.concerne(PATIENT)).isFalse();
    }

    @Test
    void le_commentaire_est_facultatif_et_un_commentaire_blanc_est_efface() {
        assertThat(avis(5, null).commentaire()).isNull();
        assertThat(avis(5, "").commentaire()).isNull();
        assertThat(avis(5, "   ").commentaire()).isNull();
    }

    @Test
    void la_note_doit_etre_entre_1_et_5() {
        assertThatThrownBy(() -> avis(0, null)).isInstanceOf(AvisInvalideException.class);
        assertThatThrownBy(() -> avis(6, null)).isInstanceOf(AvisInvalideException.class);
        assertThatThrownBy(() -> avis(-3, null)).isInstanceOf(AvisInvalideException.class);
        assertThat(avis(1, null).note()).isEqualTo(1);
        assertThat(avis(5, null).note()).isEqualTo(5);
    }

    @Test
    void le_commentaire_ne_depasse_pas_500_caracteres() {
        String borne = "a".repeat(Avis.LONGUEUR_MAX_COMMENTAIRE);

        assertThat(avis(3, borne).commentaire()).hasSize(500);
        assertThat(avis(3, " " + borne + " ").commentaire()).hasSize(500);
        assertThatThrownBy(() -> avis(3, borne + "a"))
                .isInstanceOf(AvisInvalideException.class)
                .hasMessageContaining("500");
    }

    @Test
    void signaler_passe_un_avis_publie_a_signale_en_copie() {
        Avis a = avis(2, "Retard important.");

        Avis signale = a.signaler();

        assertThat(signale.id()).isEqualTo(a.id());
        assertThat(signale.statut()).isEqualTo(StatutAvis.SIGNALE);
        assertThat(signale.estPublie()).isFalse();
        assertThat(signale.note()).isEqualTo(2);
        assertThat(signale.commentaire()).isEqualTo("Retard important.");
        assertThat(a.statut()).isEqualTo(StatutAvis.PUBLIE);
    }

    @Test
    void signaler_exige_un_avis_publie() {
        Avis signale = avis(2, null).signaler();
        Avis masque = avis(2, null).masquer();

        assertThatThrownBy(signale::signaler).isInstanceOf(TransitionInvalideException.class);
        assertThatThrownBy(masque::signaler).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void masquer_accepte_un_avis_publie_ou_signale_mais_pas_deja_masque() {
        Avis depuisPublie = avis(2, null).masquer();
        Avis depuisSignale = avis(2, null).signaler().masquer();

        assertThat(depuisPublie.statut()).isEqualTo(StatutAvis.MASQUE);
        assertThat(depuisSignale.statut()).isEqualTo(StatutAvis.MASQUE);
        assertThatThrownBy(depuisPublie::masquer).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void retablir_remet_en_ligne_un_avis_signale_ou_masque_mais_pas_deja_publie() {
        Avis publie = avis(4, null);

        assertThat(publie.signaler().retablir().statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThat(publie.masquer().retablir().statut()).isEqualTo(StatutAvis.PUBLIE);
        assertThatThrownBy(publie::retablir).isInstanceOf(TransitionInvalideException.class);
    }

    @Test
    void la_synthese_calcule_la_moyenne_arrondie_a_une_decimale_et_le_nombre() {
        SyntheseAvis synthese = SyntheseAvis.de(List.of(avis(5, null), avis(4, null), avis(4, null)));

        assertThat(synthese.moyenne()).isEqualTo(4.3);
        assertThat(synthese.nombre()).isEqualTo(3);
        assertThat(synthese.avis()).hasSize(3);
    }

    @Test
    void la_synthese_sans_avis_n_a_pas_de_moyenne() {
        SyntheseAvis synthese = SyntheseAvis.de(List.of());

        assertThat(synthese.moyenne()).isNull();
        assertThat(synthese.nombre()).isEqualTo(0);
        assertThat(synthese.avis()).isEmpty();
    }
}
