package dz.tabibi.backend.listeattente.adapter;

import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entree REST de la liste d'attente : inscription, liste et retrait par le patient,
 * consultation de sa liste par le medecin. L'inscription passe par POST /api/medecins/{id}/liste-attente :
 * seul GET est public sur /api/medecins/** (SecurityConfig), ce POST exige un jeton PATIENT.
 * Les erreurs metier (deja inscrit, introuvable, acces refuse) sont traduites par GestionErreursApi.
 */
@RestController
public class ListeAttenteController {

    private final ListeAttenteService service;

    public ListeAttenteController(ListeAttenteService service) {
        this.service = service;
    }

    /** Vue d'une inscription telle que renvoyee par l'API (au patient comme au medecin). */
    public record InscriptionVue(UUID id, UUID patientId, UUID medecinId, Instant inscritLe) {
        static InscriptionVue de(InscriptionAttente i) {
            return new InscriptionVue(i.id(), i.patientId(), i.medecinId(), i.inscritLe());
        }
    }

    /** Inscription du patient connecte sur la liste d'attente d'un medecin (409 s'il y est deja). */
    @PostMapping("/api/medecins/{id}/liste-attente")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<InscriptionVue> inscrire(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        InscriptionAttente inscription = service.inscrire(identifiant(jwt), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(InscriptionVue.de(inscription));
    }

    /** Mes inscriptions, les plus anciennes d'abord. */
    @GetMapping("/api/liste-attente/mes")
    @PreAuthorize("hasRole('PATIENT')")
    public List<InscriptionVue> mesInscriptions(@AuthenticationPrincipal Jwt jwt) {
        return service.mesInscriptions(identifiant(jwt)).stream().map(InscriptionVue::de).toList();
    }

    /** Retrait d'une de mes inscriptions (204 sans corps ; 404 si inconnue, 403 si elle est a un autre patient). */
    @PostMapping("/api/liste-attente/{id}/retirer")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> retirer(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.retirer(identifiant(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** Liste d'attente du medecin connecte, les plus anciens inscrits d'abord. */
    @GetMapping("/api/medecin/liste-attente")
    @PreAuthorize("hasRole('MEDECIN')")
    public List<InscriptionVue> listeDuMedecin(@AuthenticationPrincipal Jwt jwt) {
        return service.listeDuMedecin(identifiant(jwt)).stream().map(InscriptionVue::de).toList();
    }

    /** Le sujet du jeton Keycloak est l'identifiant de l'utilisateur, patient ou medecin. */
    private static UUID identifiant(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
