package dz.tabibi.backend.demandes.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandePraticienRepository {
    DemandePraticien enregistrer(DemandePraticien demande);

    Optional<DemandePraticien> parId(UUID id);

    /** Les plus recentes d'abord ; filtre de statut facultatif. */
    List<DemandePraticien> lister(Optional<StatutDemande> statut);
}
