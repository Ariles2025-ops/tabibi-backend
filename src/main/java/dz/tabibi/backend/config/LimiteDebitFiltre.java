package dz.tabibi.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de debit des actions (POST, PUT, PATCH, DELETE) par compte connecte : au-dela de {@code maxParMinute}
 * dans la minute en cours, 429. Empeche un compte de remplir en boucle agendas, messageries ou notifications.
 * Les lectures ne sont pas limitees. Fenetre fixe d'une minute, en memoire (une seule instance d'API).
 */
public class LimiteDebitFiltre extends OncePerRequestFilter {

    private final int maxParMinute;
    private final Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();
    private volatile long minuteCourante = minute();

    public LimiteDebitFiltre(int maxParMinute) {
        this.maxParMinute = maxParMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String m = requete.getMethod();
        return maxParMinute <= 0 || "GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            suite.doFilter(requete, reponse);
            return;
        }
        long maintenant = minute();
        if (maintenant != minuteCourante) {
            synchronized (this) {
                if (maintenant != minuteCourante) {
                    compteurs.clear();
                    minuteCourante = maintenant;
                }
            }
        }
        int n = compteurs.computeIfAbsent(auth.getName(), k -> new AtomicInteger()).incrementAndGet();
        if (n > maxParMinute) {
            reponse.setStatus(429);
            reponse.setHeader("Retry-After", "60");
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("{\"message\":\"Trop d'actions en peu de temps : réessayez dans une minute.\"}");
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private static long minute() {
        return System.currentTimeMillis() / 60_000;
    }
}
