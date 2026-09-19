package dz.tabibi.backend.profil;

import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilInvalideException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regles du domaine : nom complet obligatoire (2 a 120 caracteres), telephone algerien facultatif,
 * date de naissance facultative dans le passe et apres 1900, wilaya facultative (4 caracteres au plus),
 * langue parmi fr / ar / kab / en (fr par defaut).
 */
class ProfilTest {

    private static final UUID UTILISATEUR = UUID.randomUUID();
    /** 2026-09-18 a 23:30 UTC, soit deja le 19/09/2026 a l'heure d'Algerie. */
    private static final Instant MIS_A_JOUR_LE = Instant.parse("2026-09-18T23:30:00Z");
    private static final LocalDate AUJOURD_HUI_EN_ALGERIE = LocalDate.of(2026, 9, 19);
    private static final DemandeProfil DEMANDE =
            new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "fr");

    private static Profil renseigne(DemandeProfil demande) {
        return Profil.renseigner(UTILISATEUR, demande, MIS_A_JOUR_LE);
    }

    @Test
    void renseigner_cree_un_profil_complet_date() {
        Profil p = renseigne(DEMANDE);

        assertThat(p.utilisateurId()).isEqualTo(UTILISATEUR);
        assertThat(p.nomComplet()).isEqualTo("Amina Belkacem");
        assertThat(p.telephone()).isEqualTo("0550123456");
        assertThat(p.dateNaissance()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(p.wilayaCode()).isEqualTo("16");
        assertThat(p.langue()).isEqualTo("fr");
        assertThat(p.misAJourLe()).isEqualTo(MIS_A_JOUR_LE);
        assertThat(p.estDe(UTILISATEUR)).isTrue();
        assertThat(p.estDe(UUID.randomUUID())).isFalse();
    }

    @Test
    void renseigner_nettoie_les_espaces_et_vide_les_champs_facultatifs_blancs() {
        Profil p = renseigne(new DemandeProfil("  Amina Belkacem ", "0550 12 34 56", null, "  ", " "));

        assertThat(p.nomComplet()).isEqualTo("Amina Belkacem");
        assertThat(p.telephone()).isEqualTo("0550123456");
        assertThat(p.dateNaissance()).isNull();
        assertThat(p.wilayaCode()).isNull();
        assertThat(p.langue()).isEqualTo("fr");
    }

    @Test
    void seul_le_nom_complet_est_obligatoire() {
        Profil p = renseigne(new DemandeProfil("Karim Haddad", null, null, null, null));

        assertThat(p.nomComplet()).isEqualTo("Karim Haddad");
        assertThat(p.telephone()).isNull();
        assertThat(p.dateNaissance()).isNull();
        assertThat(p.wilayaCode()).isNull();
        assertThat(p.langue()).isEqualTo(Profil.LANGUE_PAR_DEFAUT);
    }

    @Test
    void le_nom_complet_est_obligatoire_et_borne() {
        assertThatThrownBy(() -> renseigne(new DemandeProfil(null, null, null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("  ", null, null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("A", null, null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("a".repeat(Profil.LONGUEUR_MAX_NOM + 1), null, null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);

        assertThat(renseigne(new DemandeProfil("Al", null, null, null, null)).nomComplet()).hasSize(2);
        assertThat(renseigne(new DemandeProfil("a".repeat(Profil.LONGUEUR_MAX_NOM), null, null, null, null)).nomComplet())
                .hasSize(Profil.LONGUEUR_MAX_NOM);
    }

    @Test
    void le_telephone_accepte_les_numeros_algeriens_mobiles_et_fixes() {
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", "0550123456", null, null, null)).telephone())
                .isEqualTo("0550123456");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", "021123456", null, null, null)).telephone())
                .isEqualTo("021123456");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", " 021 12 34 56 ", null, null, null)).telephone())
                .isEqualTo("021123456");
    }

    @Test
    void le_telephone_refuse_les_numeros_mal_formes() {
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "0550-12-34-56", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "+213550123456", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "550123456", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "05501234", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "05501234567", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", "abcdefghij", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
    }

    @Test
    void la_date_de_naissance_est_dans_le_passe_a_l_heure_d_algerie() {
        LocalDate hier = AUJOURD_HUI_EN_ALGERIE.minusDays(1);

        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, hier, null, null)).dateNaissance()).isEqualTo(hier);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, AUJOURD_HUI_EN_ALGERIE, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, AUJOURD_HUI_EN_ALGERIE.plusDays(1), null, null)))
                .isInstanceOf(ProfilInvalideException.class);
    }

    @Test
    void la_date_de_naissance_est_posterieure_a_1900() {
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, LocalDate.of(1900, 12, 31), null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, LocalDate.of(1850, 1, 1), null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, LocalDate.of(1901, 1, 1), null, null)).dateNaissance())
                .isEqualTo(LocalDate.of(1901, 1, 1));
    }

    @Test
    void le_code_de_wilaya_compte_au_plus_quatre_caracteres() {
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, " 1601 ", null)).wilayaCode()).isEqualTo("1601");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, "5", null)).wilayaCode()).isEqualTo("5");
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, null, "Alger", null)))
                .isInstanceOf(ProfilInvalideException.class);
    }

    @Test
    void la_langue_est_parmi_fr_ar_kab_en_et_vaut_fr_par_defaut() {
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, null, null)).langue()).isEqualTo("fr");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, null, "ar")).langue()).isEqualTo("ar");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, null, "kab")).langue()).isEqualTo("kab");
        assertThat(renseigne(new DemandeProfil("Amina Belkacem", null, null, null, " EN ")).langue()).isEqualTo("en");
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, null, null, "de")))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> renseigne(new DemandeProfil("Amina Belkacem", null, null, null, "francais")))
                .isInstanceOf(ProfilInvalideException.class);
    }

    @Test
    void renseigner_exige_un_utilisateur_et_une_demande() {
        assertThatThrownBy(() -> Profil.renseigner(null, DEMANDE, MIS_A_JOUR_LE))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> Profil.renseigner(UTILISATEUR, null, MIS_A_JOUR_LE))
                .isInstanceOf(ProfilInvalideException.class);
    }
}
