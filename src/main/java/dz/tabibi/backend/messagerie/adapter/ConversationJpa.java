package dz.tabibi.backend.messagerie.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConversationJpa extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByPatientIdAndMedecinId(UUID patientId, UUID medecinId);

    /** Conversations ou l'utilisateur est patient ou medecin (le meme identifiant est passe deux fois). */
    List<ConversationEntity> findByPatientIdOrMedecinIdOrderByDernierMessageLeDesc(UUID patientId, UUID medecinId);
}
