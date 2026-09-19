package dz.tabibi.backend.dawini.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des besoins de medicaments. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface BesoinRepository {

    BesoinMedicament enregistrer(BesoinMedicament besoin);

    Optional<BesoinMedicament> parId(UUID id);

    /** Besoins d'un patient, tous statuts, les plus recents d'abord. */
    List<BesoinMedicament> parPatient(UUID patientId);

    /** Besoins ouverts d'une wilaya, les plus recents d'abord. */
    List<BesoinMedicament> ouvertsParWilaya(String wilayaCode);
}
