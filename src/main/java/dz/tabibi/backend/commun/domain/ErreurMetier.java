package dz.tabibi.backend.commun.domain;

/**
 * Racine des erreurs metier de la plateforme. Elle porte, en plus du message brut,
 * une <strong>cle de traduction facultative</strong> et ses parametres : quand la cle est
 * presente, la couche web ({@code GestionErreursApi}) rend le message dans la langue demandee
 * par la requete ; sinon elle renvoie le message brut, comme avant.
 * Le domaine ne connait donc aucun catalogue de textes : il nomme l'erreur, la traduction est
 * une affaire d'adaptateur.
 */
public abstract class ErreurMetier extends RuntimeException {

    private static final Object[] SANS_PARAMETRE = new Object[0];

    private final transient String cle;
    private final transient Object[] params;

    /** Erreur a message brut, sans traduction (comportement historique). */
    protected ErreurMetier(String message) {
        super(message);
        this.cle = null;
        this.params = SANS_PARAMETRE;
    }

    /**
     * Erreur traduisible : {@code message} reste le texte brut (journaux, appelants qui lisent
     * {@code getMessage()}), {@code cle} designe l'entree du catalogue de messages et
     * {@code params} ses valeurs ({@code {0}}, {@code {1}}...).
     */
    protected ErreurMetier(String message, String cle, Object... params) {
        super(message);
        this.cle = cle;
        this.params = params == null ? SANS_PARAMETRE : params.clone();
    }

    /** Cle de traduction de l'erreur, null si elle n'en a pas (le message brut fait foi). */
    public String cle() {
        return cle;
    }

    /** Parametres de la traduction, dans l'ordre des reperes {@code {0}}, {@code {1}}... ; jamais null. */
    public Object[] params() {
        return params.clone();
    }

    /** Vrai si l'erreur peut etre rendue dans la langue de l'appelant. */
    public boolean estTraduisible() {
        return cle != null;
    }
}
