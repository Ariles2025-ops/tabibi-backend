package dz.tabibi.backend.rendezvous.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance des rendez-vous. Le domaine definit ce dont il a besoin ;
 * un adaptateur (en memoire aujourd'hui, JPA demain) le realise.
 */
public interface RendezVousRepository {

    RendezVous enregistrer(RendezVous rdv);

    Optional<RendezVous> parId(UUID id);

    /** Rendez-vous d'un patient, tous statuts, du plus proche au plus lointain. */
    List<RendezVous> parPatient(UUID patientId);

    /** Agenda d'un medecin : ses rendez-vous, tous statuts, du plus proche au plus lointain. */
    List<RendezVous> parMedecin(UUID medecinId);

    /**
     * Rendez-vous confirmes dont le rappel n'a pas encore ete envoye et dont le debut est dans
     * l'intervalle [de, a[, du plus proche au plus lointain.
     */
    List<RendezVous> confirmesSansRappelEntre(Instant de, Instant a);

    /**
     * Rendez-vous confirmes dont le SMS de rappel a echoue (moins de {@code maxEchecs} fois) et dont le
     * debut est dans [de, a[ : a reprendre.
     */
    List<RendezVous> confirmesAvecSmsAReprendre(Instant de, Instant a, int maxEchecs);

    /** Rendez-vous encore confirmes dont le debut est strictement avant l'instant donne. */
    List<RendezVous> confirmesDebutantAvant(Instant limite);

    /**
     * Serialise, jusqu'a la fin de la transaction en cours, les reservations d'un meme patient (verrou consultatif) :
     * les regles « un RDV a venir par medecin » et « pas de chevauchement » se verifient alors sans course.
     * Sans effet pour un adaptateur monoprocessus.
     */
    default void verrouillerPatient(UUID patientId) {
    }
}
