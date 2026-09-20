package dz.tabibi.backend.referentiel.domain;

import java.util.List;

/** Port des donnees de reference publiques : wilayas et specialites. */
public interface ReferentielRepository {
    List<Wilaya> wilayas();
    List<Specialite> specialites();
}
