package dz.tabibi.backend.rappels.application;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.commun.domain.Courriel;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
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
import java.util.Optional;
import java.util.UUID;

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

    private Courriel courriel;
    private ProfilRepository profils;
    private int smsEnvoyes;
    private int courrielsEnvoyes;

    /** Rappel aussi par e-mail quand le compte du patient a une adresse (si un serveur SMTP est configure). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCourriel(Courriel courriel) {
        this.courriel = courriel;
    }

    /** Profils : repli sur le telephone saisi dans le profil quand le compte n'a pas de numero. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

    /**
     * Envoie le rappel de chaque rendez-vous confirme, sans rappel deja envoye, qui commence entre
     * maintenant et maintenant + 24 h, et le marque ; renvoie le nombre de rappels envoyes.
     */
    @Transactional
    public int executer() {
        Instant maintenant = horloge.instant();
        smsEnvoyes = 0;
        courrielsEnvoyes = 0;
        List<RendezVous> aRappeler = rendezVous.confirmesSansRappelEntre(maintenant, maintenant.plus(HORIZON));
        for (RendezVous rdv : aRappeler) {
            if (rdv.patientId() == null) {
                continue; // rendez-vous pris par telephone : aucun compte, aucun envoi
            }
            notifieur.notifier(rdv.patientId(), "Rappel de rendez-vous",
                    "Rappel : votre rendez-vous a lieu le " + FormatDate.lisible(rdv.debut())
                            + ". Pensez à vous présenter 10 minutes en avance.", "/mes-rendez-vous");
            envoyerHorsApplication(rdv);
            rdv.marquerRappelEnvoye(maintenant);
            rendezVous.enregistrer(rdv);
        }
        int total = (int) aRappeler.stream().filter(r -> r.patientId() != null).count();
        if (total > 0) {
            log.info("Rappels : {} rendez-vous rappelés, dont {} par SMS et {} par e-mail.", total, smsEnvoyes, courrielsEnvoyes);
        }
        return total;
    }

    /** SMS et e-mail de rappel ; un echec (coordonnee absente, passerelle) n'empeche jamais la notification ni les autres rappels. */
    private void envoyerHorsApplication(RendezVous rdv) {
        boolean smsPossible = sms != null && comptes != null && sms.disponible();
        boolean courrielPossible = courriel != null && courriel.disponible();
        if (!smsPossible && !courrielPossible) {
            return;
        }
        Optional<ComptesUtilisateurs.InfosCompte> compte = Optional.empty();
        try {
            if (comptes != null) {
                compte = comptes.infos(rdv.patientId());
            }
        } catch (RuntimeException e) {
            log.warn("Compte du patient introuvable pour le rappel du rendez-vous {} : {}", rdv.id(), e.getMessage());
        }
        String date = FormatDate.lisible(rdv.debut());
        if (smsPossible) {
            envoyerSms(rdv.id(), telephone(rdv.patientId(), compte), date);
        }
        String email = compte.map(ComptesUtilisateurs.InfosCompte::email).filter(e -> e != null && !e.isBlank()).orElse(null);
        if (courrielPossible && email != null) {
            try {
                courriel.envoyer(email, "Ndawik : rappel de votre rendez-vous",
                        "Rappel : votre rendez-vous a lieu le " + date
                                + ". Pensez à vous présenter 10 minutes en avance.\n"
                                + "Pour annuler : tabibi.doctor/mes-rendez-vous");
                courrielsEnvoyes++;
            } catch (RuntimeException e) {
                log.warn("Rappel e-mail non envoye pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
            }
        }
    }

    /** Numero verifie du compte, sinon numero saisi dans le profil ; normalise, ou null. */
    private String telephone(UUID patientId, Optional<ComptesUtilisateurs.InfosCompte> compte) {
        String tel = compte.map(ComptesUtilisateurs.InfosCompte::telephone).map(EnvoiSms::normaliser).orElse(null);
        if (tel == null && profils != null) {
            try {
                tel = profils.parUtilisateur(patientId).map(Profil::telephone).map(EnvoiSms::normaliser).orElse(null);
            } catch (RuntimeException e) {
                log.warn("Profil illisible pour le rappel du patient {} : {}", patientId, e.getMessage());
            }
        }
        return tel;
    }

    private void envoyerSms(UUID rdvId, String tel, String date) {
        if (tel == null) {
            return;
        }
        try {
            sms.envoyer(tel, "Ndawik : rappel de votre rendez-vous le " + date
                    + ". Pour annuler : tabibi.doctor/mes-rendez-vous");
            smsEnvoyes++;
        } catch (RuntimeException e) {
            log.warn("Rappel SMS non envoye pour le rendez-vous {} : {}", rdvId, e.getMessage());
        }
    }
}
