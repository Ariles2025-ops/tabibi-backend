package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.Langue;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalogue des messages : les trois langues portent exactement les memes cles, toutes les cles
 * nommees dans {@link Cles} y figurent, et les parametres sont bien substitues.
 */
class MessagesTest {

    private final Messages messages = Messages.partagees();

    @Test
    void les_trois_fichiers_portent_exactement_les_memes_cles() {
        Set<String> fr = messages.cles(Langue.FR);
        Set<String> ar = messages.cles(Langue.AR);
        Set<String> en = messages.cles(Langue.EN);

        assertThat(fr).isNotEmpty();
        assertThat(manquantes(fr, ar)).isEmpty();
        assertThat(manquantes(ar, fr)).isEmpty();
        assertThat(manquantes(fr, en)).isEmpty();
        assertThat(manquantes(en, fr)).isEmpty();
    }

    @Test
    void toutes_les_cles_nommees_existent_dans_les_trois_langues() {
        for (String cle : Cles.toutes()) {
            for (Langue langue : Langue.values()) {
                assertThat(messages.connait(langue, cle))
                        .as("cle " + cle + " en " + langue.code()).isTrue();
            }
        }
    }

    @Test
    void aucune_cle_n_est_declaree_deux_fois_ni_oubliee_dans_Cles() {
        assertThat(new TreeSet<>(Cles.toutes())).hasSize(Cles.toutes().size());
        assertThat(manquantes(messages.cles(Langue.FR), new TreeSet<>(Cles.toutes()))).isEmpty();
    }

    @Test
    void rend_le_texte_de_la_langue_demandee() {
        assertThat(messages.message(Langue.FR, Cles.ACCES_REFUSE)).isEqualTo("Acces refuse.");
        assertThat(messages.message(Langue.EN, Cles.ACCES_REFUSE)).isEqualTo("Access denied.");
        assertThat(messages.message(Langue.AR, Cles.ACCES_REFUSE)).isEqualTo("الوصول مرفوض.");
    }

    @Test
    void remplace_les_reperes_par_les_parametres() {
        assertThat(messages.message(Langue.FR, Cles.MESSAGE_TROP_LONG, 2000))
                .isEqualTo("Le message ne peut pas depasser 2000 caracteres.");
        assertThat(messages.message(Langue.EN, Cles.AVIS_NOTE_HORS_BORNES, 1, 5))
                .isEqualTo("The rating must be between 1 and 5.");
        assertThat(messages.message(Langue.AR, Cles.NOTIF_RAPPEL_MESSAGE, "07/12/2026 a 10:00"))
                .contains("07/12/2026 a 10:00");
    }

    @Test
    void un_repere_sans_parametre_reste_tel_quel_et_les_apostrophes_ne_sont_pas_echappees() {
        assertThat(messages.message(Langue.FR, Cles.MESSAGE_TROP_LONG)).contains("{0}");
        assertThat(messages.message(Langue.FR, Cles.CRENEAU_DEJA_RESERVE))
                .isEqualTo("Ce creneau n'est plus disponible.");
        assertThat(messages.message(Langue.FR, Cles.BESOIN_CLOTURE)).contains("n'accepte plus");
    }

    @Test
    void une_langue_absente_retombe_sur_le_francais_et_une_cle_inconnue_est_renvoyee_telle_quelle() {
        assertThat(messages.message(null, Cles.ACCES_REFUSE)).isEqualTo("Acces refuse.");
        assertThat(messages.message(Langue.AR, "erreur.inexistante")).isEqualTo("erreur.inexistante");
        assertThat(messages.message(Langue.FR, null)).isEmpty();
    }

    /** Les cles de {@code reference} que {@code candidat} ne connait pas. */
    private static Set<String> manquantes(Set<String> reference, Set<String> candidat) {
        Set<String> absentes = new TreeSet<>(reference);
        absentes.removeAll(candidat);
        return absentes;
    }
}
