package dz.tabibi.backend.identite;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.listeattente.application.ListeAttenteService;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.application.RendezVousService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Suppression du compte par l'utilisateur lui-meme (Google Play, loi 18-07), quel que soit son role :
 * ses rendez-vous a venir sont annules (le cabinet est prevenu), il quitte les listes d'attente, son profil
 * est anonymise (nom « Compte supprime », telephone, e-mail et date de naissance effaces) et son compte
 * Keycloak est supprime. Les ordonnances et les avis restent (obligation de conservation, avis deja anonymes
 * en public) : ils ne pointent plus que vers un profil anonyme.
 */
@Service
public class SuppressionCompteService {

    /** Nom affiche a la place de celui d'un compte supprime (agenda passe du cabinet, ordonnances...). */
    public static final String NOM_ANONYME = "Compte supprimé";

    private static final Logger log = LoggerFactory.getLogger(SuppressionCompteService.class);

    private final RendezVousService rendezVous;
    private final ListeAttenteService listeAttente;
    private final ProfilRepository profils;
    private final ComptesUtilisateurs comptes;

    public SuppressionCompteService(RendezVousService rendezVous, ListeAttenteService listeAttente,
                                    ProfilRepository profils, ComptesUtilisateurs comptes) {
        this.rendezVous = rendezVous;
        this.listeAttente = listeAttente;
        this.profils = profils;
        this.comptes = comptes;
    }

    /**
     * Supprime le compte de l'utilisateur. La suppression Keycloak vient en dernier : si elle echoue, rien
     * n'est enregistre (transaction annulee) et l'utilisateur peut recommencer.
     */
    @Transactional
    public void supprimer(UUID utilisateurId) {
        int annules = rendezVous.annulerAVenirDuPatient(utilisateurId);
        // Medecin : le nom et le telephone des patients pris par telephone sont effaces avec son compte.
        int anonymises = rendezVous.anonymiserRendezVousTelephoniques(utilisateurId);
        listeAttente.retirerToutes(utilisateurId);
        String langue = profils.parUtilisateur(utilisateurId).map(Profil::langue).orElse(Profil.LANGUE_PAR_DEFAUT);
        // Le profil anonyme est toujours ecrit : sans lui, le cabinet afficherait encore le nom du compte.
        profils.enregistrer(new Profil(utilisateurId, NOM_ANONYME, null, null, null, langue, Instant.now()));
        comptes.supprimer(utilisateurId);
        log.info("Compte {} supprime a sa demande ({} rendez-vous a venir annules, {} rendez-vous telephoniques anonymises).", utilisateurId, annules, anonymises);
    }
}
