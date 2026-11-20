package dz.tabibi.backend.ordonnances.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.ordonnances.domain.CodeVerification;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import dz.tabibi.backend.notifications.domain.Messages;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cas d'usage des ordonnances : rediger, consulter, verifier par code. */
@Service
public class OrdonnanceService {

    /** Nombre de tirages avant d'abandonner la recherche d'un code libre (jamais atteint en pratique). */
    private static final int ESSAIS_CODE = 10;

    private final OrdonnanceRepository repository;
    private final RendezVousRepository rendezVous;
    private final Notifieur notifieur;

    /** Profils : langue du patient pour ses notifications (francais si absent). */
    private ProfilRepository profils;

    @Autowired(required = false)
    public void setProfils(ProfilRepository profils) {
        this.profils = profils;
    }

    private String langue(UUID patientId) {
        return Messages.langueDe(profils, patientId);
    }

    public OrdonnanceService(OrdonnanceRepository repository, RendezVousRepository rendezVous, Notifieur notifieur) {
        this.repository = repository;
        this.rendezVous = rendezVous;
        this.notifieur = notifieur;
    }

    /**
     * Redige une ordonnance pour un patient, eventuellement rattachee a un rendez-vous.
     * Regle : au moins une ligne, et chaque ligne indique un medicament.
     * Regle d'acces : le medecin ne prescrit qu'a ses patients (au moins un rendez-vous avec lui) ; un
     * rendez-vous indique doit etre le sien et celui de ce patient.
     * @throws OrdonnanceInvalideException si le contenu est incomplet.
     * @throws AccesRefuseException si le patient ou le rendez-vous n'est pas celui du medecin.
     */
    public Ordonnance emettre(UUID medecinId, UUID patientId, UUID rendezVousId, List<LigneOrdonnance> lignes) {
        if (patientId == null) {
            throw new OrdonnanceInvalideException("Le patient est obligatoire.");
        }
        verifierPatientDuMedecin(medecinId, patientId, rendezVousId);
        if (lignes == null || lignes.isEmpty()) {
            throw new OrdonnanceInvalideException("Une ordonnance doit contenir au moins une ligne.");
        }
        boolean ligneIncomplete = lignes.stream()
                .anyMatch(l -> l == null || l.medicament() == null || l.medicament().isBlank());
        if (ligneIncomplete) {
            throw new OrdonnanceInvalideException("Chaque ligne doit indiquer un médicament.");
        }
        Ordonnance ordonnance = Ordonnance.emettre(
                medecinId, patientId, rendezVousId, lignes, codeLibre(), Instant.now());
        Ordonnance emise = repository.enregistrer(ordonnance);
        notifieur.notifier(patientId, Messages.ordonnanceNouvelleSujet(langue(patientId)),
                Messages.ordonnanceNouvelleMessage(langue(patientId)),
                "/ordonnances/" + emise.id());
        return emise;
    }

    /** Ordonnances d'un patient, de la plus recente a la plus ancienne. */
    public List<Ordonnance> mesOrdonnances(UUID patientId) {
        return repository.parPatient(patientId);
    }

    /** Ordonnances redigees par un medecin, de la plus recente a la plus ancienne. */
    public List<Ordonnance> ordonnancesDuMedecin(UUID medecinId) {
        return repository.parMedecin(medecinId);
    }

    /**
     * Ordonnance par identifiant, pour le patient a qui elle est destinee ou le medecin qui l'a redigee.
     * @throws OrdonnanceIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si le demandeur n'est ni l'un ni l'autre.
     */
    public Ordonnance parIdPour(UUID demandeurId, UUID ordonnanceId) {
        Ordonnance ordonnance = repository.parId(ordonnanceId)
                .orElseThrow(() -> new OrdonnanceIntrouvableException(ordonnanceId));
        if (!ordonnance.estAccessiblePar(demandeurId)) {
            throw new AccesRefuseException("Cette ordonnance ne vous concerne pas.");
        }
        return ordonnance;
    }

