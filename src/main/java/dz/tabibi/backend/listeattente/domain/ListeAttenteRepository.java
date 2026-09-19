package dz.tabibi.backend.listeattente.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des inscriptions en liste d'attente. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire ou JPA) le realise.
 */
public interface ListeAttenteRepository {

    InscriptionAttente enregistrer(InscriptionAttente inscription);

    Optional<InscriptionAttente> parId(UUID id);

    /** L'inscription d'un patient sur la liste d'un medecin, s'il y en a une (au plus une). */
    Optional<InscriptionAttente> parPatientEtMedecin(UUID patientId, UUID medecinId);

    /** Inscriptions d'un patient, les plus anciennes d'abord. */
    List<InscriptionAttente> parPatient(UUID patientId);

    /** Liste d'attente d'un medecin : ses inscrits, les plus anciens d'abord. */
    List<InscriptionAttente> parMedecin(UUID medecinId);

    /** Retire une inscription ; sans effet si elle n'existe pas. */
    void supprimer(UUID id);
}
