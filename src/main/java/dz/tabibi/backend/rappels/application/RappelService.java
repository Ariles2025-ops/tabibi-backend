package dz.tabibi.backend.rappels.application;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.rappels.domain.EnvoiSms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RappelService.class);
    private EnvoiSms sms;
    private ComptesUtilisateurs comptes;

    /** Rappel aussi par SMS, au numero verifie du compte du patient (si une passerelle est configuree). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSms(EnvoiSms sms, ComptesUtilisateurs comptes) {
        this.sms = sms;
        this.comptes = comptes;
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
            if (rdv.patientId() == null) {
                continue; // rendez-vous pris par telephone : aucun compte, aucun envoi
            }
            notifieur.notifier(rdv.patientId(), "Rappel de rendez-vous",
                    "Rappel : votre rendez-vous a lieu le " + FormatDate.lisible(rdv.debut())
                            + ". Pensez à vous présenter 10 minutes en avance.", "/mes-rendez-vous");
            envoyerSms(rdv);
            rdv.marquerRappelEnvoye(maintenant);
            rendezVous.enregistrer(rdv);
        }
        return (int) aRappeler.stream().filter(r -> r.patientId() != null).count();
    }

    /** SMS de rappel ; un echec (numero absent, passerelle) n'empeche jamais la notification ni les autres rappels. */
    private void envoyerSms(RendezVous rdv) {
        if (sms == null || comptes == null || !sms.disponible()) {
            return;
        }
        try {
            String tel = comptes.infos(rdv.patientId()).map(ComptesUtilisateurs.InfosCompte::telephone)
                    .map(EnvoiSms::normaliser).orElse(null);
            if (tel == null) {
                return;
            }
            sms.envoyer(tel, "Ndawik : rappel de votre rendez-vous le " + FormatDate.lisible(rdv.debut())
                    + ". Pour annuler : tabibi.doctor/mes-rendez-vous");
        } catch (RuntimeException e) {
            log.warn("Rappel SMS non envoye pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
        }
    }
}
