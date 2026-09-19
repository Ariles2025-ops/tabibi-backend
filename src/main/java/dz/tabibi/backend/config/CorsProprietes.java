package dz.tabibi.backend.config;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Origines (schema://hote[:port]) autorisees a appeler l'API depuis un navigateur : le front web
 * Angular tourne sur une autre origine que l'API, sans CORS le navigateur bloque tout appel.
 * Propriete {@code tabibi.cors.origines} (liste separee par des virgules, variable
 * d'environnement TABIBI_CORS_ORIGINES) ; par defaut le serveur de developpement Angular.
 * Enregistree par SecurityConfig (@Import), donc presente aussi dans les tests web qui
 * n'importent que SecurityConfig ; la valeur par defaut rend la propriete facultative.
 */
public class CorsProprietes {

    /** Serveur de developpement Angular (ng serve). */
    public static final String ORIGINE_DEV = "http://localhost:4200";

    private final List<String> origines;

    public CorsProprietes(@Value("${tabibi.cors.origines:" + ORIGINE_DEV + "}") List<String> origines) {
        this.origines = origines == null ? List.of() : origines.stream()
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    /** Origines autorisees, sans blancs ni entree vide ; une liste vide bloque tout appel depuis un navigateur. */
    public List<String> origines() {
        return origines;
    }
}
