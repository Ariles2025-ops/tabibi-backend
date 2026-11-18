package dz.tabibi.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Securite de l'API : sans etat, chaque requete porte un JWT signe par Keycloak.
 * L'autorisation se joue ici (par role), jamais cote client.
 * Le partage entre origines (CORS) n'est ouvert qu'aux origines listees dans CorsProprietes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(CorsProprietes.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
                                           @Value("${tabibi.limite.actions-par-minute:60}") int actionsParMinute,
                                           @Value("${tabibi.limite.lectures-annuaire-par-minute:600}") int lecturesParMinute) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Supervision (sante et sondes liveness/readiness, sans detail). La documentation d'API n'est
                // plus publique : elle donnait la carte complete des routes (desactivee en prod, voir application-postgres.yml).
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/medecins", "/api/medecins/**").permitAll()
                // Verification d'une ordonnance par son code (pharmacien) : sans jeton, sans donnee personnelle.
                .requestMatchers(HttpMethod.GET, "/api/ordonnances/verifier/**").permitAll()
                // Administration : verrou par chemin, en plus du @PreAuthorize des controleurs (defense en profondeur).
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
            // Au plus N actions (ecritures) par minute et par compte, 429 au-dela.
            .addFilterAfter(new LimiteDebitFiltre(actionsParMinute), BearerTokenAuthenticationFilter.class)
            // Anti-copie : au plus N lectures de l'annuaire par IP et par minute (reseau interne exempte).
            .addFilterAfter(new LimiteLectureFiltre(lecturesParMinute), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS pour le front web : seules les origines configurees, sur /api/** ; methodes et en-tetes
     * limites a ce qu'utilise le front (jeton en en-tete Authorization, corps JSON) ; sans cookies
     * (credentials false, l'API est sans etat) ; le navigateur garde la reponse de preflight 1 h.
     * Une requete d'une autre origine est refusee avant d'atteindre l'API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProprietes proprietes) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(proprietes.origines());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("X-Total-Count"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /** Branche notre traduction des roles Keycloak. */
    public JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }
}
