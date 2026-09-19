package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Compteurs;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Realisation Micrometer du port {@link Compteurs} : chaque evenement metier devient un compteur
 * expose par {@code /actuator/prometheus} (reserve au role ADMIN). Les compteurs sont crees a la
 * demande et gardes en cache : un compteur Micrometer se retrouve par son nom, mais le cache evite
 * une recherche a chaque evenement.
 * <p>
 * Aucune etiquette ne porte d'identifiant d'utilisateur : ce serait a la fois une fuite de donnees
 * personnelles et une explosion du nombre de series temporelles.
 */
@Component
public class CompteursMetier implements Compteurs {

    private final MeterRegistry registre;
    private final Map<String, Counter> cache = new ConcurrentHashMap<>();

    public CompteursMetier(MeterRegistry registre) {
        this.registre = registre;
    }

    @Override
    public void incrementer(String compteur) {
        cache.computeIfAbsent(compteur, registre::counter).increment();
    }
}
