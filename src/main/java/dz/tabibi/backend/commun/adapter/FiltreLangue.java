package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Langue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lit l'en-tete {@code Accept-Language} de chaque requete et pose la langue correspondante dans
 * {@link ContexteLangue} pour toute la duree du traitement ; elle est effacee a la sortie, quoi
 * qu'il arrive (le fil d'execution retourne au pool du serveur). Un en-tete absent, vide ou
 * inconnu donne le francais.
 * Enregistre par {@link LangueConfig} (FilterRegistrationBean), pas par balayage : les tests web
 * (@WebMvcTest) n'en dependent pas et voient donc la langue par defaut.
 */
public class FiltreLangue extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        ContexteLangue.poser(Langue.depuisEntete(requete.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            ContexteLangue.effacer();
        }
    }
}
