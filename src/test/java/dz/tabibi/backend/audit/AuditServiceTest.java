package dz.tabibi.backend.audit;

import dz.tabibi.backend.audit.adapter.EnMemoireAuditRepository;
import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.AuditRepository;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID MEDECIN = UUID.randomUUID();

    private final AuditRepository repository = new EnMemoireAuditRepository();
    private final AuditService service = new AuditService(repository);

    private EntreeAudit acces(UUID sujet, String chemin, String date) {
        return service.enregistrer(EntreeAudit.nouvelle(sujet, "GET", chemin, 200, "192.168.1.0", Instant.parse(date), 12));
    }

    @Test
    void une_entree_nouvelle_recoit_un_identifiant_et_sait_si_elle_est_anonyme() {
        EntreeAudit anonyme = EntreeAudit.nouvelle(null, "GET", "/api/medecins", 200, null, Instant.parse("2026-03-01T09:00:00Z"), 3);
        EntreeAudit identifiee = EntreeAudit.nouvelle(PATIENT, "POST", "/api/rendezvous", 201, "10.0.0.0", Instant.parse("2026-03-01T09:00:00Z"), 40);

        assertThat(anonyme.id()).isNotNull();
        assertThat(anonyme.id()).isNotEqualTo(identifiee.id());
        assertThat(anonyme.anonyme()).isTrue();
        assertThat(identifiee.anonyme()).isFalse();
        assertThat(identifiee.sujet()).isEqualTo(PATIENT);
        assertThat(identifiee.statut()).isEqualTo(201);
        assertThat(identifiee.dureeMs()).isEqualTo(40L);
    }

    @Test
    void une_entree_exige_methode_chemin_et_horodatage() {
        Instant date = Instant.parse("2026-03-01T09:00:00Z");
        assertThatThrownBy(() -> new EntreeAudit(UUID.randomUUID(), null, null, "/api/moi", 200, null, date, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntreeAudit(UUID.randomUUID(), null, "GET", null, 200, null, date, 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntreeAudit(UUID.randomUUID(), null, "GET", "/api/moi", 200, null, null, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recents_liste_les_acces_les_plus_recents_d_abord() {
        EntreeAudit ancien = acces(PATIENT, "/api/moi", "2026-01-10T09:00:00Z");
        EntreeAudit recent = acces(MEDECIN, "/api/medecin/rendezvous", "2026-03-01T09:00:00Z");
        EntreeAudit milieu = acces(null, "/api/medecins", "2026-02-01T09:00:00Z");

        assertThat(service.recents(100)).containsExactly(recent, milieu, ancien);
    }

    @Test
    void a_date_egale_le_dernier_enregistre_vient_en_premier() {
        EntreeAudit premier = acces(PATIENT, "/api/moi", "2026-03-01T09:00:00Z");
        EntreeAudit second = acces(PATIENT, "/api/rendezvous/mes", "2026-03-01T09:00:00Z");

        assertThat(service.recents(100)).containsExactly(second, premier);
    }

    @Test
    void recents_respecte_la_limite() {
        acces(PATIENT, "/api/moi", "2026-01-10T09:00:00Z");
        EntreeAudit deuxieme = acces(PATIENT, "/api/moi", "2026-01-11T09:00:00Z");
        EntreeAudit troisieme = acces(PATIENT, "/api/moi", "2026-01-12T09:00:00Z");

        assertThat(service.recents(2)).containsExactly(troisieme, deuxieme);
        assertThat(service.recents(1)).containsExactly(troisieme);
    }

    @Test
    void par_sujet_ne_liste_que_les_acces_de_l_utilisateur_les_plus_recents_d_abord() {
        EntreeAudit ancien = acces(PATIENT, "/api/moi", "2026-01-10T09:00:00Z");
        acces(MEDECIN, "/api/medecin/rendezvous", "2026-01-11T09:00:00Z");
        acces(null, "/api/medecins", "2026-01-12T09:00:00Z");
        EntreeAudit recent = acces(PATIENT, "/api/rendezvous/mes", "2026-01-13T09:00:00Z");

        assertThat(service.parSujet(PATIENT, 100)).containsExactly(recent, ancien);
        assertThat(service.parSujet(PATIENT, 1)).containsExactly(recent);
        assertThat(service.parSujet(UUID.randomUUID(), 100)).isEmpty();
    }

    @Test
    void la_limite_est_ramenee_entre_1_et_1000() {
        assertThat(AuditService.borner(0)).isEqualTo(1);
        assertThat(AuditService.borner(-5)).isEqualTo(1);
        assertThat(AuditService.borner(100)).isEqualTo(100);
        assertThat(AuditService.borner(1000)).isEqualTo(1000);
        assertThat(AuditService.borner(5000)).isEqualTo(AuditService.LIMITE_MAX);

        acces(PATIENT, "/api/moi", "2026-01-10T09:00:00Z");
        acces(PATIENT, "/api/moi", "2026-01-11T09:00:00Z");
        assertThat(service.recents(0)).hasSize(1);
        assertThat(service.parSujet(PATIENT, -1)).hasSize(1);
    }

    @Test
    void le_journal_en_memoire_est_borne_et_oublie_les_plus_anciennes_entrees() {
        EnMemoireAuditRepository borne = new EnMemoireAuditRepository(3);
        List<EntreeAudit> entrees = IntStream.range(0, 5)
                .mapToObj(i -> borne.enregistrer(EntreeAudit.nouvelle(PATIENT, "GET", "/api/moi/" + i, 200, null,
                        Instant.parse("2026-01-10T09:00:00Z").plusSeconds(i), i)))
                .toList();

        assertThat(borne.recents(100)).containsExactly(entrees.get(4), entrees.get(3), entrees.get(2));
        assertThat(borne.parSujet(PATIENT, 100)).hasSize(3);
    }

    @Test
    void la_capacite_par_defaut_est_de_dix_mille_entrees_et_doit_etre_positive() {
        assertThat(EnMemoireAuditRepository.CAPACITE_DEFAUT).isEqualTo(10_000);
        assertThatThrownBy(() -> new EnMemoireAuditRepository(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
