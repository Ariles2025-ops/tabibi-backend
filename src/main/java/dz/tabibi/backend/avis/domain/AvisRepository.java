package dz.tabibi.backend.avis.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des avis. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface AvisRepository {

    Avis enregistrer(Avis avis);

    Optional<Avis> parId(UUID id);

    /** L'avis depose pour un rendez-vous, s'il y en a un (au plus un). */
    Optional<Avis> parRendezVous(UUID rendezVousId);

    /** Avis deposes par un patient, tous statuts, les plus recents d'abord. */
    List<Avis> parPatient(UUID patientId);

    /** Avis publies sur un medecin (ni signales, ni masques), les plus recents d'abord. */
    List<Avis> publiesPourMedecin(UUID medecinId);

    /** Avis d'un statut donne, les plus anciens d'abord. */
    List<Avis> parStatut(StatutAvis statut);

    /** Tous les avis, tous statuts, les plus anciens d'abord. */
    List<Avis> tous();
}
