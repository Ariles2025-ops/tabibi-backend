package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.Compteurs;
import dz.tabibi.backend.commun.domain.CompteursNeutres;
import dz.tabibi.backend.commun.domain.Langue;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Filtre servlet de limitation de debit par adresse IP sur les routes publiques (annuaire,
 * verification d'ordonnance) et les routes de publication sensibles aux abus (besoins Dawini,
 * conversations, avis). Place avant la chaine de Spring Security : une rafale est refusee sans
 * meme decoder un jeton. Une requete de trop recoit 429 { "erreur": "..." } avec l'en-tete
 * Retry-After (secondes), le message etant rendu dans la langue demandee par la requete
 * (Accept-Language, voir FiltreLangue) ; les autres routes ne sont jamais concernees.
 * L'adresse du client est le premier element de X-Forwarded-For quand l'application est derriere
 * un reverse proxy qui le renseigne (server.forward-headers-strategy actif), sinon l'adresse
 * distante de la connexion : hors proxy, l'en-tete est ignore car n'importe qui peut l'ecrire.
 * Enregistre par LimiteDebitConfig (FilterRegistrationBean), pas par balayage : les tests web
 * n'en dependent pas.
 */
public class FiltreLimiteDebit extends OncePerRequestFilter {

    /** Corps de la reponse 429, au format des erreurs de l'API ; texte francais, langue par defaut. */
    public static final String MESSAGE_REFUS = Messages.partagees().message(Langue.FR, Cles.LIMITE_DEBIT);
    static final String CORPS_REFUS = corps(MESSAGE_REFUS);
    static final String EN_TETE_TRANSFERT = "X-Forwarded-For";
    /** Cle des requetes dont l'adresse est inconnue : elles partagent un seul seau. */
    static final String CLE_INCONNUE = "inconnue";

    /**
     * Une route limitee : methode HTTP, chemin exact ou prefixe (termine par /** : le chemin lui-meme
     * et tout ce qui est dessous), et le limiteur qui lui est propre.
     */
    public record Regle(String methode, String chemin, LimiteurDebit limiteur) {

        public Regle {
            if (methode == null || methode.isBlank() || chemin == null || !chemin.startsWith("/") || limiteur == null) {
                throw new IllegalArgumentException("Regle de limitation incomplete.");
            }
        }

        boolean concerne(String methodeRequete, String cheminRequete) {
            if (!methode.equalsIgnoreCase(methodeRequete) || cheminRequete == null) {
                return false;
            }
            if (chemin.endsWith("/**")) {
                String prefixe = chemin.substring(0, chemin.length() - 3);
                return cheminRequete.equals(prefixe) || cheminRequete.startsWith(prefixe + "/");
            }
            return cheminRequete.equals(chemin);
        }
    }

    private final List<Regle> regles;
    private final boolean enTetesTransferesActifs;
    private final Messages messages;
    private final Compteurs compteurs;

    /**
     * @param regles                  routes limitees, dans l'ordre d'evaluation (la premiere qui correspond s'applique)
     * @param enTetesTransferesActifs vrai derriere un reverse proxy de confiance (X-Forwarded-For pris en compte)
     */
    public FiltreLimiteDebit(List<Regle> regles, boolean enTetesTransferesActifs) {
        this(regles, enTetesTransferesActifs, Messages.partagees(), CompteursNeutres.INSTANCE);
    }

    /** Meme filtre avec un catalogue de messages et des compteurs explicites (production, tests). */
    public FiltreLimiteDebit(List<Regle> regles, boolean enTetesTransferesActifs, Messages messages,
                             Compteurs compteurs) {
        this.regles = List.copyOf(regles);
        this.enTetesTransferesActifs = enTetesTransferesActifs;
        this.messages = messages;
        this.compteurs = compteurs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return regleApplicable(requete) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine)
            throws ServletException, IOException {
        Regle regle = regleApplicable(requete);
        if (regle != null) {
            LimiteurDebit.Decision decision = regle.limiteur().tenter(cleDe(requete));
            if (!decision.autorise()) {
                compteurs.incrementer(Compteurs.LIMITE_DEPASSEMENTS);
                refuser(reponse, decision.attenteSecondes(),
                        messages.message(ContexteLangue.courante(), Cles.LIMITE_DEBIT));
                return;
            }
        }
        chaine.doFilter(requete, reponse);
    }

    private Regle regleApplicable(HttpServletRequest requete) {
        String methode = requete.getMethod();
        String chemin = requete.getRequestURI();
        for (Regle regle : regles) {
            if (regle.concerne(methode, chemin)) {
                return regle;
            }
        }
        return null;
    }

    /**
     * Cle de limitation d'une requete, l'adresse du client : premier element de X-Forwarded-For
     * derriere un proxy de confiance, sinon l'adresse distante ; "inconnue" si aucune n'est connue.
     */
    public String cleDe(HttpServletRequest requete) {
        if (enTetesTransferesActifs) {
            String transfere = requete.getHeader(EN_TETE_TRANSFERT);
            if (transfere != null && !transfere.isBlank()) {
                String premiere = transfere.split(",", 2)[0].strip();
                if (!premiere.isEmpty()) {
                    return premiere;
                }
            }
        }
        String distante = requete.getRemoteAddr();
        return distante == null || distante.isBlank() ? CLE_INCONNUE : distante;
    }

    private static void refuser(HttpServletResponse reponse, long attenteSecondes, String message) throws IOException {
        reponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        reponse.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(attenteSecondes));
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        reponse.getWriter().write(corps(message));
        reponse.getWriter().flush();
    }

    /** Le corps JSON d'un refus, au format des erreurs de l'API : { "erreur": "..." }. */
    static String corps(String message) {
        return "{\"erreur\":\"" + message + "\"}";
    }
}
