package dz.tabibi.backend.teleconsultation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des teleconsultations. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface TeleconsultationRepository {

    Teleconsultation enregistrer(Teleconsultation teleconsultation);

    Optional<Teleconsultation> parId(UUID id);

    /** Teleconsultations d'un patient, tous statuts, de la plus recente a la plus ancienne. */
    List<Teleconsultation> parPatient(UUID patientId);

    /** Teleconsultations menees par un medecin, tous statuts, de la plus recente a la plus ancienne. */
    List<Teleconsultation> parMedecin(UUID medecinId);

    /** La derniere teleconsultation non annulee d'un rendez-vous, s'il y en a une. */
    Optional<Teleconsultation> parRendezVous(UUID rendezVousId);
}
