package dz.tabibi.backend.annuaire.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface MedecinAliasJpa extends JpaRepository<MedecinAliasEntity, UUID> {
}
