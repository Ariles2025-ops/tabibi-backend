package dz.tabibi.backend.creneaux.application;

import dz.tabibi.backend.creneaux.domain.Creneau;
import dz.tabibi.backend.creneaux.domain.CreneauExistantException;
import dz.tabibi.backend.creneaux.domain.CreneauInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauRepository;
import dz.tabibi.backend.creneaux.domain.ResultatSemaineType;
import dz.tabibi.backend.creneaux.domain.SemaineType;
import dz.tabibi.backend.listeattente.domain.AlerteCreneau;
import org.springframework.stereotype.Service;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.creneaux.domain.CreneauIntrouvableException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
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
    public static final int SEMAINES_MAX = 8;
    public static final int CRENEAUX_MAX_PAR_APPEL = 500;
    public static final ZoneId FUSEAU = ZoneId.of("Africa/Algiers");

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
        // Deux ouvertures simultanees a la meme heure : l'index unique (medecin, debut) refuse la seconde (409).
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
        // Suppression conditionnelle (where id = ? and disponible = true) : un creneau reserve entre-temps n'est jamais supprime.
        if (!repository.supprimerSiDisponible(creneauId)) {
            throw new TransitionInvalideException("Ce créneau vient d'être réservé : annulez d'abord le rendez-vous.");
        }
    }

    /**
     * Ouvre en une fois les creneaux d'une semaine type (heure d'Alger). Un creneau deja passe ou qui chevauche
     * un creneau existant (ou un autre creneau du meme appel) est ignore.
     * @throws CreneauInvalideException si le modele est invalide, depasse 8 semaines ou 500 creneaux.
     */
    public ResultatSemaineType ouvrirSemaineType(UUID medecinId, SemaineType modele) {
        if (modele == null || modele.jours() == null || modele.jours().isEmpty() || modele.jours().stream().anyMatch(java.util.Objects::isNull)) {
            throw new CreneauInvalideException("Choisissez au moins un jour de la semaine.");
        }
        if (modele.debut() == null || modele.fin() == null || !modele.debut().isBefore(modele.fin())) {
            throw new CreneauInvalideException("L'heure de début doit précéder l'heure de fin.");
        }
        if (modele.dureeMinutes() < DUREE_MIN_MINUTES || modele.dureeMinutes() > DUREE_MAX_MINUTES) {
            throw new CreneauInvalideException("La durée doit être comprise entre "
                    + DUREE_MIN_MINUTES + " et " + DUREE_MAX_MINUTES + " minutes.");
        }
        if (modele.aPartirDu() == null) {
            throw new CreneauInvalideException("La date de départ est obligatoire.");
        }
        if (modele.nombreSemaines() < 1 || modele.nombreSemaines() > SEMAINES_MAX) {
            throw new CreneauInvalideException("Le nombre de semaines doit être compris entre 1 et " + SEMAINES_MAX + ".");
        }
        Set<DayOfWeek> jours = modele.jours().stream().map(j -> j.jour()).collect(Collectors.toSet());
        List<Instant> debuts = new ArrayList<>();
        LocalDate fin = modele.aPartirDu().plusDays(7L * modele.nombreSemaines());
        for (LocalDate date = modele.aPartirDu(); date.isBefore(fin); date = date.plusDays(1)) {
            if (!jours.contains(date.getDayOfWeek())) {
                continue;
            }
            for (LocalTime t = modele.debut();
                 !t.plusMinutes(modele.dureeMinutes()).isAfter(modele.fin()) && !t.plusMinutes(modele.dureeMinutes()).isBefore(t);
                 t = t.plusMinutes(modele.dureeMinutes())) {
                debuts.add(date.atTime(t).atZone(FUSEAU).toInstant());
                if (debuts.size() > CRENEAUX_MAX_PAR_APPEL) {
                    throw new CreneauInvalideException("Au plus " + CRENEAUX_MAX_PAR_APPEL + " créneaux par appel.");
                }
            }
        }
        long dureeSecondes = 60L * modele.dureeMinutes();
        Instant maintenant = Instant.now();
        List<Creneau> existants = new ArrayList<>(repository.aVenirPour(medecinId,
                maintenant.minusSeconds(60L * DUREE_MAX_MINUTES)));
        int crees = 0;
        int ignores = 0;
        Instant premierCree = null;
        for (Instant debut : debuts) {
            Instant finCreneau = debut.plusSeconds(dureeSecondes);
            boolean chevauche = existants.stream()
                    .anyMatch(c -> c.debut().isBefore(finCreneau) && debut.isBefore(c.debut().plusSeconds(60L * c.dureeMinutes())));
            if (!debut.isAfter(maintenant) || chevauche) {
                ignores++;
                continue;
            }
            Creneau creneau;
            try {
                creneau = repository.enregistrer(new Creneau(UUID.randomUUID(), medecinId, debut, modele.dureeMinutes(), true));
            } catch (CreneauExistantException e) {
                // Un creneau a la meme heure a ete ouvert entre-temps : ignore comme un chevauchement.
                ignores++;
                continue;
            }
            existants.add(creneau);
            if (premierCree == null) {
                premierCree = creneau.debut();
            }
            crees++;
        }
        // Une seule alerte liste d'attente par appel (pas une par creneau) : les inscrits ne recoivent qu'un message.
        if (premierCree != null) {
            alerteCreneau.creneauLibere(medecinId, premierCree);
        }
        return new ResultatSemaineType(crees, ignores);
    }
}
