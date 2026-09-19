package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.domain.Langue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture tolerante de la langue demandee : en-tete Accept-Language (qualites, sous-etiquettes,
 * langues inconnues) et code de langue d'un profil. Le francais est toujours le repli.
 */
class LangueTest {

    @Test
    void lit_une_langue_simple_dans_l_en_tete() {
        assertThat(Langue.depuisEntete("ar")).isEqualTo(Langue.AR);
        assertThat(Langue.depuisEntete("fr")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("en")).isEqualTo(Langue.EN);
    }

    @Test
    void retient_la_langue_de_meilleure_qualite() {
        assertThat(Langue.depuisEntete("ar-DZ,fr;q=0.9")).isEqualTo(Langue.AR);
        assertThat(Langue.depuisEntete("fr;q=0.8,ar;q=0.9")).isEqualTo(Langue.AR);
        assertThat(Langue.depuisEntete("ar;q=0.3,en;q=0.7,fr;q=0.1")).isEqualTo(Langue.EN);
    }

    @Test
    void ignore_la_sous_etiquette_de_region() {
        assertThat(Langue.depuisEntete("en-US")).isEqualTo(Langue.EN);
        assertThat(Langue.depuisEntete("fr-DZ")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("AR-dz")).isEqualTo(Langue.AR);
    }

    @Test
    void un_en_tete_vide_absent_ou_inconnu_donne_le_francais() {
        assertThat(Langue.depuisEntete(null)).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("   ")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("de")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("kab")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("*")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete(",,")).isEqualTo(Langue.FR);
    }

    @Test
    void ignore_les_langues_inconnues_et_garde_la_premiere_connue() {
        assertThat(Langue.depuisEntete("de,en;q=0.8")).isEqualTo(Langue.EN);
        assertThat(Langue.depuisEntete("zh-CN,de-DE;q=0.9,ar;q=0.5")).isEqualTo(Langue.AR);
    }

    @Test
    void une_qualite_nulle_ou_illisible_ne_choisit_pas_la_langue() {
        assertThat(Langue.depuisEntete("en;q=0")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("ar;q=abc")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisEntete("ar;q=0,en;q=0.5")).isEqualTo(Langue.EN);
    }

    @Test
    void lit_le_code_de_langue_d_un_profil() {
        assertThat(Langue.depuisCode("fr")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisCode("AR")).isEqualTo(Langue.AR);
        assertThat(Langue.depuisCode(" en ")).isEqualTo(Langue.EN);
        assertThat(Langue.depuisCode("ar_DZ")).isEqualTo(Langue.AR);
        assertThat(Langue.depuisCode("kab")).isEqualTo(Langue.FR);
        assertThat(Langue.depuisCode(null)).isEqualTo(Langue.FR);
        assertThat(Langue.depuisCode("")).isEqualTo(Langue.FR);
    }

    @Test
    void chaque_langue_porte_son_code_iso() {
        assertThat(Langue.FR.code()).isEqualTo("fr");
        assertThat(Langue.AR.code()).isEqualTo("ar");
        assertThat(Langue.EN.code()).isEqualTo("en");
        assertThat(Langue.PAR_DEFAUT).isEqualTo(Langue.FR);
    }
}
