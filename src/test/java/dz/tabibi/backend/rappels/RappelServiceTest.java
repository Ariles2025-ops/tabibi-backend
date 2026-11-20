package dz.tabibi.backend.rappels;

import dz.tabibi.backend.notifications.domain.Notifieur;
import dz.tabibi.backend.rappels.application.RappelService;
import dz.tabibi.backend.rendezvous.adapter.EnMemoireRendezVousRepository;
import dz.tabibi.backend.rendezvous.domain.RendezVous;
import dz.tabibi.backend.rendezvous.domain.StatutRdv;
import dz.tabibi.backend.rendezvous.domain.RendezVousRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rappels a heure fixe : un rendez-vous confirme qui commence dans les 24 prochaines heures vaut un
 * rappel au patient, une seule fois ; les autres (plus lointains, passes, deja rappeles, annules, honores)
 * sont ignores.
 */
class RappelServiceTest {

    /** 6 decembre 2026, 10:00 UTC (11:00 a Alger). */
    private static final Instant MAINTENANT = Instant.parse("2026-12-06T10:00:00Z");

    /** Faux notifieur : memorise les appels pour verifier qui a ete prevenu, et de quoi. */
    static class FauxNotifieur implements Notifieur {
        record Appel(UUID destinataireId, String sujet, String message) {}

        final List<Appel> appels = new ArrayList<>();

        @Override
        public void notifier(UUID destinataireId, String sujet, String message) {
            appels.add(new Appel(destinataireId, sujet, message));
        }

        List<Appel> pour(UUID destinataireId) {
            return appels.stream().filter(a -> a.destinataireId().equals(destinataireId)).toList();
        }
    }

