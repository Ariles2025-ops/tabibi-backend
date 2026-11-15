package dz.tabibi.backend.creneaux.application;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import org.springframework.stereotype.Service;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage des creneaux : consulter les disponibilites d'un medecin, ouvrir un creneau.
 * L'ouverture d'un creneau est signalee au port AlerteCreneau (liste d'attente du medecin).
 */
@Service
public class CreneauService {

    public static final int DUREE_MIN_MINUTES = 5;
    public static final int DUREE_MAX_MINUTES = 120;

    private final CreneauRepository repository;
    private final AlerteCreneau alerteCreneau;

    public CreneauService(CreneauRepository repository, AlerteCreneau alerteCreneau) {
        this.repository = repository;
        this.alerteCreneau = alerteCreneau;
    }

    public List<Creneau> disponiblesPour(UUID medecinId) {
        // Seuls les creneaux a venir sont proposes : un creneau passe n'est plus reservable.
        Instant maintenant = Instant.now();
        return repository.disponiblesPour(medecinId).stream()
                .filter(c -> c.debut().isAfter(maintenant))
                .toList();
    }

    /**
     * Ouvre un creneau disponible dans l'agenda du medecin ; la liste d'attente du medecin en est alertee.
     * Regles : le debut est dans le futur, la duree est comprise entre 5 et 120 minutes.
     * @throws CreneauInvalideException si une regle n'est pas respectee.
     */
    public Creneau ouvrir(UUID medecinId, Instant debut, int dureeMinutes) {
        if (debut == null || !debut.isAfter(Instant.now())) {
            throw new CreneauInvalideException("Le début du créneau doit être dans le futur.");
        }
        if (dureeMinutes < DUREE_MIN_MINUTES || dureeMinutes > DUREE_MAX_MINUTES) {
            throw new CreneauInvalideException("La durée doit être comprise entre "
                    + DUREE_MIN_MINUTES + " et " + DUREE_MAX_MINUTES + " minutes.");
        }
        Instant fin = debut.plusSeconds(60L * dureeMinutes);
        boolean chevauche = repository.aVenirPour(medecinId, debut.minusSeconds(60L * DUREE_MAX_MINUTES)).stream()
                .anyMatch(c -> c.debut().isBefore(fin) && debut.isBefore(c.debut().plusSeconds(60L * c.dureeMinutes())));
        if (chevauche) {
            throw new CreneauInvalideException("Ce créneau chevauche un créneau déjà ouvert à cette heure-là.");
        }
        Creneau creneau = repository.enregistrer(new Creneau(UUID.randomUUID(), medecinId, debut, dureeMinutes, true));
        alerteCreneau.creneauLibere(creneau.medecinId(), creneau.debut());
        return creneau;
    }

    /** Creneaux a venir du medecin, libres ou reserves, du plus proche au plus lointain (sa vue d'agenda). */
    public List<Creneau> aVenirDu(UUID medecinId) {
        return repository.aVenirPour(medecinId, Instant.now());
    }

    /**
     * Supprime un creneau libre de l'agenda du medecin.
     * @throws CreneauIntrouvableException si le creneau n'existe pas.
     * @throws AccesRefuseException s'il est dans l'agenda d'un autre medecin.
     * @throws TransitionInvalideException s'il est deja reserve (il faut alors annuler le rendez-vous).
     */
    public void supprimer(UUID medecinId, UUID creneauId) {
        Creneau creneau = repository.parId(creneauId).orElseThrow(() -> new CreneauIntrouvableException(creneauId));
        if (!creneau.medecinId().equals(medecinId)) {
            throw new AccesRefuseException("Ce créneau n'est pas dans votre agenda.");
        }
        if (!creneau.disponible()) {
            throw new TransitionInvalideException("Ce créneau est réservé : annulez d'abord le rendez-vous.");
        }
        repository.supprimer(creneauId);
    }
}
