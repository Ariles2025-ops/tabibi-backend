package dz.tabibi.backend.ordonnances.application;

import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.Compteurs;
import dz.tabibi.backend.ordonnances.domain.CodeVerification;
import dz.tabibi.backend.ordonnances.domain.GenerateurPdfOrdonnance;
import dz.tabibi.backend.ordonnances.domain.LigneOrdonnance;
import dz.tabibi.backend.ordonnances.domain.Ordonnance;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceImprimable;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceIntrouvableException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceInvalideException;
import dz.tabibi.backend.ordonnances.domain.OrdonnanceRepository;
import dz.tabibi.backend.ordonnances.domain.ResultatVerification;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des ordonnances : rediger, consulter, verifier par code, imprimer (PDF avec QR code).
 * La version imprimable porte le nom du medecin (annuaire) et celui du patient (profil) ; a defaut,
 * une mention generique : le document reste utilisable, le code de verification fait foi.
 */
@Service
public class OrdonnanceService {

    /** Nombre de tirages avant d'abandonner la recherche d'un code libre (jamais atteint en pratique). */
    private static final int ESSAIS_CODE = 10;

    /** Repli quand le medecin n'est pas (ou plus) dans l'annuaire. */
    static final String MEDECIN_INCONNU = "Medecin";
    /** Repli quand le patient n'a pas renseigne son profil. */
    static final String PATIENT_INCONNU = "Patient";
    /** Chemin de la page publique de verification du front web ; le code est passe en parametre. */
    static final String CHEMIN_VERIFICATION = "/verifier?code=";

    private final OrdonnanceRepository repository;
    private final GenerateurPdfOrdonnance generateurPdf;
    private final ProfilRepository profils;
    private final MedecinRepository medecins;
    private final Compteurs compteurs;
    private final String baseUrlWeb;

    public OrdonnanceService(OrdonnanceRepository repository,
                             GenerateurPdfOrdonnance generateurPdf,
                             ProfilRepository profils,
                             MedecinRepository medecins,
                             Compteurs compteurs,
                             @Value("${tabibi.web.base-url:http://localhost:4200}") String baseUrlWeb) {
        this.repository = repository;
        this.generateurPdf = generateurPdf;
        this.profils = profils;
        this.medecins = medecins;
        this.compteurs = compteurs;
        this.baseUrlWeb = baseUrlWeb.endsWith("/") ? baseUrlWeb.substring(0, baseUrlWeb.length() - 1) : baseUrlWeb;
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
            throw new OrdonnanceInvalideException("Une ordonnance doit contenir au moins une ligne.", Cles.ORDONNANCE_SANS_LIGNE);
        }
        boolean ligneIncomplete = lignes.stream()
                .anyMatch(l -> l == null || l.medicament() == null || l.medicament().isBlank());
        if (ligneIncomplete) {
            throw new OrdonnanceInvalideException("Chaque ligne doit indiquer un medicament.");
        }
        Ordonnance ordonnance = repository.enregistrer(Ordonnance.emettre(
                medecinId, patientId, rendezVousId, lignes, codeLibre(), Instant.now()));
        compteurs.incrementer(Compteurs.ORDONNANCES_EMISES);
        return ordonnance;
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
     * Version imprimable (PDF) d'une ordonnance, aux memes conditions d'acces que {@link #parIdPour} :
     * son patient ou son medecin auteur. Le QR code encode l'adresse publique de verification
     * ({@code base-url/verifier?code=XXXX}), qui ne revele aucune donnee personnelle.
     * @throws OrdonnanceIntrouvableException si elle n'existe pas.
     * @throws AccesRefuseException si le demandeur n'est ni l'un ni l'autre.
     */
    public OrdonnanceImprimable pdf(UUID demandeurId, UUID ordonnanceId) {
        Ordonnance ordonnance = parIdPour(demandeurId, ordonnanceId);
        String nomMedecin = medecins.parId(ordonnance.medecinId()).map(Medecin::nomComplet).orElse(MEDECIN_INCONNU);
        String nomPatient = profils.parUtilisateur(ordonnance.patientId()).map(Profil::nomComplet).orElse(PATIENT_INCONNU);
        byte[] contenu = generateurPdf.generer(ordonnance, nomMedecin, nomPatient, urlVerification(ordonnance));
        return new OrdonnanceImprimable(ordonnance.codeVerification(), contenu);
    }

    /** Adresse de la page publique de verification du front web pour cette ordonnance. */
    String urlVerification(Ordonnance ordonnance) {
        return baseUrlWeb + CHEMIN_VERIFICATION + ordonnance.codeVerification();
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
