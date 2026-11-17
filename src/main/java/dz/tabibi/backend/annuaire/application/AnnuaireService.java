package dz.tabibi.backend.annuaire.application;

import dz.tabibi.backend.annuaire.domain.CritereRecherche;
import dz.tabibi.backend.annuaire.domain.Medecin;
import dz.tabibi.backend.annuaire.domain.MedecinRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cas d'usage de l'annuaire : rechercher des praticiens, consulter une fiche. */
@Service
public class AnnuaireService {

    private final MedecinRepository repository;

    public AnnuaireService(MedecinRepository repository) {
        this.repository = repository;
    }

    public List<Medecin> rechercher(CritereRecherche critere) {
        return repository.rechercher(critere);
    }

    /**
     * Fiche d'un praticien. Un ancien identifiant (fiche importee puis reclamee par le praticien) mene a la
     * fiche actuelle, qui porte son nouvel identifiant : les adresses deja indexees restent valables.
     */
    public Optional<Medecin> parId(UUID id) {
        Optional<Medecin> fiche = repository.parId(id);
        UUID courant = id;
        // Quelques sauts au plus (une fiche reclamee n'est normalement jamais reclamee a nouveau).
        for (int saut = 0; fiche.isEmpty() && saut < 3; saut++) {
            Optional<UUID> suivant = repository.aliasDe(courant);
            if (suivant.isEmpty()) {
                break;
            }
            courant = suivant.get();
            fiche = repository.parId(courant);
        }
        return fiche;
    }

    public List<MedecinRepository.CommuneNombre> communes(String specialite, String wilaya) {
        return repository.communes(blanc(specialite), blanc(wilaya), 3);
    }

    private static String blanc(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public List<Medecin> page(int numero, int taille) {
        return repository.page(numero, taille);
    }

    public long nombre() {
        return repository.nombre();
    }
}
