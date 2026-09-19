package dz.tabibi.backend.commun.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limiteur de debit a seaux de jetons, un seau par cle (en pratique une adresse IP). Chaque seau
 * contient au plus {@code capacite} jetons et se recharge en continu de {@code capacite} jetons
 * par {@code periode} ; une requete consomme un jeton, elle est refusee quand le seau est vide.
 * Sans dependance externe : une ConcurrentHashMap dont les entrees inactives (seau plein depuis
 * une periode entiere) sont purgees au fil de l'eau, pour que la memoire ne croisse pas avec le
 * nombre d'adresses vues. L'horloge est injectee (fixe dans les tests).
 */
public final class LimiteurDebit {

    /** Verdict pour une requete : acceptee, ou refusee avec le delai (en secondes, au moins 1) avant un nouveau jeton. */
    public record Decision(boolean autorise, long attenteSecondes) {

        static final Decision AUTORISE = new Decision(true, 0);

        static Decision refuse(long attenteMs) {
            return new Decision(false, Math.max(1, (attenteMs + 999) / 1000));
        }
    }

    /** Etat d'une cle : jetons restants et dernier moment ou ils ont ete recalcules. */
    private static final class Seau {
        double jetons;
        long dernierCalculMs;

        Seau(double jetons, long dernierCalculMs) {
            this.jetons = jetons;
            this.dernierCalculMs = dernierCalculMs;
        }
    }

    private final int capacite;
    private final long periodeMs;
    private final Clock horloge;
    private final Map<String, Seau> seaux = new ConcurrentHashMap<>();
    private final AtomicLong dernierePurgeMs;

    /**
     * @param capacite nombre de requetes acceptees d'un coup (et par periode en regime continu)
     * @param periode  duree de recharge complete d'un seau (par exemple une minute pour "n requetes par minute")
     * @param horloge  source de temps
     */
    public LimiteurDebit(int capacite, Duration periode, Clock horloge) {
        if (capacite < 1) {
            throw new IllegalArgumentException("La capacite doit valoir au moins 1.");
        }
        if (periode == null || periode.isZero() || periode.isNegative()) {
            throw new IllegalArgumentException("La periode doit etre strictement positive.");
        }
        this.capacite = capacite;
        this.periodeMs = periode.toMillis();
        this.horloge = horloge;
        this.dernierePurgeMs = new AtomicLong(horloge.millis());
    }

    /** {@code capacite} requetes par minute. */
    public static LimiteurDebit parMinute(int capacite, Clock horloge) {
        return new LimiteurDebit(capacite, Duration.ofMinutes(1), horloge);
    }

    /** Tente de consommer un jeton pour cette cle. */
    public Decision tenter(String cle) {
        long maintenant = horloge.millis();
        purgerSiNecessaire(maintenant);
        Seau seau = seaux.computeIfAbsent(cle, c -> new Seau(capacite, maintenant));
        synchronized (seau) {
            recharger(seau, maintenant);
            if (seau.jetons >= 1) {
                seau.jetons -= 1;
                return Decision.AUTORISE;
            }
            double manque = 1 - seau.jetons;
            long attenteMs = (long) Math.ceil(manque * periodeMs / capacite);
            return Decision.refuse(attenteMs);
        }
    }

    /** Retire les cles dont le seau est de nouveau plein (aucune requete depuis une periode entiere). */
    public void purger() {
        long maintenant = horloge.millis();
        seaux.entrySet().removeIf(entree -> {
            Seau seau = entree.getValue();
            synchronized (seau) {
                return maintenant - seau.dernierCalculMs >= periodeMs;
            }
        });
    }

    /** Nombre de cles suivies en ce moment (supervision, tests). */
    public int nombreDeCles() {
        return seaux.size();
    }

    public int capacite() {
        return capacite;
    }

    private void recharger(Seau seau, long maintenant) {
        long ecoule = maintenant - seau.dernierCalculMs;
        if (ecoule > 0) {
            seau.jetons = Math.min(capacite, seau.jetons + (double) ecoule * capacite / periodeMs);
            seau.dernierCalculMs = maintenant;
        }
    }

    /** Au plus une purge par periode, par le premier appel qui constate qu'elle est due. */
    private void purgerSiNecessaire(long maintenant) {
        long derniere = dernierePurgeMs.get();
        if (maintenant - derniere >= periodeMs && dernierePurgeMs.compareAndSet(derniere, maintenant)) {
            purger();
        }
    }
}
