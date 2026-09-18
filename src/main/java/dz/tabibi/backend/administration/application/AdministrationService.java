package dz.tabibi.backend.administration.application;

import dz.tabibi.backend.administration.domain.CandidatureIntrouvableException;
import dz.tabibi.backend.administration.domain.CandidatureInvalideException;
import dz.tabibi.backend.administration.domain.CandidatureMedecin;
import dz.tabibi.backend.administration.domain.CandidatureRepository;
import dz.tabibi.backend.administration.domain.DemandeCandidature;
import dz.tabibi.backend.administration.domain.StatistiquesAdministration;
import dz.tabibi.backend.administration.domain.StatutCandidature;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage de l'administration : depot d'une candidature par un medecin, examen par
 * l'administrateur (validation = publication dans l'annuaire, refus motive), statistiques.
 * Le medecin est prevenu de la decision par le port Notifieur.
 */
@Service
public class AdministrationService {

    private final CandidatureRepository candidatures;
    private final MedecinRepository medecins;
    private final Notifieur notifieur;

    public AdministrationService(CandidatureRepository candidatures, MedecinRepository medecins, Notifieur notifieur) {
        this.candidatures = candidatures;
        this.medecins = medecins;
        this.notifieur = notifieur;
    }

    /**
     * Depose la candidature du medecin connecte. Une candidature refusee peut etre redeposee.
     * @throws CandidatureInvalideException si une donnee obligatoire manque.
     * @throws TransitionInvalideException si une candidature en attente ou validee existe deja.
     */
    @Transactional
    public CandidatureMedecin deposer(UUID medecinId, DemandeCandidature demande) {
        candidatures.derniereDuMedecin(medecinId)
                .filter(c -> c.statut() != StatutCandidature.REFUSEE)
                .ifPresent(c -> {
                    throw new TransitionInvalideException(c.estEnAttente()
                            ? "Une candidature est deja en attente d'examen."
                            : "Votre candidature a deja ete validee.");
                });
        return candidatures.enregistrer(CandidatureMedecin.deposer(medecinId, demande, Instant.now()));
    }

    /**
     * La derniere candidature du medecin connecte.
     * @throws CandidatureIntrouvableException s'il n'en a depose aucune.
     */
    public CandidatureMedecin maCandidature(UUID medecinId) {
        return candidatures.derniereDuMedecin(medecinId)
                .orElseThrow(CandidatureIntrouvableException::aucuneDeposee);
    }

    /** Candidatures, eventuellement filtrees par statut, de la plus ancienne a la plus recente. */
    public List<CandidatureMedecin> lister(Optional<StatutCandidature> statut) {
        return candidatures.lister(statut);
    }

    /**
     * Valide une candidature : le medecin est publie dans l'annuaire et prevenu.
     * @throws CandidatureIntrouvableException si la candidature n'existe pas.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    @Transactional
    public CandidatureMedecin valider(UUID candidatureId) {
        CandidatureMedecin validee = candidatures.enregistrer(charger(candidatureId).valider(Instant.now()));
        medecins.enregistrer(fichePublique(validee));
        notifieur.notifier(validee.medecinId(), "Candidature validee",
                "Votre candidature a ete validee : vous figurez desormais dans l'annuaire Tabibi.");
        return validee;
    }

    /**
     * Refuse une candidature en motivant la decision ; le medecin est prevenu avec le motif.
     * @throws CandidatureIntrouvableException si la candidature n'existe pas.
     * @throws CandidatureInvalideException si le motif manque.
     * @throws TransitionInvalideException si elle n'est pas en attente.
     */
    @Transactional
    public CandidatureMedecin refuser(UUID candidatureId, String motif) {
        CandidatureMedecin refusee = candidatures.enregistrer(charger(candidatureId).refuser(motif, Instant.now()));
        notifieur.notifier(refusee.medecinId(), "Candidature refusee",
                "Votre candidature a ete refusee. Motif : " + refusee.motifRefus());
        return refusee;
    }

    public StatistiquesAdministration statistiques() {
        return new StatistiquesAdministration(
                candidatures.compter(StatutCandidature.EN_ATTENTE),
                candidatures.compter(StatutCandidature.VALIDEE),
                candidatures.compter(StatutCandidature.REFUSEE));
    }

    private CandidatureMedecin charger(UUID candidatureId) {
        return candidatures.parId(candidatureId)
                .orElseThrow(() -> new CandidatureIntrouvableException(candidatureId));
    }

    /**
     * Fiche publique du praticien, sous l'identifiant du medecin (sujet de son jeton).
     * Les libelles facultatifs absents sont remplaces par le code correspondant : l'annuaire les exige.
     */
    private static Medecin fichePublique(CandidatureMedecin c) {
        return new Medecin(c.medecinId(), c.nomComplet(), c.specialiteSlug(),
                c.specialiteFr() != null ? c.specialiteFr() : c.specialiteSlug(),
                c.wilayaCode(),
                c.wilayaFr() != null ? c.wilayaFr() : c.wilayaCode(),
                c.ville());
    }
}
