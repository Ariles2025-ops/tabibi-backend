package dz.tabibi.backend.profil.adapter;

import dz.tabibi.backend.cabinet.domain.RattachementRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.profil.application.PatientsService;
import dz.tabibi.backend.profil.application.PatientsService.PatientResume;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Noms des patients pour le cabinet (medecin, secretaire rattachee) et l'administration. */
@RestController
public class PatientsController {

    private final PatientsService service;
    private final RattachementRepository rattachements;

    public PatientsController(PatientsService service, RattachementRepository rattachements) {
        this.service = service;
        this.rattachements = rattachements;
    }

    @GetMapping("/api/medecin/patients")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<PatientResume> mesPatients(@AuthenticationPrincipal Jwt jwt) {
        return service.patientsDuMedecin(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/api/secretaire/medecins/{medecinId}/patients")
    @PreAuthorize("hasRole('SECRETAIRE')")
    public List<PatientResume> patientsDuCabinet(@PathVariable UUID medecinId, @AuthenticationPrincipal Jwt jwt) {
        if (rattachements.parMedecinEtSecretaire(medecinId, UUID.fromString(jwt.getSubject())).isEmpty()) {
            throw new AccesRefuseException("Vous n'êtes pas rattachée au cabinet de ce médecin.");
        }
        return service.patientsDuMedecin(medecinId);
    }

    @GetMapping("/api/admin/patients")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PatientResume> patients(@RequestParam List<UUID> ids) {
        return service.resumes(ids);
    }
}
