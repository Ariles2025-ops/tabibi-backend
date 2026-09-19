package dz.tabibi.backend.commun;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge de test que l'on avance a la main. */
public final class HorlogeReglable extends Clock {

    private Instant instant;

    public HorlogeReglable(Instant depart) {
        this.instant = depart;
    }

    public void avancer(Duration duree) {
        instant = instant.plus(duree);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
