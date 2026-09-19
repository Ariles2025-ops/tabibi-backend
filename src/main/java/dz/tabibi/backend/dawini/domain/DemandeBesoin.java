package dz.tabibi.backend.dawini.domain;

/**
 * Ce qu'un patient declare en publiant un besoin : le medicament recherche, la wilaya (code) et,
 * facultativement, la commune et une precision (dosage, forme, urgence...). La validation se fait
 * a la publication ({@link BesoinMedicament#publier}).
 */
public record DemandeBesoin(
        String medicament,
        String wilayaCode,
        String commune,
        String precision
) {}
