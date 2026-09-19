package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.application.AuditService;
import dz.tabibi.backend.audit.domain.AdresseIp;
import dz.tabibi.backend.audit.domain.EntreeAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Adaptateur entrant du journal des acces : un filtre servlet, place apres la chaine de Spring
 * Security (le sujet du jeton est connu), qui enregistre chaque requete /api/** (jamais
 * /actuator/**) : methode, chemin sans parametres de requete, statut, adresse IP tronquee, heure et
 * duree. Le corps et les parametres ne sont jamais lus ni journalises (donnees de sante). Un echec
 * d'ecriture du journal est signale (warn) et ne fait jamais echouer la requete.
 * Le filtre pose aussi, pour la duree de la requete, un identifiant de requete dans le MDC
 * (cle {@value #CLE_MDC}) et le renvoie dans l'en-tete {@value #EN_TETE_REQUETE} : toutes les
 * lignes de journal d'une meme requete se recollent, et l'utilisateur qui signale une erreur peut
 * citer cet identifiant. Il vaut celui fourni par le reverse proxy s'il en pose un, sinon un
 * identifiant tire au hasard. Le sujet du jeton, lui, n'entre jamais dans le MDC : les journaux
 * applicatifs ne doivent pas designer d'utilisateur (c'est le role du journal des acces, protege).
 * Enregistre par AuditConfig (FilterRegistrationBean), pas par balayage : les tests web n'en
 * dependent pas.
 */
public class FiltreAudit extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FiltreAudit.class);

    /** Cle du MDC portant l'identifiant de la requete en cours. */
    public static final String CLE_MDC = "requeteId";
    /** En-tete lu (s'il vient d'un proxy de confiance) puis renvoye avec l'identifiant de requete. */
    public static final String EN_TETE_REQUETE = "X-Request-Id";
    /** Longueur maximale d'un identifiant de requete accepte de l'exterieur. */
    static final int LONGUEUR_MAX_REQUETE_ID = 64;

    /** Longueur maximale du chemin journalise (colonne journal_acces.chemin). */
    static final int LONGUEUR_MAX_CHEMIN = 512;
    private static final int PROFONDEUR_MAX_CAUSES = 10;

    private final AuditService service;
    private final Clock horloge;

    public FiltreAudit(AuditService service, Clock horloge) {
        this.service = service;
        this.horloge = horloge;
    }

    /** Seules les requetes de l'API sont journalisees ; jamais la supervision. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        return chemin == null || !chemin.startsWith("/api/") || chemin.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        Instant horodatage = horloge.instant();
        long depart = System.nanoTime();
        UUID sujet = sujetCourant();
        String requeteId = identifiantDe(requete);
        MDC.put(CLE_MDC, requeteId);
        reponse.setHeader(EN_TETE_REQUETE, requeteId);
        try {
            chaine.doFilter(requete, reponse);
        } catch (Exception e) {
            // L'erreur sera traduite plus haut (403 pour un refus de @PreAuthorize, 500 sinon) :
            // on journalise le statut qu'elle produira, puis on la laisse remonter.
            journaliser(requete, sujet, statutDe(e), horodatage, depart);
            throw e;
        } finally {
            MDC.remove(CLE_MDC);
        }
        journaliser(requete, sujet, reponse.getStatus(), horodatage, depart);
    }

    /**
     * Identifiant de la requete : celui pose par le reverse proxy s'il est present et raisonnable
     * (au plus {@value #LONGUEUR_MAX_REQUETE_ID} caracteres, ni espace ni retour a la ligne, pour
     * qu'un client ne puisse pas forger une ligne de journal), un identifiant tire au hasard sinon.
     */
    public static String identifiantDe(HttpServletRequest requete) {
        String fourni = requete.getHeader(EN_TETE_REQUETE);
        if (fourni != null && !fourni.isBlank() && fourni.length() <= LONGUEUR_MAX_REQUETE_ID
                && fourni.chars().noneMatch(Character::isWhitespace)) {
            return fourni;
        }
        return UUID.randomUUID().toString();
    }

    private void journaliser(HttpServletRequest requete, UUID sujet, int statut, Instant horodatage, long depart) {
        long dureeMs = (System.nanoTime() - depart) / 1_000_000;
        try {
            service.enregistrer(EntreeAudit.nouvelle(sujet, requete.getMethod(), abreger(requete.getRequestURI()),
                    statut, AdresseIp.tronquer(requete.getRemoteAddr()), horodatage, dureeMs));
        } catch (RuntimeException e) {
            LOG.warn("Journal des acces indisponible : une entree n'a pas ete enregistree.", e);
        }
    }

    /** Sujet du jeton porte par la requete, null sans jeton (endpoint public) ou si le sujet n'est pas un UUID. */
    private static UUID sujetCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated()
                || authentification instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentification.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Statut que produira une erreur remontee par la chaine : 403 (acces refuse), 401 (non authentifie), 500 sinon. */
    static int statutDe(Throwable erreur) {
        Throwable cause = erreur;
        for (int i = 0; cause != null && i < PROFONDEUR_MAX_CAUSES; i++, cause = cause.getCause()) {
            if (cause instanceof AccessDeniedException) {
                return HttpStatus.FORBIDDEN.value();
            }
            if (cause instanceof AuthenticationException) {
                return HttpStatus.UNAUTHORIZED.value();
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    static String abreger(String chemin) {
        if (chemin == null) {
            return "";
        }
        return chemin.length() <= LONGUEUR_MAX_CHEMIN ? chemin : chemin.substring(0, LONGUEUR_MAX_CHEMIN);
    }
}
