package dz.tabibi.backend.listeattente.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Port par lequel les autres modules (creneaux, rendez-vous) signalent qu'un creneau vient de se
 * liberer chez un medecin : ouverture d'un creneau, annulation d'un rendez-vous qui remet son
 * creneau a disposition. La liste d'attente de ce medecin en est alertee sans que ces modules
 * la connaissent.
 */
public interface AlerteCreneau {

    void creneauLibere(UUID medecinId, Instant debut);

    /**
     * Le patient vient d'obtenir un rendez-vous chez ce medecin : il n'a plus besoin d'etre alerte
     * (sa place sur la liste d'attente est retiree). Sans effet par defaut.
     */
    default void patientAReserve(UUID patientId, UUID medecinId) {
    }
}
