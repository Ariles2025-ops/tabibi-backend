package dz.tabibi.backend.annuaire.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port de l'annuaire : lecture publique, publication d'un praticien (candidature validee). */
public interface MedecinRepository {

    List<Medecin> rechercher(CritereRecherche critere);

    /** Une commune d'une wilaya pour une specialite, avec son nombre de fiches. */
    record CommuneNombre(String slug, String nom, long nombre) {}

    /** Communes ayant au moins {@code minimum} fiches (hors ville vide ou egale a la wilaya), par nombre decroissant. */
    List<CommuneNombre> communes(String specialite, String wilaya, int minimum);

    Optional<Medecin> parId(UUID id);

    /** Publie ou met a jour la fiche d'un praticien (meme identifiant = meme fiche). */
    Medecin enregistrer(Medecin medecin);

    /** Retire une fiche de l'annuaire (fiche importee remplacee par celle du praticien qui l'a reclamee). */
    void supprimer(UUID id);

    /** Toutes les fiches, page par page, dans un ordre stable (plan du site). */
    List<Medecin> page(int numero, int taille);

    /** Nombre total de fiches. */
    long nombre();

    /** L'ancien identifiant d'une fiche (fiche importee reclamee) mene desormais a la fiche {@code nouvelId}. */
    void ajouterAlias(UUID ancienId, UUID nouvelId);

    /** Identifiant actuel de la fiche qui portait autrefois cet identifiant, s'il y en a un. */
    Optional<UUID> aliasDe(UUID ancienId);
}
