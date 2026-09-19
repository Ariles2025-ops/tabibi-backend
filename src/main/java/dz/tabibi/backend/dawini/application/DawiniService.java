package dz.tabibi.backend.dawini.application;

import dz.tabibi.backend.commun.domain.AccesRefuseException;
import dz.tabibi.backend.commun.domain.Cles;
import dz.tabibi.backend.commun.domain.TransitionInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinIntrouvableException;
import dz.tabibi.backend.dawini.domain.BesoinInvalideException;
import dz.tabibi.backend.dawini.domain.BesoinMedicament;
import dz.tabibi.backend.dawini.domain.BesoinRepository;
import dz.tabibi.backend.dawini.domain.DemandeBesoin;
import dz.tabibi.backend.dawini.domain.DemandeReponse;
import dz.tabibi.backend.dawini.domain.ReponseInvalideException;
import dz.tabibi.backend.dawini.domain.ReponsePharmacie;
import dz.tabibi.backend.dawini.domain.ReponseRepository;
import dz.tabibi.backend.notifications.domain.Notifieur;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cas d'usage de Dawini : un patient publie un besoin de medicament dans sa wilaya, les pharmacies de
 * la wilaya le voient (sans l'identite du patient) et y repondent une fois chacune, le patient est
 * prevenu de chaque reponse par le port Notifieur et cloture son besoin quand il est satisfait.
 */
@Service
public class DawiniService {

    private final BesoinRepository besoins;
    private final ReponseRepository reponses;
    private final Notifieur notifieur;

    public DawiniService(BesoinRepository besoins, ReponseRepository reponses, Notifieur notifieur) {
        this.besoins = besoins;
        this.reponses = reponses;
        this.notifieur = notifieur;
    }

    /**
     * Publie un besoin ouvert pour le patient connecte.
     * @throws BesoinInvalideException si le medicament ou la wilaya manque, ou si une donnee est trop longue.
     */
    @Transactional
    public BesoinMedicament publier(UUID patientId, DemandeBesoin demande) {
        return besoins.enregistrer(BesoinMedicament.publier(patientId, demande, Instant.now()));
    }

    /** Besoins du patient, tous statuts, les plus recents d'abord. */
    public List<BesoinMedicament> mesBesoins(UUID patientId) {
        return besoins.parPatient(patientId);
    }

    /**
     * Le patient cloture l'un de ses besoins.
     * @throws BesoinIntrouvableException si le besoin n'existe pas.
     * @throws AccesRefuseException s'il a ete publie par un autre patient.
     * @throws TransitionInvalideException s'il est deja cloture.
     */
    @Transactional
    public BesoinMedicament cloturer(UUID patientId, UUID besoinId) {
        return besoins.enregistrer(chargerPourLePatient(patientId, besoinId).cloturer(Instant.now()));
    }

    /**
     * Besoins ouverts d'une wilaya, pour les pharmacies, les plus recents d'abord.
     * @throws BesoinInvalideException si la wilaya manque.
     */
    public List<BesoinMedicament> besoinsOuverts(String wilayaCode) {
        if (wilayaCode == null || wilayaCode.isBlank()) {
            throw new BesoinInvalideException("La wilaya est obligatoire.", Cles.BESOIN_WILAYA_OBLIGATOIRE);
        }
        return besoins.ouvertsParWilaya(wilayaCode.strip());
    }

    /**
     * Une pharmacie repond a un besoin ouvert (une seule fois) ; le patient est prevenu, sans detail.
     * @throws BesoinIntrouvableException si le besoin n'existe pas.
     * @throws TransitionInvalideException s'il est cloture ou si cette pharmacie a deja repondu.
     * @throws ReponseInvalideException si la reponse est incomplete ou incoherente.
     */
    @Transactional
    public ReponsePharmacie repondre(UUID pharmacieId, UUID besoinId, DemandeReponse demande) {
        BesoinMedicament besoin = charger(besoinId);
        if (!besoin.estOuvert()) {
            throw new TransitionInvalideException("Ce besoin est cloture : il n'accepte plus de reponse.", Cles.BESOIN_CLOTURE);
        }
        if (reponses.parBesoinEtPharmacie(besoinId, pharmacieId).isPresent()) {
            throw new TransitionInvalideException("Votre pharmacie a deja repondu a ce besoin.", Cles.BESOIN_DEJA_REPONDU);
        }
        ReponsePharmacie reponse = reponses.enregistrer(
                ReponsePharmacie.repondre(besoin.id(), pharmacieId, demande, Instant.now()));
        notifieur.notifier(besoin.patientId(), Cles.NOTIF_DAWINI_REPONSE_SUJET, Cles.NOTIF_DAWINI_REPONSE_MESSAGE);
        return reponse;
    }

    /**
     * Reponses a un besoin, pour le patient qui l'a publie, les plus anciennes d'abord.
     * @throws BesoinIntrouvableException si le besoin n'existe pas.
     * @throws AccesRefuseException s'il a ete publie par un autre patient.
     */
    public List<ReponsePharmacie> reponsesPourPatient(UUID patientId, UUID besoinId) {
        return reponses.parBesoin(chargerPourLePatient(patientId, besoinId).id());
    }

    /**
     * Reponses a un besoin, pour une pharmacie, les plus anciennes d'abord.
     * @throws BesoinIntrouvableException si le besoin n'existe pas.
     */
    public List<ReponsePharmacie> reponsesPourPharmacie(UUID besoinId) {
        return reponses.parBesoin(charger(besoinId).id());
    }

    /** Nombre de reponses recues par un besoin. */
    public long nombreReponses(UUID besoinId) {
        return reponses.compterParBesoin(besoinId);
    }

    private BesoinMedicament charger(UUID besoinId) {
        return besoins.parId(besoinId).orElseThrow(() -> new BesoinIntrouvableException(besoinId));
    }

    private BesoinMedicament chargerPourLePatient(UUID patientId, UUID besoinId) {
        BesoinMedicament besoin = charger(besoinId);
        if (!besoin.estDe(patientId)) {
            throw new AccesRefuseException("Ce besoin ne vous appartient pas.", Cles.BESOIN_AUTRE);
        }
        return besoin;
    }
}
