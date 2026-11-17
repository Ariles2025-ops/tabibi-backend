package dz.tabibi.backend.profil.application;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import dz.tabibi.backend.profil.domain.Profil;
import dz.tabibi.backend.profil.domain.ProfilRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Noms et coordonnees des patients, pour les ecrans du cabinet (agenda, liste d'attente, teleconsultations,
 * ordonnances) : le nom du profil Tabibi s'il est renseigne, sinon celui du compte. Un medecin ne voit que
 * ses propres patients (au moins un rendez-vous ou une inscription sur sa liste d'attente).
 */
@Service
public class PatientsService {

    /** Ce que le cabinet voit d'un patient. */
    public record PatientResume(UUID patientId, String nomComplet, String telephone, String email) {}

    private final RendezVousRepository rendezVous;
    private final ListeAttenteRepository listeAttente;
    private final ProfilRepository profils;
    private final ComptesUtilisateurs comptes;

    public PatientsService(RendezVousRepository rendezVous, ListeAttenteRepository listeAttente,
                           ProfilRepository profils, ComptesUtilisateurs comptes) {
        this.rendezVous = rendezVous;
        this.listeAttente = listeAttente;
        this.profils = profils;
        this.comptes = comptes;
    }

    /**
     * Les patients d'un medecin, avec leur nom. Telephone et e-mail ne sont donnes que pour les patients qui ont
     * eu au moins un rendez-vous avec lui (loi 18-07) : un simple inscrit en liste d'attente n'apparait qu'avec son nom.
     */
    public List<PatientResume> patientsDuMedecin(UUID medecinId) {
        Set<UUID> avecRendezVous = new LinkedHashSet<>();
        rendezVous.parMedecin(medecinId).stream().map(RendezVous::patientId).filter(java.util.Objects::nonNull).forEach(avecRendezVous::add);
        Set<UUID> enAttenteSeulement = new LinkedHashSet<>();
        listeAttente.parMedecin(medecinId).stream().map(i -> i.patientId())
                .filter(id -> !avecRendezVous.contains(id)).forEach(enAttenteSeulement::add);
        List<PatientResume> patients = new java.util.ArrayList<>(resumes(avecRendezVous));
        enAttenteSeulement.stream().map(this::resume)
                .map(p -> new PatientResume(p.patientId(), p.nomComplet(), null, null))
                .forEach(patients::add);
        return patients;
    }

    /** Resumes d'une liste de comptes (administration). */
    public List<PatientResume> resumes(Collection<UUID> ids) {
        return ids.stream().map(this::resume).toList();
    }

    private PatientResume resume(UUID id) {
        Optional<Profil> profil = profils.parUtilisateur(id);
        Optional<ComptesUtilisateurs.InfosCompte> compte = comptes.infos(id);
        String nom = profil.map(Profil::nomComplet).filter(n -> n != null && !n.isBlank())
                .or(() -> compte.map(ComptesUtilisateurs.InfosCompte::nomComplet))
                .orElse("Patient");
        // Le numero verifie par SMS (compte Keycloak) passe avant celui saisi librement dans le profil.
        String telephone = compte.map(ComptesUtilisateurs.InfosCompte::telephone).filter(t -> t != null && !t.isBlank())
                .or(() -> profil.map(Profil::telephone).filter(t -> t != null && !t.isBlank()))
                .orElse(null);
        String email = compte.map(ComptesUtilisateurs.InfosCompte::email).orElse(null);
        return new PatientResume(id, nom, telephone, email);
    }
}
