package dz.tabibi.backend.dawini;

import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.dawini.domain.ReponseInvalideException;
import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regles du domaine : nom de pharmacie et disponibilite obligatoires, prix positif ou nul, commentaire borne. */
class ReponsePharmacieTest {

    private static final UUID BESOIN = UUID.randomUUID();
    private static final UUID PHARMACIE = UUID.randomUUID();
    private static final Instant REPONDUE_LE = Instant.parse("2026-09-18T11:00:00Z");

    private static ReponsePharmacie repondre(DemandeReponse demande) {
        return ReponsePharmacie.repondre(BESOIN, PHARMACIE, demande, REPONDUE_LE);
    }

    @Test
    void repondre_cree_une_reponse_complete() {
        ReponsePharmacie r = repondre(new DemandeReponse(" Pharmacie El Amel ", true, 850, " En stock ce matin. "));

        assertThat(r.id()).isNotNull();
        assertThat(r.besoinId()).isEqualTo(BESOIN);
        assertThat(r.pharmacieId()).isEqualTo(PHARMACIE);
        assertThat(r.nomPharmacie()).isEqualTo("Pharmacie El Amel");
        assertThat(r.disponible()).isTrue();
        assertThat(r.prixDa()).isEqualTo(850);
        assertThat(r.commentaire()).isEqualTo("En stock ce matin.");
        assertThat(r.repondueLe()).isEqualTo(REPONDUE_LE);
    }

    @Test
    void le_prix_et_le_commentaire_sont_facultatifs() {
        ReponsePharmacie r = repondre(new DemandeReponse("Pharmacie El Amel", false, null, "  "));

        assertThat(r.disponible()).isFalse();
        assertThat(r.prixDa()).isNull();
        assertThat(r.commentaire()).isNull();
        assertThat(repondre(new DemandeReponse("Pharmacie El Amel", true, 0, null)).prixDa()).isEqualTo(0);
    }

    @Test
    void repondre_exige_le_nom_de_la_pharmacie_et_la_disponibilite() {
        assertThatThrownBy(() -> repondre(new DemandeReponse(" ", true, null, null)))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> repondre(new DemandeReponse(null, true, null, null)))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> repondre(new DemandeReponse("Pharmacie El Amel", null, null, null)))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> repondre(null)).isInstanceOf(ReponseInvalideException.class);
    }

    @Test
    void repondre_refuse_un_prix_negatif() {
        assertThatThrownBy(() -> repondre(new DemandeReponse("Pharmacie El Amel", true, -1, null)))
                .isInstanceOf(ReponseInvalideException.class);
    }

    @Test
    void repondre_borne_le_commentaire_et_le_nom() {
        String commentaire500 = "a".repeat(ReponsePharmacie.LONGUEUR_MAX_COMMENTAIRE);

        assertThat(repondre(new DemandeReponse("Pharmacie El Amel", true, null, commentaire500)).commentaire()).hasSize(500);
        assertThatThrownBy(() -> repondre(new DemandeReponse("Pharmacie El Amel", true, null, commentaire500 + "a")))
                .isInstanceOf(ReponseInvalideException.class);
        assertThatThrownBy(() -> repondre(new DemandeReponse("n".repeat(161), true, null, null)))
                .isInstanceOf(ReponseInvalideException.class);
    }
}
