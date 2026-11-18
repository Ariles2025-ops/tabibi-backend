package dz.tabibi.backend.rendezvous.application;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Cloture automatique des rendez-vous passes : un rendez-vous encore CONFIRME dont le debut date de plus de
 * {@link #DELAI} passe a HONORE (ce qui ouvre l'avis du patient), sans notification. Le cabinet a ce delai
 * pour le marquer « absent » ; les rendez-vous annules, absents ou deja honores ne sont pas touches.
 */
@Service
public class ClotureRendezVousService {

    /** Delai laisse au cabinet pour marquer lui-meme le rendez-vous avant la cloture automatique. */
    public static final Duration DELAI = Duration.ofHours(24);

    private final RendezVousRepository rendezVous;
    private final Clock horloge;

    public ClotureRendezVousService(RendezVousRepository rendezVous, Clock horloge) {
        this.rendezVous = rendezVous;
        this.horloge = horloge;
    }

    /** @return le nombre de rendez-vous clos. */
    @Transactional
    public int cloturerLesRendezVousPasses() {
        int clos = 0;
        for (RendezVous rdv : rendezVous.confirmesDebutantAvant(horloge.instant().minus(DELAI))) {
            if (rdv.cloturerAutomatiquement()) {
                rendezVous.enregistrer(rdv);
                clos++;
            }
        }
        return clos;
    }
}
