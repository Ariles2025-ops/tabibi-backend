package dz.tabibi.backend.profil;

import dz.tabibi.backend.commun.FauxComptes;
import dz.tabibi.backend.listeattente.adapter.EnMemoireListeAttenteRepository;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository;
import dz.tabibi.backend.profil.application.PatientsService;
import dz.tabibi.backend.profil.application.PatientsService.PatientResume;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Loi 18-07 : le cabinet ne voit les coordonnees que des patients qui ont eu un rendez-vous avec lui. */
class PatientsServiceTest {

    private static final UUID MEDECIN = UUID.randomUUID();
    private static final UUID CONSULTE = UUID.randomUUID();
    private static final UUID EN_ATTENTE = UUID.randomUUID();

    private final EnMemoireRendezVousRepository rdvs = new EnMemoireRendezVousRepository();
    private final EnMemoireListeAttenteRepository attente = new EnMemoireListeAttenteRepository();
    private final FauxComptes comptes = new FauxComptes()
            .avec(CONSULTE, "Amina Belkacem", "amina@example.dz", "0550123456")
            .avec(EN_ATTENTE, "Karim Haddad", "karim@example.dz", "0660123456");
    private final PatientsService service = new PatientsService(rdvs, attente, new EnMemoireProfilRepository(), comptes);

    @Test
    void coordonnees_seulement_pour_les_patients_avec_rendez_vous() {
        rdvs.enregistrer(RendezVous.confirmer(CONSULTE, MEDECIN, Instant.now().plusSeconds(3600)));
        attente.enregistrer(InscriptionAttente.inscrire(CONSULTE, MEDECIN, Instant.now()));
        attente.enregistrer(InscriptionAttente.inscrire(EN_ATTENTE, MEDECIN, Instant.now()));

        var patients = service.patientsDuMedecin(MEDECIN);

        assertThat(patients).hasSize(2);
        assertThat(patients).filteredOn(p -> p.patientId().equals(CONSULTE)).singleElement()
                .isEqualTo(new PatientResume(CONSULTE, "Amina Belkacem", "0550123456", "amina@example.dz"));
        assertThat(patients).filteredOn(p -> p.patientId().equals(EN_ATTENTE)).singleElement()
                .isEqualTo(new PatientResume(EN_ATTENTE, "Karim Haddad", null, null));
    }
}
