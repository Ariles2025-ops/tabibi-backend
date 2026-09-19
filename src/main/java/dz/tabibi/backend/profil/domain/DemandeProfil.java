package dz.tabibi.backend.profil.domain;

import java.time.LocalDate;

/**
 * Ce que l'utilisateur declare en renseignant son profil : nom complet, telephone, date de
 * naissance, wilaya et langue preferee. Seul le nom complet est obligatoire ; la validation
 * se fait a l'enregistrement ({@link Profil#renseigner}).
 */
public record DemandeProfil(
        String nomComplet,
        String telephone,
        LocalDate dateNaissance,
        String wilayaCode,
        String langue
) {}
