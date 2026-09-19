package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Langue;

/**
 * Langue de la requete en cours, portee par le fil d'execution qui la traite : posee par
 * {@link FiltreLangue} a l'entree, effacee a la sortie (le fil est rendu au pool de Tomcat).
 * Le conseil d'erreurs et le filtre de limitation de debit la lisent pour rendre leur message
 * dans la bonne langue sans que les signatures du domaine aient a la transporter.
 * Hors requete (planificateur, tests unitaires), la langue vaut {@link Langue#PAR_DEFAUT}.
 */
public final class ContexteLangue {

    private static final ThreadLocal<Langue> COURANTE = new ThreadLocal<>();

    private ContexteLangue() { }

    /** La langue de la requete en cours, {@link Langue#PAR_DEFAUT} si aucune n'a ete posee. */
    public static Langue courante() {
        Langue langue = COURANTE.get();
        return langue == null ? Langue.PAR_DEFAUT : langue;
    }

    /** Pose la langue de la requete en cours ; null efface. */
    public static void poser(Langue langue) {
        if (langue == null) {
            effacer();
        } else {
            COURANTE.set(langue);
        }
    }

    /** Efface la langue portee par ce fil d'execution (a faire dans un finally). */
    public static void effacer() {
        COURANTE.remove();
    }
}
