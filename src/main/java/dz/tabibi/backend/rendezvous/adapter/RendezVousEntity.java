package dz.tabibi.backend.rendezvous.adapter;

import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.SourceRdv;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'un rendez-vous (table rendez_vous). */
@Entity
@Table(name = "rendez_vous")
class RendezVousEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "medecin_id", nullable = false)
    private UUID medecinId;

    @Column(name = "debut", nullable = false)
    private Instant debut;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutRdv statut;

    /** Creneau de l'agenda reserve ; absent pour un rendez-vous pris sur un horaire libre. */
    @Column(name = "creneau_id")
    private UUID creneauId;

    /** Date d'envoi du rappel de la veille ; absente tant qu'il n'a pas ete envoye. */
    @Column(name = "rappel_envoye_le")
    private Instant rappelEnvoyeLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SourceRdv source;

    @Column(name = "nom_patient", length = 200)
    private String nomPatient;

    @Column(name = "telephone_patient", length = 40)
    private String telephonePatient;

    @Column(name = "motif", length = 500)
    private String motif;

    @Column(name = "sms_echecs", nullable = false)
    private int smsEchecs;

    @Column(name = "sms_a_reprendre", nullable = false)
    private boolean smsAReprendre;

    protected RendezVousEntity() { }

    static RendezVousEntity de(RendezVous r) {
        RendezVousEntity e = new RendezVousEntity();
        e.id = r.id();
        e.patientId = r.patientId();
        e.medecinId = r.medecinId();
        e.debut = r.debut();
        e.statut = r.statut();
        e.creneauId = r.creneauId();
        e.rappelEnvoyeLe = r.rappelEnvoyeLe();
        e.source = r.source();
        e.nomPatient = r.nomPatient();
        e.telephonePatient = r.telephonePatient();
        e.motif = r.motif();
        e.smsEchecs = r.smsEchecs();
        e.smsAReprendre = r.smsAReprendre();
        return e;
    }

    RendezVous versDomaine() {
        return new RendezVous(id, patientId, medecinId, debut, statut, creneauId, rappelEnvoyeLe,
                source, nomPatient, telephonePatient, motif, smsEchecs, smsAReprendre);
    }

    UUID getMedecinId() { return medecinId; }
    Instant getDebut() { return debut; }
    StatutRdv getStatut() { return statut; }
}
