package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduit les erreurs metier en reponses HTTP pour tous les controleurs,
 * avec un corps uniforme : { "erreur": "..." }.
 * Les refus de Spring Security (401/403 par role) ne passent pas ici.
 */
@RestControllerAdvice
public class GestionErreursApi {

    /** Corps de toute reponse d'erreur metier. */
    public record ErreurApi(String erreur) {}

    @ExceptionHandler({CreneauIntrouvableException.class, RendezVousIntrouvableException.class})
    public ResponseEntity<ErreurApi> introuvable(RuntimeException ex) {
        return reponse(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(CreneauDejaReserveException.class)
    public ResponseEntity<ErreurApi> creneauPris(CreneauDejaReserveException ex) {
        return reponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(AccesRefuseException.class)
    public ResponseEntity<ErreurApi> accesRefuse(AccesRefuseException ex) {
        return reponse(HttpStatus.FORBIDDEN, ex);
    }

    private static ResponseEntity<ErreurApi> reponse(HttpStatus statut, RuntimeException ex) {
        return ResponseEntity.status(statut).body(new ErreurApi(ex.getMessage()));
    }
}