    /**
     * Verification publique par code (pharmacien) : la reponse ne revele aucune donnee personnelle.
     * Le code est accepte en minuscules et avec des espaces autour.
     * @throws OrdonnanceIntrouvableException si le code est inconnu.
     */
    public ResultatVerification verifier(String code) {
        Ordonnance ordonnance = repository.parCode(CodeVerification.normaliser(code))
                .orElseThrow(OrdonnanceIntrouvableException::codeInconnu);
        return ResultatVerification.de(ordonnance);
    }

    /**
     * Le medecin annule une ordonnance qu'il a redigee ; le patient est prevenu.
     * @throws OrdonnanceIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si elle a ete redigee par un autre medecin.
     */
    public Ordonnance annuler(UUID medecinId, UUID ordonnanceId, String motif) {
        Ordonnance ordonnance = repository.parId(ordonnanceId)
                .orElseThrow(() -> new OrdonnanceIntrouvableException(ordonnanceId));
        if (!ordonnance.medecinId().equals(medecinId)) {
            throw new AccesRefuseException("Vous n'avez pas rédigé cette ordonnance.");
        }
        Ordonnance annulee = repository.enregistrer(ordonnance.annuler(motif));
        notifieur.notifier(annulee.patientId(), Messages.ordonnanceAnnuleeSujet(langue(annulee.patientId())),
                Messages.ordonnanceAnnuleeMessage(langue(annulee.patientId()), annulee.motifAnnulation()),
                "/ordonnances/" + annulee.id());
        return annulee;
    }

    /**
     * Une pharmacie delivre l'ordonnance (par son code) : elle ne pourra plus etre presentee ailleurs.
     * @throws OrdonnanceIntrouvableException si le code est inconnu.
     */
    public ResultatVerification delivrer(String code) {
        Ordonnance ordonnance = repository.parCode(CodeVerification.normaliser(code))
                .orElseThrow(OrdonnanceIntrouvableException::codeInconnu);
        Ordonnance delivree = repository.enregistrer(ordonnance.delivrer(Instant.now()));
        notifieur.notifier(delivree.patientId(), Messages.ordonnanceDelivreeSujet(langue(delivree.patientId())),
                Messages.ordonnanceDelivreeMessage(langue(delivree.patientId())), "/ordonnances/" + delivree.id());
        return ResultatVerification.de(delivree);
    }

    /** Tire un code de verification qu'aucune ordonnance ne porte encore. */
    private String codeLibre() {
        for (int essai = 0; essai < ESSAIS_CODE; essai++) {
            String code = CodeVerification.generer();
            if (repository.parCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Impossible d'attribuer un code de vérification libre.");
    }

    /** Seul un rendez-vous confirme ou honore fait du patient celui du medecin (pas annule, pas absent). */
    private static boolean rdvOuvreUneOrdonnance(RendezVous rdv) {
        return rdv.statut() == dz.tabibi.backend.rendezvous.domain.StatutRdv.CONFIRME
                || rdv.statut() == dz.tabibi.backend.rendezvous.domain.StatutRdv.HONORE;
    }

    private void verifierPatientDuMedecin(UUID medecinId, UUID patientId, UUID rendezVousId) {
        if (rendezVousId != null) {
            RendezVous rdv = rendezVous.parId(rendezVousId)
                    .orElseThrow(() -> new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda."));
            if (!rdv.estAvec(medecinId) || !rdv.appartientA(patientId)) {
                throw new AccesRefuseException("Ce rendez-vous n'est pas dans votre agenda.");
            }
            if (!rdvOuvreUneOrdonnance(rdv)) {
                throw new dz.tabibi.backend.commun.domain.TransitionInvalideException(
                        "Une ordonnance ne se rédige que sur un rendez-vous confirmé ou honoré.");
            }
            return;
        }
        boolean patientDuMedecin = rendezVous.parPatient(patientId).stream()
                .anyMatch(r -> r.estAvec(medecinId) && rdvOuvreUneOrdonnance(r));
        if (!patientDuMedecin) {
            throw new AccesRefuseException("Vous ne pouvez rédiger une ordonnance que pour vos patients.");
        }
    }
}
