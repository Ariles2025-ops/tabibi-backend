package dz.tabibi.backend.administration.domain;

import java.util.UUID;

/**
 * Port : donne le role MEDECIN au compte du praticien dont la candidature est validee.
 * Tout le monde s'inscrit comme patient ; seul l'administrateur fait d'un compte un medecin.
 */
public interface AttributionRoleMedecin {

    void accorder(UUID compte);
}
