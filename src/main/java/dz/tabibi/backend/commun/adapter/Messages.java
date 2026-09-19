package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Langue;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Catalogue des textes adresses aux utilisateurs (erreurs de l'API, notifications), charge une
 * fois au demarrage depuis {@code src/main/resources/messages/{fr,ar,en}.properties}, lus en UTF-8.
 * <p>
 * Le rendu remplace les reperes {@code {0}}, {@code {1}}... par les parametres, dans l'ordre :
 * volontairement pas {@code MessageFormat}, dont les apostrophes (nombreuses en francais)
 * exigeraient un echappement dans chaque fichier.
 * <p>
 * Une cle absente de la langue demandee retombe sur le francais ; absente partout, elle est
 * renvoyee telle quelle (l'API reste utilisable et le defaut se voit tout de suite).
 */
@Component
public final class Messages {

    private static final String DOSSIER = "/messages/";
    /** Instance partagee, pour les points d'entree que Spring ne cable pas (conseil d'erreurs, filtres). */
    private static final Messages PARTAGEES = new Messages();

    private final Map<Langue, Properties> catalogues = new EnumMap<>(Langue.class);

    public Messages() {
        for (Langue langue : Langue.values()) {
            catalogues.put(langue, charger(langue));
        }
    }

    /** Catalogue partage : meme contenu qu'un bean, sans dependre du contexte Spring. */
    public static Messages partagees() {
        return PARTAGEES;
    }

    /**
     * Le texte de cette cle dans cette langue, ses reperes remplaces par les parametres.
     * @param langue langue demandee ; null vaut {@link Langue#PAR_DEFAUT}
     * @param cle    entree du catalogue ; renvoyee telle quelle si aucune langue ne la connait
     */
    public String message(Langue langue, String cle, Object... params) {
        if (cle == null) {
            return "";
        }
        Langue demandee = langue == null ? Langue.PAR_DEFAUT : langue;
        String modele = catalogues.get(demandee).getProperty(cle);
        if (modele == null) {
            modele = catalogues.get(Langue.PAR_DEFAUT).getProperty(cle);
        }
        return modele == null ? cle : remplir(modele, params);
    }

    /** Vrai si cette langue connait la cle (sans repli sur le francais). */
    public boolean connait(Langue langue, String cle) {
        return catalogues.get(langue == null ? Langue.PAR_DEFAUT : langue).getProperty(cle) != null;
    }

    /** Les cles declarees par une langue, triees ; sert au test de coherence des trois catalogues. */
    public Set<String> cles(Langue langue) {
        return new TreeSet<>(catalogues.get(langue == null ? Langue.PAR_DEFAUT : langue).stringPropertyNames());
    }

    /** Remplace {0}, {1}... par les parametres ; un repere sans parametre est laisse tel quel. */
    private static String remplir(String modele, Object... params) {
        if (params == null || params.length == 0) {
            return modele;
        }
        String texte = modele;
        for (int i = 0; i < params.length; i++) {
            texte = texte.replace("{" + i + "}", String.valueOf(params[i]));
        }
        return texte;
    }

    private static Properties charger(Langue langue) {
        String chemin = DOSSIER + langue.code() + ".properties";
        Properties proprietes = new Properties();
        try (InputStream flux = Messages.class.getResourceAsStream(chemin)) {
            if (flux == null) {
                throw new IllegalStateException("Catalogue de messages absent : " + chemin);
            }
            proprietes.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Catalogue de messages illisible : " + chemin, e);
        }
        return proprietes;
    }
}
