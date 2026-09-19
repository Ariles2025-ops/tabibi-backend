package dz.tabibi.backend.commun.domain;

/**
 * Port de supervision : les cas d'usage comptent ce qui se passe de notable (rendez-vous reserves,
 * ordonnances emises...) sans rien savoir du systeme de metriques. Un adaptateur Micrometer expose
 * ces compteurs a Prometheus ; {@link CompteursNeutres} ne fait rien, pour les tests et les
 * cablages a la main.
 * <p>
 * Un compteur ne porte <strong>jamais</strong> d'identifiant d'utilisateur ni de donnee de sante :
 * la supervision compte des evenements, elle ne raconte pas qui a fait quoi (c'est le role du
 * journal des acces, protege, lui).
 */
public interface Compteurs {

    /** Un rendez-vous a ete reserve (creneau de l'agenda ou horaire libre). */
    String RENDEZVOUS_RESERVES = "tabibi.rendezvous.reserves";
    /** Un rendez-vous a ete annule, par le patient ou par le cabinet. */
    String RENDEZVOUS_ANNULES = "tabibi.rendezvous.annules";
    /** Une ordonnance a ete redigee par un medecin. */
    String ORDONNANCES_EMISES = "tabibi.ordonnances.emises";
    /** Une teleconsultation a ete demarree par un medecin. */
    String TELECONSULTATIONS_DEMARREES = "tabibi.teleconsultations.demarrees";
    /** Un avis a ete depose par un patient. */
    String AVIS_DEPOSES = "tabibi.avis.deposes";
    /** Une requete a ete refusee par la limitation de debit (429). */
    String LIMITE_DEPASSEMENTS = "tabibi.limite.depassements";

    /** Ajoute un a ce compteur. */
    void incrementer(String compteur);
}
