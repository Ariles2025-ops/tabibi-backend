package dz.tabibi.backend.cabinet.domain;

import java.util.UUID;

/** Levee quand le rattachement de secretaire demande n'existe pas. */
public class RattachementIntrouvableException extends RuntimeException {
    public RattachementIntrouvableException(UUID rattachementId) {
        super("Rattachement introuvable : " + rattachementId + ".");
    }
}
