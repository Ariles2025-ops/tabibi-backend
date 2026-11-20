package dz.tabibi.backend.demandes.adapter;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Au plus {@code max} depots par IP et par heure (fenetre fixe, en memoire, une seule instance d'API). */
class LimiteParIp {

    private final int max;
    private final Clock horloge;
    private final Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();
    private volatile long heureCourante;

    LimiteParIp(int max, Clock horloge) {
        this.max = max;
        this.horloge = horloge;
        this.heureCourante = heure();
    }

    /** Compte un depot ; false si la limite de l'heure est depassee. */
    boolean autoriser(String ip) {
        long h = heure();
        if (h != heureCourante) {
            synchronized (this) {
                if (h != heureCourante) {
                    compteurs.clear();
                    heureCourante = h;
                }
            }
        }
        return compteurs.computeIfAbsent(ip == null ? "?" : ip, k -> new AtomicInteger()).incrementAndGet() <= max;
    }

    private long heure() {
        return horloge.millis() / 3_600_000;
    }
}
