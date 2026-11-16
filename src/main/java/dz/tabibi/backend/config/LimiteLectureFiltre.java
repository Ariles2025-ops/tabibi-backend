package dz.tabibi.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Anti-copie de l'annuaire (et anti-essais de codes d'ordonnance) : au plus {@code maxParMinute} lectures de
 * /api/medecins… et /api/ordonnances/verifier/… par adresse IP et par minute
 * (429 au-dela). Un visiteur humain n'en fait que quelques-unes ; un robot qui aspire les 75 000 fiches est freine.
 * Les adresses du reseau interne (rendu serveur du site, donc aussi les robots des moteurs de recherche qui lisent
 * les pages rendues) ne sont pas limitees. L'adresse du client est celle reconstituee par Tomcat depuis
 * X-Forwarded-For (proxies internes de confiance : Caddy). Fenetre fixe d'une minute, en memoire.
 */
public class LimiteLectureFiltre extends OncePerRequestFilter {

    private final int maxParMinute;
    private final Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();
    private volatile long minuteCourante = minute();

    public LimiteLectureFiltre(int maxParMinute) {
        this.maxParMinute = maxParMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        return maxParMinute <= 0 || !"GET".equals(requete.getMethod())
                || chemin == null || !(chemin.startsWith("/api/medecins") || chemin.startsWith("/api/ordonnances/verifier"))
                || estInterne(requete.getRemoteAddr());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        long maintenant = minute();
        if (maintenant != minuteCourante) {
            synchronized (this) {
                if (maintenant != minuteCourante) {
                    compteurs.clear();
                    minuteCourante = maintenant;
                }
            }
        }
        int n = compteurs.computeIfAbsent(requete.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet();
        if (n > maxParMinute) {
            reponse.setStatus(429);
            reponse.setHeader("Retry-After", "60");
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("{\"message\":\"Trop de recherches en peu de temps : réessayez dans une minute.\"}");
            return;
        }
        suite.doFilter(requete, reponse);
    }

    /** Pour les controleurs : meme regle que le filtre. */
    public static boolean estInterneAdresse(String adresse) {
        return estInterne(adresse);
    }

    /** Adresse privee, locale ou de boucle : trafic interne au serveur (rendu du site, sondes). */
    static boolean estInterne(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            return false;
        }
        try {
            InetAddress a = InetAddress.getByName(adresse);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private static long minute() {
        return System.currentTimeMillis() / 60_000;
    }
}
