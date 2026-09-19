package dz.tabibi.backend.audit.adapter;

import dz.tabibi.backend.audit.domain.EntreeAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Representation persistante d'une entree du journal des acces (table journal_acces). */
@Entity
@Table(name = "journal_acces")
class JournalAccesEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "sujet")
    private UUID sujet;

    @Column(name = "methode", nullable = false, length = 10)
    private String methode;

    @Column(name = "chemin", nullable = false, length = 512)
    private String chemin;

    @Column(name = "statut", nullable = false)
    private int statut;

    @Column(name = "adresse_ip", length = 45)
    private String adresseIp;

    @Column(name = "horodatage", nullable = false)
    private Instant horodatage;

    @Column(name = "duree_ms", nullable = false)
    private long dureeMs;

    protected JournalAccesEntity() { }

    static JournalAccesEntity de(EntreeAudit e) {
        JournalAccesEntity entite = new JournalAccesEntity();
        entite.id = e.id();
        entite.sujet = e.sujet();
        entite.methode = e.methode();
        entite.chemin = e.chemin();
        entite.statut = e.statut();
        entite.adresseIp = e.adresseIp();
        entite.horodatage = e.horodatage();
        entite.dureeMs = e.dureeMs();
        return entite;
    }

    EntreeAudit versDomaine() {
        return new EntreeAudit(id, sujet, methode, chemin, statut, adresseIp, horodatage, dureeMs);
    }
}
