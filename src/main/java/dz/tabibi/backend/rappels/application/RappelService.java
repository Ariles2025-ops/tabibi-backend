package dz.tabibi.backend.rappels.application;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.commun.domain.Courriel;
import dz.tabibi.backend.commun.domain.FormatDate;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rappels.domain.EnvoiSms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;

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

    /** Nombre maximal d'envois SMS refuses par la passerelle avant d'abandonner un rendez-vous. */
    public static final int MAX_ECHECS_SMS = 5;

    private int smsEnEchec;

    /**
     * Envoie le rappel de chaque rendez-vous confirme, sans rappel deja envoye, qui commence entre
     * maintenant et maintenant + 24 h ; renvoie le nombre de rappels envoyes. Pas de transaction globale :
     * chaque rendez-vous est marque et enregistre seul AVANT l'envoi (un echec d'enregistrement n'annule pas
     * les autres, et un SMS parti n'est jamais renvoye). Un SMS refuse par la passerelle est repris aux
     * passages suivants (jusqu'a {@link #MAX_ECHECS_SMS} echecs) tant que le rendez-vous est a venir.
     */
    public int executer() {
        Instant maintenant = horloge.instant();
        smsEnvoyes = 0;
        courrielsEnvoyes = 0;
        smsEnEchec = 0;
        Instant fin = maintenant.plus(HORIZON);
        int total = 0;
        // Reprises d'abord : un SMS qui vient d'echouer dans ce passage n'est pas retente aussitot.
        int repris = reprendreSmsEnEchec(maintenant, fin);
        for (RendezVous rdv : rendezVous.confirmesSansRappelEntre(maintenant, fin)) {
            if (rdv.patientId() == null) {
                continue; // rendez-vous pris par telephone : aucun compte, aucun envoi
            }
            try {
                rdv.marquerRappelEnvoye(maintenant);
                rendezVous.enregistrer(rdv); // transaction courte, validee avant tout envoi
            } catch (RuntimeException e) {
                log.warn("Rappel non enregistre pour le rendez-vous {} (reessai au prochain passage) : {}", rdv.id(), e.getMessage());
                continue;
            }
            total++;
            try {
                String langue = Messages.langueDe(profils, rdv.patientId());
                notifieur.notifier(rdv.patientId(), Messages.rappelSujet(langue),
                        Messages.rappelMessage(langue, FormatDate.lisible(rdv.debut(), langue)), "/mes-rendez-vous");
            } catch (RuntimeException e) {
                log.warn("Notification de rappel non envoyee pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
            }
            try {
                envoyerHorsApplication(rdv);
            } catch (RuntimeException e) {
                log.warn("Rappel hors application en erreur pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
            }
        }
        if (total > 0) {
            log.info("Rappels : {} rendez-vous rappelés, dont {} par SMS et {} par e-mail.", total, smsEnvoyes, courrielsEnvoyes);
        }
        if (smsEnEchec > 0) {
            log.warn("Rappels : {} SMS en échec ce passage (dont {} repris avec succès) ; reprise au prochain passage, abandon après {} échecs.",
                    smsEnEchec, repris, MAX_ECHECS_SMS);
        }
        return total;
    }

    /** Reprend les SMS refuses (rendez-vous a venir, moins de MAX_ECHECS_SMS echecs) ; renvoie le nombre reussis. */
    private int reprendreSmsEnEchec(Instant maintenant, Instant fin) {
        if (sms == null || comptes == null || !sms.disponible()) {
            return 0;
        }
        int reussis = 0;
        for (RendezVous rdv : rendezVous.confirmesAvecSmsAReprendre(maintenant, fin, MAX_ECHECS_SMS)) {
            if (rdv.patientId() == null) {
                continue;
            }
            try {
                String tel = telephone(rdv.patientId(), infos(rdv));
                if (tel == null) {
                    rdv.marquerSmsEnvoye(); // plus de numero : rien a reprendre
                } else {
                    String langueSms = Messages.langueDe(profils, rdv.patientId());
                    sms.envoyer(tel, Messages.rappelSms(langueSms, FormatDate.lisible(rdv.debut(), langueSms)));
                    smsEnvoyes++;
                    reussis++;
                    rdv.marquerSmsEnvoye();
                }
            } catch (RuntimeException e) {
                smsEnEchec++;
                rdv.marquerSmsEnEchec();
                log.warn("Reprise du SMS de rappel en échec pour le rendez-vous {} (échec {}/{}) : {}",
                        rdv.id(), rdv.smsEchecs(), MAX_ECHECS_SMS, e.getMessage());
            }
            try {
                rendezVous.enregistrer(rdv);
            } catch (RuntimeException e) {
                log.warn("Etat du SMS non enregistre pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
            }
        }
        return reussis;
    }

    private Optional<ComptesUtilisateurs.InfosCompte> infos(RendezVous rdv) {
        try {
            if (comptes != null) {
                return comptes.infos(rdv.patientId());
            }
        } catch (RuntimeException e) {
            log.warn("Compte du patient introuvable pour le rappel du rendez-vous {} : {}", rdv.id(), e.getMessage());
        }
        return Optional.empty();
    }

    /** SMS et e-mail de rappel ; un echec (coordonnee absente, passerelle) n'empeche jamais la notification ni les autres rappels. */
    private void envoyerHorsApplication(RendezVous rdv) {
        boolean smsPossible = sms != null && comptes != null && sms.disponible();
        boolean courrielPossible = courriel != null && courriel.disponible();
        if (!smsPossible && !courrielPossible) {
            return;
        }
        Optional<ComptesUtilisateurs.InfosCompte> compte = infos(rdv);
        String langue = Messages.langueDe(profils, rdv.patientId());
        String date = FormatDate.lisible(rdv.debut(), langue);
        if (smsPossible) {
            envoyerSms(rdv, telephone(rdv.patientId(), compte), date, langue);
        }
        String email = compte.map(ComptesUtilisateurs.InfosCompte::email).filter(e -> e != null && !e.isBlank()).orElse(null);
        if (courrielPossible && email != null) {
            try {
                courriel.envoyer(email, Messages.rappelCourrielSujet(langue),
                        Messages.rappelCourrielCorps(langue, date));
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

    /** Envoie le SMS ; s'il est refuse, le rendez-vous est marque « SMS en echec » (repris au prochain passage). */
    private void envoyerSms(RendezVous rdv, String tel, String date, String langue) {
        if (tel == null) {
            return;
        }
        try {
            sms.envoyer(tel, Messages.rappelSms(langue, date));
            smsEnvoyes++;
        } catch (RuntimeException e) {
            smsEnEchec++;
            log.warn("Rappel SMS non envoye pour le rendez-vous {} : {}", rdv.id(), e.getMessage());
            rdv.marquerSmsEnEchec();
            try {
                rendezVous.enregistrer(rdv);
            } catch (RuntimeException e2) {
                log.warn("Echec du SMS non enregistre pour le rendez-vous {} : {}", rdv.id(), e2.getMessage());
            }
        }
    }
}
