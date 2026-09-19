package dz.tabibi.backend.profil;

import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.application.ProfilService;
import dz.tabibi.backend.profil.domain.DemandeProfil;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilInvalideException;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilServiceTest {

    private static final UUID UTILISATEUR = UUID.randomUUID();
    private static final DemandeProfil DEMANDE =
            new DemandeProfil("Amina Belkacem", "0550123456", LocalDate.of(1990, 5, 20), "16", "fr");

    private final ProfilRepository repository = new EnMemoireProfilRepository();
    private final ProfilService service = new ProfilService(repository);

    @Test
    void mon_profil_est_vide_tant_qu_il_n_est_pas_renseigne() {
        assertThat(service.monProfil(UTILISATEUR)).isEmpty();
    }

    @Test
    void enregistrer_cree_le_profil_date_de_maintenant() {
        Instant avant = Instant.now();

        Profil p = service.enregistrer(UTILISATEUR, DEMANDE);

        assertThat(p.utilisateurId()).isEqualTo(UTILISATEUR);
        assertThat(p.nomComplet()).isEqualTo("Amina Belkacem");
        assertThat(p.telephone()).isEqualTo("0550123456");
        assertThat(p.dateNaissance()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(p.wilayaCode()).isEqualTo("16");
        assertThat(p.langue()).isEqualTo("fr");
        assertThat(p.misAJourLe()).isAfterOrEqualTo(avant);
        assertThat(service.monProfil(UTILISATEUR)).contains(p);
        assertThat(repository.parUtilisateur(UTILISATEUR)).contains(p);
    }

    @Test
    void enregistrer_a_nouveau_remplace_le_profil_sans_en_creer_un_second() throws InterruptedException {
        Profil initial = service.enregistrer(UTILISATEUR, DEMANDE);
        Thread.sleep(2);

        Profil remplace = service.enregistrer(UTILISATEUR,
                new DemandeProfil("Amina Belkacem-Haddad", null, null, "31", "kab"));

        assertThat(remplace.utilisateurId()).isEqualTo(UTILISATEUR);
        assertThat(remplace.nomComplet()).isEqualTo("Amina Belkacem-Haddad");
        assertThat(remplace.telephone()).isNull();
        assertThat(remplace.dateNaissance()).isNull();
        assertThat(remplace.wilayaCode()).isEqualTo("31");
        assertThat(remplace.langue()).isEqualTo("kab");
        assertThat(remplace.misAJourLe()).isAfter(initial.misAJourLe());
        assertThat(service.monProfil(UTILISATEUR)).contains(remplace);
    }

    @Test
    void chaque_utilisateur_a_son_propre_profil() {
        UUID autre = UUID.randomUUID();
        Profil mien = service.enregistrer(UTILISATEUR, DEMANDE);
        Profil sien = service.enregistrer(autre, new DemandeProfil("Karim Haddad", null, null, null, "ar"));

        assertThat(service.monProfil(UTILISATEUR)).contains(mien);
        assertThat(service.monProfil(autre)).contains(sien);
        assertThat(service.monProfil(UUID.randomUUID())).isEmpty();
    }

    @Test
    void un_profil_invalide_est_refuse_sans_toucher_au_profil_existant() {
        Profil initial = service.enregistrer(UTILISATEUR, DEMANDE);

        assertThatThrownBy(() -> service.enregistrer(UTILISATEUR, new DemandeProfil(" ", null, null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> service.enregistrer(UTILISATEUR,
                new DemandeProfil("Amina Belkacem", "12345", null, null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> service.enregistrer(UTILISATEUR,
                new DemandeProfil("Amina Belkacem", null, LocalDate.now(FormatDate.FUSEAU).plusDays(1), null, null)))
                .isInstanceOf(ProfilInvalideException.class);
        assertThatThrownBy(() -> service.enregistrer(UTILISATEUR,
                new DemandeProfil("Amina Belkacem", null, null, null, "de")))
                .isInstanceOf(ProfilInvalideException.class);
        assertThat(service.monProfil(UTILISATEUR)).contains(initial);
        assertThat(service.monProfil(UUID.randomUUID())).isEmpty();
    }
}
