package dz.tabibi.backend.ordonnances.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.ordonnances.domain.CodeVerification;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
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

    public OrdonnanceService(OrdonnanceRepository repository) {
        this.repository = repository;
    }

    /**
     * Redige une ordonnance pour un patient, eventuellement rattachee a un rendez-vous.
     * Regle : au moins une ligne, et chaque ligne indique un medicament.
     * @throws OrdonnanceInvalideException si le contenu est incomplet.
     */
    public Ordonnance emettre(UUID medecinId, UUID patientId, UUID rendezVousId, List<LigneOrdonnance> lignes) {
        if (patientId == null) {
            throw new OrdonnanceInvalideException("Le patient est obligatoire.");
        }
        if (lignes == null || lignes.isEmpty()) {
            throw new OrdonnanceInvalideException("Une ordonnance doit contenir au moins une ligne.");
        }
        boolean ligneIncomplete = lignes.stream()
                .anyMatch(l -> l == null || l.medicament() == null || l.medicament().isBlank());
        if (ligneIncomplete) {
            throw new OrdonnanceInvalideException("Chaque ligne doit indiquer un medicament.");
        }
        Ordonnance ordonnance = Ordonnance.emettre(
                medecinId, patientId, rendezVousId, lignes, codeLibre(), Instant.now());
        return repository.enregistrer(ordonnance);
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

    /** Tire un code de verification qu'aucune ordonnance ne porte encore. */
    private String codeLibre() {
        for (int essai = 0; essai < ESSAIS_CODE; essai++) {
            String code = CodeVerification.generer();
            if (repository.parCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Impossible d'attribuer un code de verification libre.");
    }
}
