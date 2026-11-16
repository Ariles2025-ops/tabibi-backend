package dz.tabibi.backend.creneaux.domain;

import java.time.DayOfWeek;

/** Jour de la semaine, nomme en francais comme dans l'API (LUNDI, MARDI, ...). */
public enum JourSemaine {
    LUNDI(DayOfWeek.MONDAY),
    MARDI(DayOfWeek.TUESDAY),
    MERCREDI(DayOfWeek.WEDNESDAY),
    JEUDI(DayOfWeek.THURSDAY),
    VENDREDI(DayOfWeek.FRIDAY),
    SAMEDI(DayOfWeek.SATURDAY),
    DIMANCHE(DayOfWeek.SUNDAY);

    private final DayOfWeek jour;

    JourSemaine(DayOfWeek jour) {
        this.jour = jour;
    }

    public DayOfWeek jour() {
        return jour;
    }
}
