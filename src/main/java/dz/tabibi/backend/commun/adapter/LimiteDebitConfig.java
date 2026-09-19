package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.adapter.FiltreLimiteDebit.Regle;
import dz.tabibi.backend.commun.domain.Compteurs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * Enregistre le filtre de limitation de debit dans la chaine servlet, juste avant la chaine de
 * Spring Security. Quotas par adresse IP et par minute, lus dans application.yml
 * (tabibi.limite-debit.*) ; tabibi.limite-debit.actif=false retire le filtre (tests de charge,
 * limitation deja faite par le reverse proxy). Configuration a part, non importee par les tests
 * web (@WebMvcTest), qui n'en ont pas besoin.
 */
@Configuration
@ConditionalOnProperty(name = "tabibi.limite-debit.actif", havingValue = "true", matchIfMissing = true)
public class LimiteDebitConfig {

    /** Juste avant la chaine de filtres de Spring Security (ordre -100). */
    public static final int ORDRE_FILTRE = SecurityProperties.DEFAULT_FILTER_ORDER - 1;

    /** Les routes limitees et l'ordre de leur evaluation. */
    public static List<Regle> regles(int annuaireParMinute, int verificationParMinute, int publicationParMinute, Clock horloge) {
        return List.of(
                new Regle("GET", "/api/medecins/**", LimiteurDebit.parMinute(annuaireParMinute, horloge)),
                new Regle("GET", "/api/ordonnances/verifier/**", LimiteurDebit.parMinute(verificationParMinute, horloge)),
                new Regle("POST", "/api/dawini/besoins", LimiteurDebit.parMinute(publicationParMinute, horloge)),
                new Regle("POST", "/api/conversations", LimiteurDebit.parMinute(publicationParMinute, horloge)),
                new Regle("POST", "/api/avis", LimiteurDebit.parMinute(publicationParMinute, horloge)));
    }

    @Bean
    public FilterRegistrationBean<FiltreLimiteDebit> filtreLimiteDebit(
            Clock horloge,
            Messages messages,
            Compteurs compteurs,
            @Value("${tabibi.limite-debit.annuaire-par-minute:120}") int annuaireParMinute,
            @Value("${tabibi.limite-debit.verification-par-minute:30}") int verificationParMinute,
            @Value("${tabibi.limite-debit.publication-par-minute:20}") int publicationParMinute,
            @Value("${server.forward-headers-strategy:none}") String strategieEnTetesTransferes) {
        boolean derriereProxy = !"none".equalsIgnoreCase(strategieEnTetesTransferes.strip());
        FiltreLimiteDebit filtre = new FiltreLimiteDebit(
                regles(annuaireParMinute, verificationParMinute, publicationParMinute, horloge), derriereProxy,
                messages, compteurs);
        FilterRegistrationBean<FiltreLimiteDebit> enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.setName("filtreLimiteDebit");
        enregistrement.setOrder(ORDRE_FILTRE);
        return enregistrement;
    }
}
