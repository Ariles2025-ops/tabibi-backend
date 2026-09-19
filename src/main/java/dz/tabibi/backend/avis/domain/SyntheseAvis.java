package dz.tabibi.backend.avis.domain;

import java.util.List;

/**
 * Ce que le public voit d'un medecin : la moyenne des notes de ses avis publies (arrondie a une
 * decimale, null s'il n'en a aucun), leur nombre et les avis eux-memes.
 */
public record SyntheseAvis(Double moyenne, long nombre, List<Avis> avis) {

    public SyntheseAvis {
        avis = List.copyOf(avis);
    }

    /** Synthese des avis donnes (supposes publies), les plus recents d'abord. */
    public static SyntheseAvis de(List<Avis> avis) {
        if (avis.isEmpty()) {
            return new SyntheseAvis(null, 0, avis);
        }
        double somme = avis.stream().mapToInt(Avis::note).sum();
        double moyenne = Math.round(somme / avis.size() * 10.0) / 10.0;
        return new SyntheseAvis(moyenne, avis.size(), avis);
    }
}
