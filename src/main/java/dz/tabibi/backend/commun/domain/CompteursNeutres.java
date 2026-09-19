package dz.tabibi.backend.commun.domain;

/**
 * Realisation neutre du port {@link Compteurs} : elle ne compte rien. Utile la ou la supervision
 * n'a pas de sens (tests unitaires qui ne la verifient pas, cablage a la main d'un service) sans
 * avoir a rendre le port facultatif dans les constructeurs.
 */
public final class CompteursNeutres implements Compteurs {

    /** Instance partagee : l'objet n'a aucun etat. */
    public static final Compteurs INSTANCE = new CompteursNeutres();

    private CompteursNeutres() { }

    @Override
    public void incrementer(String compteur) {
        // Rien a compter.
    }
}