    private final RendezVousRepository rendezVous = new EnMemoireRendezVousRepository();
    private final FauxNotifieur notifieur = new FauxNotifieur();
    private final RappelService service =
            new RappelService(rendezVous, notifieur, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    /** Rendez-vous confirme enregistre, qui commence dans ce delai a partir de l'heure fixe. */
    private RendezVous confirmeDans(Duration delai) {
        return rendezVous.enregistrer(RendezVous.confirmer(UUID.randomUUID(), UUID.randomUUID(), MAINTENANT.plus(delai)));
    }

    private RendezVous recharge(RendezVous rdv) {
        return rendezVous.parId(rdv.id()).orElseThrow();
    }

    @Test
    void le_rappel_part_aussi_par_sms_au_numero_verifie_et_un_echec_sms_ne_bloque_rien() {
        RendezVous avecTel = confirmeDans(Duration.ofHours(2));
        RendezVous sansTel = confirmeDans(Duration.ofHours(3));
        RendezVous passerelleKo = confirmeDans(Duration.ofHours(4));
        List<String> envoyes = new java.util.ArrayList<>();
        service.setSms(new dz.tabibi.backend.rappels.domain.EnvoiSms() {
            public boolean disponible() { return true; }
            public void envoyer(String tel, String texte) {
                if (tel.equals("+213770000000")) throw new IllegalStateException("ERR 1001");
                envoyes.add(tel + " | " + texte);
            }
        }, new dz.tabibi.backend.commun.domain.ComptesUtilisateurs() {
            public boolean disponible() { return true; }
            public java.util.Optional<InfosCompte> infos(UUID c) {
                if (c.equals(avecTel.patientId())) return java.util.Optional.of(new InfosCompte(c, "A", null, "0550 12 34 56"));
                if (c.equals(passerelleKo.patientId())) return java.util.Optional.of(new InfosCompte(c, "B", null, "+213770000000"));
                return java.util.Optional.of(new InfosCompte(c, "C", null, null));
            }
            public java.util.Optional<UUID> parEmail(String e) { return java.util.Optional.empty(); }
            public void accorderRole(UUID c, String r) { }
            public void supprimer(UUID c) { }
        });

        assertThat(service.executer()).isEqualTo(3);

        assertThat(envoyes).containsExactly("+213550123456 | Ndawik : rappel de votre rendez-vous le 06/12/2026 à 13:00. Pour annuler : ndawik.com/mes-rendez-vous");
        assertThat(notifieur.appels).hasSize(3);
        for (RendezVous r : List.of(avecTel, sansTel, passerelleKo)) {
            assertThat(recharge(r).rappelEnvoye()).isTrue();
        }
    }

    @Test
    void sans_numero_de_compte_le_sms_part_au_numero_du_profil_et_un_email_est_envoye() {
        RendezVous rdv = confirmeDans(Duration.ofHours(2));
        dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository profils = new dz.tabibi.backend.profil.adapter.EnMemoireProfilRepository();
        profils.enregistrer(new dz.tabibi.backend.profil.domain.Profil(rdv.patientId(), "Amina", "0661234567", null, null, "fr", MAINTENANT));
        List<String> sms = new ArrayList<>();
        List<String> courriels = new ArrayList<>();
        service.setProfils(profils);
        service.setSms(new dz.tabibi.backend.rappels.domain.EnvoiSms() {
            public boolean disponible() { return true; }
            public void envoyer(String tel, String texte) { sms.add(tel); }
        }, new dz.tabibi.backend.commun.FauxComptes().avec(rdv.patientId(), "Amina", "amina@exemple.dz", null));
        service.setCourriel(new dz.tabibi.backend.commun.domain.Courriel() {
            public boolean disponible() { return true; }
            public void envoyer(String d, String sujet, String texte) { courriels.add(d); }
        });

        service.executer();

        assertThat(sms).containsExactly("+213661234567");
        assertThat(courriels).containsExactly("amina@exemple.dz");
    }

    @Test
    void normalise_les_numeros_algeriens() {
        assertThat(dz.tabibi.backend.rappels.domain.EnvoiSms.normaliser("0550 12 34 56")).isEqualTo("+213550123456");
        assertThat(dz.tabibi.backend.rappels.domain.EnvoiSms.normaliser("213661234567")).isEqualTo("+213661234567");
        assertThat(dz.tabibi.backend.rappels.domain.EnvoiSms.normaliser("+213 7 71 23 45 67")).isEqualTo("+213771234567");
        assertThat(dz.tabibi.backend.rappels.domain.EnvoiSms.normaliser("021 23 45 67")).isNull();
        assertThat(dz.tabibi.backend.rappels.domain.EnvoiSms.normaliser(null)).isNull();
    }

    @Test
    void un_rendezvous_dans_deux_heures_est_rappele_au_patient_et_marque() {
        RendezVous rdv = confirmeDans(Duration.ofHours(2));
        assertThat(rdv.rappelEnvoye()).isFalse();

        int nombre = service.executer();

        assertThat(nombre).isEqualTo(1);
        assertThat(notifieur.appels).hasSize(1);
        assertThat(notifieur.pour(rdv.patientId()).get(0).sujet()).isEqualTo("Rappel de rendez-vous");
        assertThat(notifieur.pour(rdv.patientId()).get(0).message())
                .isEqualTo("Rappel : votre rendez-vous a lieu le 06/12/2026 à 13:00. Pensez à vous présenter 10 minutes en avance.");
        assertThat(recharge(rdv).rappelEnvoye()).isTrue();
        assertThat(recharge(rdv).rappelEnvoyeLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void un_rendezvous_dans_trente_heures_est_ignore() {
        RendezVous rdv = confirmeDans(Duration.ofHours(30));

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(rdv).rappelEnvoye()).isFalse();
    }

    @Test
    void la_fenetre_va_de_maintenant_inclus_a_vingt_quatre_heures_exclu() {
        RendezVous aLInstant = confirmeDans(Duration.ZERO);
        RendezVous presqueDemain = confirmeDans(Duration.ofHours(23).plusMinutes(59));
        RendezVous demainMemeHeure = confirmeDans(Duration.ofHours(24));
        RendezVous passe = confirmeDans(Duration.ofMinutes(-1));

        assertThat(service.executer()).isEqualTo(2);
        assertThat(recharge(aLInstant).rappelEnvoye()).isTrue();
        assertThat(recharge(presqueDemain).rappelEnvoye()).isTrue();
        assertThat(recharge(demainMemeHeure).rappelEnvoye()).isFalse();
        assertThat(recharge(passe).rappelEnvoye()).isFalse();
        assertThat(notifieur.pour(passe.patientId())).isEmpty();
    }

    @Test
    void un_rendezvous_deja_rappele_est_ignore() {
        RendezVous rdv = confirmeDans(Duration.ofHours(5));
        rdv.marquerRappelEnvoye(MAINTENANT.minus(Duration.ofHours(1)));
        rendezVous.enregistrer(rdv);

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(rdv).rappelEnvoyeLe()).isEqualTo(MAINTENANT.minus(Duration.ofHours(1)));
    }

    @Test
    void un_rendezvous_annule_ou_honore_est_ignore() {
        RendezVous annule = confirmeDans(Duration.ofHours(3));
        annule.annuler();
        rendezVous.enregistrer(annule);
        RendezVous honore = rendezVous.enregistrer(new RendezVous(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                MAINTENANT.plus(Duration.ofHours(4)), StatutRdv.HONORE));

        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
        assertThat(recharge(annule).rappelEnvoye()).isFalse();
        assertThat(recharge(honore).rappelEnvoye()).isFalse();
    }

    @Test
    void deux_executions_n_envoient_le_rappel_qu_une_fois() {
        RendezVous premier = confirmeDans(Duration.ofHours(2));
        RendezVous second = confirmeDans(Duration.ofHours(12));

        assertThat(service.executer()).isEqualTo(2);
        assertThat(service.executer()).isEqualTo(0);

        assertThat(notifieur.appels).hasSize(2);
        assertThat(notifieur.pour(premier.patientId())).hasSize(1);
        assertThat(notifieur.pour(second.patientId())).hasSize(1);
    }

    @Test
    void sans_rendezvous_a_rappeler_rien_n_est_envoye() {
        assertThat(service.executer()).isEqualTo(0);
        assertThat(notifieur.appels).isEmpty();
    }

    /** Passerelle SMS commandable : echoue tant que {@code panne} est vrai. */
    static class SmsCommandable implements dz.tabibi.backend.rappels.domain.EnvoiSms {
        boolean panne = true;
        int tentatives;
        final List<String> envoyes = new ArrayList<>();
        public boolean disponible() { return true; }
        public void envoyer(String tel, String texte) {
            tentatives++;
            if (panne) throw new IllegalStateException("ERR credit epuise");
            envoyes.add(tel);
        }
    }

    @Test
    void un_sms_en_echec_est_marque_puis_repris_au_passage_suivant_sans_renvoyer_la_notification() {
        RendezVous rdv = confirmeDans(Duration.ofHours(2));
        SmsCommandable passerelle = new SmsCommandable();
        service.setSms(passerelle, new dz.tabibi.backend.commun.FauxComptes().avec(rdv.patientId(), "A", null, "0550123456"));

        service.executer();
        assertThat(recharge(rdv).rappelEnvoye()).isTrue();
        assertThat(recharge(rdv).smsAReprendre()).isTrue();
        assertThat(recharge(rdv).smsEchecs()).isEqualTo(1);

        passerelle.panne = false;
        assertThat(service.executer()).isEqualTo(0);

        assertThat(passerelle.envoyes).containsExactly("+213550123456");
        assertThat(recharge(rdv).smsAReprendre()).isFalse();
        assertThat(notifieur.appels).hasSize(1);

        service.executer();
        assertThat(passerelle.tentatives).isEqualTo(2);
    }

    @Test
    void la_reprise_du_sms_s_arrete_apres_le_nombre_maximal_d_echecs() {
        RendezVous rdv = confirmeDans(Duration.ofHours(2));
        SmsCommandable passerelle = new SmsCommandable();
        service.setSms(passerelle, new dz.tabibi.backend.commun.FauxComptes().avec(rdv.patientId(), "A", null, "0550123456"));

        for (int i = 0; i < RappelService.MAX_ECHECS_SMS + 3; i++) {
            service.executer();
        }

        assertThat(passerelle.tentatives).isEqualTo(RappelService.MAX_ECHECS_SMS);
        assertThat(recharge(rdv).smsEchecs()).isEqualTo(RappelService.MAX_ECHECS_SMS);
    }

    @Test
    void un_echec_d_enregistrement_du_second_rendezvous_laisse_le_premier_marque_et_non_renvoye() {
        RendezVous premier = confirmeDans(Duration.ofHours(2));
        RendezVous second = confirmeDans(Duration.ofHours(3));
        boolean[] panne = {true};
        RendezVousRepository base = rendezVous;
        RendezVousRepository fragile = new EnMemoireRendezVousRepository() {
            @Override
            public RendezVous enregistrer(RendezVous r) {
                if (panne[0] && r.id().equals(second.id())) throw new IllegalStateException("base indisponible");
                return base.enregistrer(r);
            }
            @Override
            public List<RendezVous> confirmesSansRappelEntre(Instant de, Instant a) {
                // Copies, comme une relecture en base : un echec d'enregistrement ne laisse aucune trace.
                return base.confirmesSansRappelEntre(de, a).stream()
                        .map(r -> new RendezVous(r.id(), r.patientId(), r.medecinId(), r.debut(), r.statut(),
                                r.creneauId(), r.rappelEnvoyeLe()))
                        .toList();
            }
        };
        RappelService svc = new RappelService(fragile, notifieur, Clock.fixed(MAINTENANT, ZoneOffset.UTC));

        assertThat(svc.executer()).isEqualTo(1);
        assertThat(recharge(premier).rappelEnvoye()).isTrue();
        assertThat(recharge(second).rappelEnvoye()).isFalse();

        panne[0] = false;
        assertThat(svc.executer()).isEqualTo(1);

        assertThat(notifieur.pour(premier.patientId())).hasSize(1);
        assertThat(notifieur.pour(second.patientId())).hasSize(1);
    }
}
