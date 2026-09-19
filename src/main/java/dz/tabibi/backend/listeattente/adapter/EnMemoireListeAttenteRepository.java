package dz.tabibi.backend.listeattente.adapter;

import dz.tabibi.backend.listeattente.domain.InscriptionAttente;
import dz.tabibi.backend.listeattente.domain.ListeAttenteRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Adaptateur de persistance en memoire des inscriptions en liste d'attente (dev/tests, hors profil postgres). */
@Repository
@Profile("!postgres")
public class EnMemoireListeAttenteRepository implements ListeAttenteRepository {

    /** De la plus ancienne a la plus recente (tri stable : a date egale, l'ordre d'inscription est conserve). */
    private static final Comparator<InscriptionAttente> PLUS_ANCIENNE_D_ABORD =
            Comparator.comparing(InscriptionAttente::inscritLe);

    /** L'ordre d'insertion est conserve pour departager des inscriptions faites au meme instant. */
    private final Map<UUID, InscriptionAttente> parId = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public InscriptionAttente enregistrer(InscriptionAttente inscription) {
        parId.put(inscription.id(), inscription);
        return inscription;
    }

    @Override
    public Optional<InscriptionAttente> parId(UUID id) {
        return Optional.ofNullable(parId.get(id));
    }

    @Override
    public Optional<InscriptionAttente> parPatientEtMedecin(UUID patientId, UUID medecinId) {
        return toutes().stream()
                .filter(i -> i.estDe(patientId) && i.concerne(medecinId))
                .findFirst();
    }

    @Override
    public List<InscriptionAttente> parPatient(UUID patientId) {
        return triees(i -> i.estDe(patientId));
    }

    @Override
    public List<InscriptionAttente> parMedecin(UUID medecinId) {
        return triees(i -> i.concerne(medecinId));
    }

    @Override
    public void supprimer(UUID id) {
        parId.remove(id);
    }

    private List<InscriptionAttente> triees(Predicate<InscriptionAttente> critere) {
        return toutes().stream().filter(critere).sorted(PLUS_ANCIENNE_D_ABORD).toList();
    }

    /** Instantane des inscriptions dans l'ordre d'enregistrement. */
    private List<InscriptionAttente> toutes() {
        synchronized (parId) {
            return List.copyOf(parId.values());
        }
    }
}
