package dz.tabibi.backend.creneaux.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Modele de semaine a repeter : les jours choisis, de {@code debut} a {@code fin} (heure d'Alger),
 * decoupes en creneaux de {@code dureeMinutes}, a partir du {@code aPartirDu} pendant {@code nombreSemaines}.
 */
public record SemaineType(
        List<JourSemaine> jours,
        LocalTime debut,
        LocalTime fin,
        int dureeMinutes,
        LocalDate aPartirDu,
        int nombreSemaines
) {}
