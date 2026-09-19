package dz.tabibi.backend.profil.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** La cle est l'identifiant de l'utilisateur : findById et save suffisent au port. */
interface ProfilJpa extends JpaRepository<ProfilEntity, UUID> {
}
