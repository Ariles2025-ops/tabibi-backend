package dz.tabibi.backend.audit;

import dz.tabibi.backend.audit.domain.AdresseIp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimisation de l'adresse IP journalisee. */
class AdresseIpTest {

    @Test
    void ipv4_tronquee_au_dernier_octet() {
        assertThat(AdresseIp.tronquer("192.168.1.37")).isEqualTo("192.168.1.0");
        assertThat(AdresseIp.tronquer("10.0.0.1")).isEqualTo("10.0.0.0");
        assertThat(AdresseIp.tronquer(" 41.111.22.33 ")).isEqualTo("41.111.22.0");
        assertThat(AdresseIp.tronquer("127.0.0.1")).isEqualTo("127.0.0.0");
    }

    @Test
    void ipv6_tronquee_aux_64_premiers_bits() {
        assertThat(AdresseIp.tronquer("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2::");
        assertThat(AdresseIp.tronquer("2001:0db8:0001:0002::1")).isEqualTo("2001:db8:1:2::");
        assertThat(AdresseIp.tronquer("::1")).isEqualTo("0:0:0:0::");
        assertThat(AdresseIp.tronquer("fe80::1%eth0")).isEqualTo("fe80:0:0:0::"); // identifiant de zone ignore
    }

    @Test
    void ipv4_projetee_en_ipv6_est_tronquee_comme_une_ipv4() {
        assertThat(AdresseIp.tronquer("::ffff:192.168.1.37")).isEqualTo("192.168.1.0");
    }

    @Test
    void valeur_absente_ou_invalide_donne_null_sans_exception() {
        assertThat(AdresseIp.tronquer(null)).isNull();
        assertThat(AdresseIp.tronquer("")).isNull();
        assertThat(AdresseIp.tronquer("   ")).isNull();
        assertThat(AdresseIp.tronquer("inconnu")).isNull();
        assertThat(AdresseIp.tronquer("serveur.example")).isNull();
        assertThat(AdresseIp.tronquer("1:2:3")).isNull();
        assertThat(AdresseIp.tronquer("2001:db8::zz")).isNull();
        assertThat(AdresseIp.tronquer("192.168.1")).isNull();
    }
}
