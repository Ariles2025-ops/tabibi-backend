package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.administration.domain.CandidatureIntrouvableException;
import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.messagerie.domain.ConversationIntrouvableException;
import dz.tabibi.backend.messagerie.domain.MessageInvalideException;
import dz.tabibi.backend.notifications.domain.NotificationIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.rendezvous.domain.CreneauDejaReserveException;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.teleconsultation.domain.TeleconsultationIntrouvableException;
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

    @ExceptionHandler({
            CreneauIntrouvableException.class,
            RendezVousIntrouvableException.class,
            OrdonnanceIntrouvableException.class,
            NotificationIntrouvableException.class,
            TeleconsultationIntrouvableException.class,
            CandidatureIntrouvableException.class,
            ConversationIntrouvableException.class})
    public ResponseEntity<ErreurApi> introuvable(RuntimeException ex) {
        return reponse(HttpStatus.NOT_FOUND, ex);
    }

    /** Contenu refuse par une regle metier (ordonnance sans ligne, creneau passe, candidature incomplete, message vide...). */
    @ExceptionHandler({
            OrdonnanceInvalideException.class,
            CreneauInvalideException.class,
            CandidatureInvalideException.class,
            MessageInvalideException.class})
    public ResponseEntity<ErreurApi> invalide(RuntimeException ex) {
        return reponse(HttpStatus.BAD_REQUEST, ex);
    }

    /** Conflit avec l'etat courant (creneau deja pris, transition de statut interdite). */
    @ExceptionHandler({CreneauDejaReserveException.class, TransitionInvalideException.class})
    public ResponseEntity<ErreurApi> conflit(RuntimeException ex) {
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
