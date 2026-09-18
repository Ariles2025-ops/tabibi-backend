package dz.tabibi.backend.ordonnances.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des ordonnances. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface OrdonnanceRepository {

    Ordonnance enregistrer(Ordonnance ordonnance);

    Optional<Ordonnance> parId(UUID id);

    /** Ordonnances d'un patient, tous statuts, de la plus recente a la plus ancienne. */
    List<Ordonnance> parPatient(UUID patientId);

    /** Ordonnances redigees par un medecin, tous statuts, de la plus recente a la plus ancienne. */
    List<Ordonnance> parMedecin(UUID medecinId);

    /** Ordonnance portant ce code de verification (unique). */
    Optional<Ordonnance> parCode(String codeVerification);
}
