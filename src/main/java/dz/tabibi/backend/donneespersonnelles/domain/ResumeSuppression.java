package dz.tabibi.backend.donneespersonnelles.domain;

import java.util.Map;
import java.util.TreeMap;

/**
 * Ce que l'effacement d'un compte a fait : le nombre d'elements effaces ou anonymises, et le
 * nombre d'elements conserves (obligation de tracabilite medicale). L'utilisateur doit savoir
 * precisement ce qui reste : le resume est renvoye tel quel par l'API.
 */
public record ResumeSuppression(Map<String, Long> elementsEffaces, Map<String, Long> elementsConserves) {

    public ResumeSuppression {
        elementsEffaces = Map.copyOf(elementsEffaces);
        elementsConserves = Map.copyOf(elementsConserves);
    }

    /** Construit un resume en accumulant les comptes, ordonnes par nom d'element. */
    public static final class Constructeur {

        private final Map<String, Long> effaces = new TreeMap<>();
        private final Map<String, Long> conserves = new TreeMap<>();

        public Constructeur efface(String element, long nombre) {
            effaces.merge(element, nombre, Long::sum);
            return this;
        }

        public Constructeur conserve(String element, long nombre) {
            conserves.merge(element, nombre, Long::sum);
            return this;
        }

        public ResumeSuppression construire() {
            return new ResumeSuppression(effaces, conserves);
        }
    }
}
