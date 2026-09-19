package dz.tabibi.backend.audit.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tronque une adresse IP avant de la journaliser (minimisation des donnees) : IPv4 au dernier
 * octet (192.168.1.37 devient 192.168.1.0), IPv6 aux 64 premiers bits (2001:db8:1:2::). Une
 * valeur qui n'est pas une adresse IP donne null : rien d'inconnu n'est journalise.
 */
public final class AdresseIp {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}");
    /** Caracteres possibles d'une adresse IPv6 litterale (avec ou sans suffixe IPv4). */
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");

    private AdresseIp() { }

    public static String tronquer(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            return null;
        }
        String valeur = adresse.trim();
        Matcher ipv4 = IPV4.matcher(valeur);
        if (ipv4.matches()) {
            return ipv4.group(1) + ".0";
        }
        int zone = valeur.indexOf('%'); // identifiant de zone (fe80::1%eth0), jamais utile
        if (zone >= 0) {
            valeur = valeur.substring(0, zone);
        }
        if (valeur.indexOf(':') < 0 || !IPV6.matcher(valeur).matches()) {
            return null;
        }
        try {
            // Entre crochets : le JDK ne tente jamais une resolution DNS sur un litteral IPv6 invalide.
            byte[] octets = InetAddress.getByName("[" + valeur + "]").getAddress();
            if (octets.length == 4) { // adresse IPv4 projetee (::ffff:192.168.1.37)
                return (octets[0] & 0xff) + "." + (octets[1] & 0xff) + "." + (octets[2] & 0xff) + ".0";
            }
            StringBuilder prefixe = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                if (i > 0) {
                    prefixe.append(':');
                }
                prefixe.append(Integer.toHexString(((octets[i] & 0xff) << 8) | (octets[i + 1] & 0xff)));
            }
            return prefixe.append("::").toString();
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }
}
