package dz.tabibi.backend.demandes.application;

import dz.tabibi.backend.commun.domain.Courriel;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.demandes.domain.DemandeIntrouvableException;
import dz.tabibi.backend.demandes.domain.DemandePraticien;
import dz.tabibi.backend.demandes.domain.DemandePraticienRepository;
import dz.tabibi.backend.demandes.domain.StatutDemande;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Demandes des praticiens sans compte : enregistrement, alerte au fondateur, suivi par l'administrateur. */
@Service
public class DemandePraticienService {

    private static final Logger log = LoggerFactory.getLogger(DemandePraticienService.class);

    private final DemandePraticienRepository demandes;
    private final Courriel courriel;
    private final Clock horloge;
    private final String destinataire;
    private final String siteDomaine;

    public DemandePraticienService(DemandePraticienRepository demandes, Courriel courriel, Clock horloge,
                                   @Value("${tabibi.demandes.alerte:contact@ndawik.com}") String destinataire,
                                   @Value("${SITE_DOMAINE:ndawik.com}") String siteDomaine) {
        this.demandes = demandes;
        this.courriel = courriel;
        this.horloge = horloge;
        this.destinataire = destinataire;
        this.siteDomaine = siteDomaine;
    }

    /** Enregistre la demande (400 generique si invalide) puis alerte le fondateur ; un echec d'envoi n'annule rien. */
    public DemandePraticien deposer(String nom, String specialite, String wilaya, String ville, String numeroOrdre,
                                    String telephone, String email, String message, UUID ficheId) {
        DemandePraticien d = demandes.enregistrer(DemandePraticien.creer(UUID.randomUUID(), nom, specialite, wilaya,
                ville, numeroOrdre, telephone, email, message, ficheId, horloge.instant()));
        try {
            if (courriel.disponible()) {
                courriel.envoyer(destinataire, "Nouvelle demande praticien — " + d.nom(), corps(d));
            }
        } catch (RuntimeException e) {
            log.warn("Alerte e-mail de la demande praticien {} non envoyee : {}", d.id(), e.getClass().getSimpleName());
        }
        return d;
    }

    public List<DemandePraticien> lister(Optional<StatutDemande> statut) {
        return demandes.lister(statut);
    }

    public DemandePraticien traiter(UUID id) {
        return cloturer(id, StatutDemande.TRAITEE);
    }

    public DemandePraticien refuser(UUID id) {
        return cloturer(id, StatutDemande.REFUSEE);
    }

    private DemandePraticien cloturer(UUID id, StatutDemande nouveau) {
        DemandePraticien d = demandes.parId(id).orElseThrow(DemandeIntrouvableException::new);
        if (d.statut() != StatutDemande.NOUVELLE) {
            throw new TransitionInvalideException("Cette demande a déjà été clôturée.");
        }
        return demandes.enregistrer(d.clôturee(nouveau, horloge.instant()));
    }

    private String corps(DemandePraticien d) {
        StringBuilder s = new StringBuilder("Nouvelle demande d'un praticien (sans compte) :\n\n")
                .append("Nom : ").append(d.nom()).append('\n')
                .append("Spécialité : ").append(d.specialite()).append('\n')
                .append("Wilaya : ").append(d.wilaya()).append('\n')
                .append("Ville : ").append(d.ville() == null ? "—" : d.ville()).append('\n')
                .append("N° d'ordre : ").append(d.numeroOrdre()).append('\n')
                .append("Téléphone : ").append(d.telephone()).append('\n')
                .append("E-mail : ").append(d.email() == null ? "—" : d.email()).append('\n')
                .append("Message : ").append(d.message() == null ? "—" : d.message()).append('\n');
        if (d.ficheId() != null) {
            s.append("Fiche : https://").append(siteDomaine).append("/medecins/").append(d.ficheId()).append('\n');
        }
        return s.append("\nÀ traiter dans l'administration (demandes praticien).\n").toString();
    }
}
