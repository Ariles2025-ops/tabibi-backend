package dz.tabibi.backend.avis.application;

import dz.tabibi.backend.avis.domain.Avis;
import dz.tabibi.backend.avis.domain.AvisIntrouvableException;
import dz.tabibi.backend.avis.domain.AvisInvalideException;
import dz.tabibi.backend.avis.domain.AvisRepository;
import dz.tabibi.backend.avis.domain.StatutAvis;
import dz.tabibi.backend.avis.domain.SyntheseAvis;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousIntrouvableException;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage des avis : depot par le patient a l'issue d'un rendez-vous honore (avis verifie, un seul
 * par rendez-vous), synthese publique d'un medecin, signalement par le medecin concerne, moderation
 * (masquer / retablir) par l'administrateur.
 */
@Service
public class AvisService {

    private final AvisRepository avis;
    private final RendezVousRepository rendezVous;

    public AvisService(AvisRepository avis, RendezVousRepository rendezVous) {
        this.avis = avis;
        this.rendezVous = rendezVous;
    }

    /**
     * Depose l'avis du patient sur le medecin d'un de ses rendez-vous honores.
     * @throws AvisInvalideException si le rendez-vous manque, si la note est hors bornes ou le commentaire trop long.
     * @throws RendezVousIntrouvableException si le rendez-vous n'existe pas.
     * @throws AccesRefuseException si le rendez-vous est a un autre patient.
     * @throws TransitionInvalideException si le rendez-vous n'est pas honore ou s'il a deja un avis.
     */
    @Transactional
    public Avis deposer(UUID patientId, UUID rendezVousId, int note, String commentaire) {
        if (rendezVousId == null) {
            throw new AvisInvalideException("Le rendez-vous est obligatoire.");
        }
        RendezVous rdv = rendezVous.parId(rendezVousId)
                .orElseThrow(() -> new RendezVousIntrouvableException(rendezVousId));
        if (!rdv.appartientA(patientId)) {
            throw new AccesRefuseException("Ce rendez-vous ne vous appartient pas.");
        }
        if (rdv.statut() != StatutRdv.HONORE) {
            throw new TransitionInvalideException(
                    "Seul un rendez-vous honore peut recevoir un avis (statut actuel : " + rdv.statut() + ").");
        }
        if (avis.parRendezVous(rendezVousId).isPresent()) {
            throw new TransitionInvalideException("Un avis a deja ete depose pour ce rendez-vous.");
        }
        return avis.enregistrer(Avis.deposer(rendezVousId, patientId, rdv.medecinId(), note, commentaire, Instant.now()));
    }

    /** Avis deposes par le patient, tous statuts, les plus recents d'abord. */
    public List<Avis> mesAvis(UUID patientId) {
        return avis.parPatient(patientId);
    }

    /** Synthese publique d'un medecin : seuls les avis publies comptent (ni signales, ni masques). */
    public SyntheseAvis avisPublics(UUID medecinId) {
        return SyntheseAvis.de(avis.publiesPourMedecin(medecinId));
    }

    /**
     * Le medecin concerne signale un avis a l'administrateur.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws AccesRefuseException s'il porte sur un autre medecin.
     * @throws TransitionInvalideException s'il n'est pas publie.
     */
    @Transactional
    public Avis signaler(UUID medecinId, UUID avisId) {
        Avis a = charger(avisId);
        if (!a.concerne(medecinId)) {
            throw new AccesRefuseException("Cet avis ne vous concerne pas.");
        }
        return avis.enregistrer(a.signaler());
    }

    /** Avis pour l'administrateur, eventuellement filtres par statut, les plus anciens d'abord. */
    public List<Avis> lister(Optional<StatutAvis> statut) {
        return statut.map(avis::parStatut).orElseGet(avis::tous);
    }

    /**
     * L'administrateur masque un avis.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws TransitionInvalideException s'il est deja masque.
     */
    @Transactional
    public Avis masquer(UUID avisId) {
        return avis.enregistrer(charger(avisId).masquer());
    }

    /**
     * L'administrateur remet un avis en ligne.
     * @throws AvisIntrouvableException si l'avis n'existe pas.
     * @throws TransitionInvalideException s'il est deja publie.
     */
    @Transactional
    public Avis retablir(UUID avisId) {
        return avis.enregistrer(charger(avisId).retablir());
    }

    private Avis charger(UUID avisId) {
        return avis.parId(avisId).orElseThrow(() -> new AvisIntrouvableException(avisId));
    }
}
