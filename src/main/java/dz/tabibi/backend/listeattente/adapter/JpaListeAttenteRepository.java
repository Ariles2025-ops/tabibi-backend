package dz.tabibi.backend.listeattente.adapter;

import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur de persistance JPA/PostgreSQL des inscriptions en liste d'attente. Realise le meme
 * port que l'adaptateur en memoire ; le domaine et les cas d'usage sont inchanges.
 */
@Repository
@Profile("postgres")
public class JpaListeAttenteRepository implements ListeAttenteRepository {

    private final ListeAttenteJpa jpa;

    public JpaListeAttenteRepository(ListeAttenteJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public InscriptionAttente enregistrer(InscriptionAttente inscription) {
        jpa.save(InscriptionAttenteEntity.de(inscription));
        return inscription;
    }

    @Override
    public Optional<InscriptionAttente> parId(UUID id) {
        return jpa.findById(id).map(InscriptionAttenteEntity::versDomaine);
    }

    @Override
    public Optional<InscriptionAttente> parPatientEtMedecin(UUID patientId, UUID medecinId) {
        return jpa.findByPatientIdAndMedecinId(patientId, medecinId).map(InscriptionAttenteEntity::versDomaine);
    }

    @Override
    public List<InscriptionAttente> parPatient(UUID patientId) {
        return jpa.findByPatientIdOrderByInscritLeAsc(patientId)
                .stream().map(InscriptionAttenteEntity::versDomaine).toList();
    }

    @Override
    public List<InscriptionAttente> parMedecin(UUID medecinId) {
        return jpa.findByMedecinIdOrderByInscritLeAsc(medecinId)
                .stream().map(InscriptionAttenteEntity::versDomaine).toList();
    }

    @Override
    public void supprimer(UUID id) {
        jpa.deleteById(id);
    }
}
