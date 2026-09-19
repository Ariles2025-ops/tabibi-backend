package dz.tabibi.backend.rappels.application;

import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cas d'usage des rappels : chaque rendez-vous confirme qui commence dans les 24 prochaines heures
 * vaut un rappel au patient (port Notifieur), envoye une seule fois : le rendez-vous est marque a
 * l'envoi et n'est plus retenu ensuite. L'heure courante vient de l'horloge injectee (Clock), ce
 * qui rend le cas d'usage testable a heure fixe.
 */
@Service
public class RappelService {

    /** Fenetre des rappels : les rendez-vous qui commencent d'ici 24 heures. */
    public static final Duration HORIZON = Duration.ofHours(24);

    private final RendezVousRepository rendezVous;
    private final Notifieur notifieur;
    private final Clock horloge;

    public RappelService(RendezVousRepository rendezVous, Notifieur notifieur, Clock horloge) {
        this.rendezVous = rendezVous;
        this.notifieur = notifieur;
        this.horloge = horloge;
    }

    /**
     * Envoie le rappel de chaque rendez-vous confirme, sans rappel deja envoye, qui commence entre
     * maintenant et maintenant + 24 h, et le marque ; renvoie le nombre de rappels envoyes.
     */
    @Transactional
    public int executer() {
        Instant maintenant = horloge.instant();
        List<RendezVous> aRappeler = rendezVous.confirmesSansRappelEntre(maintenant, maintenant.plus(HORIZON));
        for (RendezVous rdv : aRappeler) {
            notifieur.notifier(rdv.patientId(), "Rappel de rendez-vous",
                    "Votre rendez-vous du " + FormatDate.lisible(rdv.debut())
                            + " est demain. Pensez a vous presenter 10 minutes en avance.");
            rdv.marquerRappelEnvoye(maintenant);
            rendezVous.enregistrer(rdv);
        }
        return aRappeler.size();
    }
}
