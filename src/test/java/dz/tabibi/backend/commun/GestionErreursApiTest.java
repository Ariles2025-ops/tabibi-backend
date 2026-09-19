package dz.tabibi.backend.commun;

import dz.tabibi.backend.commun.adapter.ContexteLangue;
import dz.tabibi.backend.commun.adapter.GestionErreursApi;
import dz.tabibi.backend.commun.adapter.Messages;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.Langue;
import dz.tabibi.backend.profil.domain.ProfilInvalideException;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le conseil d'erreurs rend le message dans la langue de la requete des que l'erreur porte une
 * cle ; sans cle, il renvoie le message brut. Le francais rendu doit etre exactement le message
 * brut de l'exception : les deux textes ne peuvent pas diverger sans que ce test le voie.
 */
class GestionErreursApiTest {

    private static final UUID RENDEZ_VOUS = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final Messages messages = Messages.partagees();
    private final GestionErreursApi gestion = new GestionErreursApi(messages);

    @AfterEach
    void rendreLaLangue() {
        ContexteLangue.effacer();
    }

    @Test
    void traduit_un_acces_refuse_dans_la_langue_de_la_requete() {
        AccesRefuseException ex =
                new AccesRefuseException("Ce rendez-vous ne vous appartient pas.", Cles.RENDEZVOUS_AUTRE_PATIENT);

        assertThat(gestion.texte(ex, Langue.AR)).isEqualTo("هذا الموعد لا يخصك.");
        assertThat(gestion.texte(ex, Langue.EN)).isEqualTo("This appointment does not belong to you.");
        assertThat(gestion.texte(ex, Langue.FR)).isEqualTo("Ce rendez-vous ne vous appartient pas.");
    }

    @Test
    void la_reponse_403_suit_la_langue_posee_par_le_filtre() {
        AccesRefuseException ex =
                new AccesRefuseException("Ce rendez-vous ne vous appartient pas.", Cles.RENDEZVOUS_AUTRE_PATIENT);
        ContexteLangue.poser(Langue.AR);

        ResponseEntity<GestionErreursApi.ErreurApi> reponse = gestion.accesRefuse(ex);

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reponse.getBody().erreur()).isEqualTo("هذا الموعد لا يخصك.");
    }

    @Test
    void sans_langue_posee_la_reponse_est_en_francais() {
        AccesRefuseException ex =
                new AccesRefuseException("Ce rendez-vous ne vous appartient pas.", Cles.RENDEZVOUS_AUTRE_PATIENT);

        assertThat(gestion.accesRefuse(ex).getBody().erreur()).isEqualTo("Ce rendez-vous ne vous appartient pas.");
    }

    @Test
    void les_parametres_de_l_erreur_sont_rendus_dans_la_traduction() {
        RendezVousIntrouvableException ex = new RendezVousIntrouvableException(RENDEZ_VOUS);

        assertThat(gestion.texte(ex, Langue.EN)).isEqualTo("Appointment not found: " + RENDEZ_VOUS + ".");
        assertThat(gestion.texte(ex, Langue.AR)).contains(RENDEZ_VOUS.toString());
        assertThat(gestion.texte(ex, Langue.FR)).isEqualTo(ex.getMessage());
    }

    @Test
    void une_erreur_sans_cle_garde_son_message_brut_quelle_que_soit_la_langue() {
        ProfilInvalideException ex = new ProfilInvalideException("Message redige a la main.");

        assertThat(ex.cle()).isNull();
        assertThat(ex.estTraduisible()).isFalse();
        assertThat(gestion.texte(ex, Langue.AR)).isEqualTo("Message redige a la main.");
        assertThat(gestion.texte(ex, Langue.FR)).isEqualTo("Message redige a la main.");
    }

    @Test
    void le_francais_rendu_est_toujours_le_message_brut_de_l_exception() {
        for (RuntimeException ex : new RuntimeException[] {
                new RendezVousIntrouvableException(RENDEZ_VOUS),
                new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.", Cles.RENDEZVOUS_AUTRE_AGENDA),
                new ProfilInvalideException("Le nom complet est obligatoire.", Cles.PROFIL_NOM_OBLIGATOIRE),
                new ProfilInvalideException("Le code de wilaya ne peut pas depasser 4 caracteres.",
                        Cles.PROFIL_WILAYA_LONGUEUR, 4)}) {
            assertThat(gestion.texte(ex, Langue.FR)).as(ex.getMessage()).isEqualTo(ex.getMessage());
        }
    }
}
