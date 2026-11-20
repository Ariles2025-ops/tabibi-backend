package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.ComptesUtilisateurs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comptes lus par l'API d'administration de Keycloak, avec le compte de service du client
 * {@code tabibi-backend-admin}. Sans secret configure, rien n'est trouve et les roles sont seulement journalises.
 */
@Component
public class KeycloakComptesUtilisateurs implements ComptesUtilisateurs {

    private static final Logger log = LoggerFactory.getLogger(KeycloakComptesUtilisateurs.class);

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String secret;
    /**
     * Les coordonnees changent rarement : on evite d'interroger Keycloak a chaque affichage d'agenda. Chaque entree
     * expire au bout d'une heure, pour qu'un changement de numero ou d'e-mail finisse par etre pris en compte.
     */
    static final Duration DUREE_CACHE = Duration.ofHours(1);

    private record EnCache(InfosCompte infos, Instant expireLe) {}

    private final Map<UUID, EnCache> cache = new ConcurrentHashMap<>();
    private final Clock horloge;

    @org.springframework.beans.factory.annotation.Autowired
    public KeycloakComptesUtilisateurs(
            @Value("${tabibi.keycloak.admin.url:http://keycloak:8080}") String url,
            @Value("${tabibi.keycloak.admin.realm:tabibi}") String realm,
            @Value("${tabibi.keycloak.admin.client-id:tabibi-backend-admin}") String clientId,
            @Value("${tabibi.keycloak.admin.secret:}") String secret) {
        this(RestClient.builder().baseUrl(url), realm, clientId, secret, Clock.systemUTC());
    }

    /** Pour les tests : client HTTP fourni (serveur simule) et horloge choisie. */
    KeycloakComptesUtilisateurs(RestClient.Builder http, String realm, String clientId, String secret, Clock horloge) {
        this.horloge = horloge;
        this.http = http.build();
        this.realm = realm;
        this.clientId = clientId;
        this.secret = secret;
    }

    private boolean configure() {
        return !secret.isBlank();
    }

    @Override
    public boolean disponible() {
        return configure();
    }

    @Override
    public Optional<InfosCompte> infos(UUID compte) {
        if (!configure() || compte == null) {
            return Optional.empty();
        }
        EnCache connu = cache.get(compte);
        if (connu != null && horloge.instant().isBefore(connu.expireLe())) {
            return Optional.of(connu.infos());
        }
        try {
            Map<?, ?> u = http.get().uri("/admin/realms/{realm}/users/{id}", realm, compte)
                    .header("Authorization", "Bearer " + jeton()).retrieve().body(Map.class);
            InfosCompte infos = versInfos(u);
            if (infos != null) {
                cache.put(compte, new EnCache(infos, horloge.instant().plus(DUREE_CACHE)));
            }
            return Optional.ofNullable(infos);
        } catch (RuntimeException e) {
            log.warn("Compte {} introuvable dans Keycloak : {}", compte, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> parEmail(String email) {
        if (!configure() || email == null || email.isBlank()) {
            return Optional.empty();
        }
        try {
            List<?> trouves = http.get()
                    .uri(b -> b.path("/admin/realms/{realm}/users").queryParam("email", email.trim())
                            .queryParam("exact", true).build(realm))
                    .header("Authorization", "Bearer " + jeton()).retrieve().body(List.class);
            if (trouves == null || trouves.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString((String) ((Map<?, ?>) trouves.get(0)).get("id")));
        } catch (RuntimeException e) {
            log.warn("Recherche du compte {} impossible : {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void accorderRole(UUID compte, String role) {
        if (!configure()) {
            log.warn("Role {} a donner a la main au compte {} : secret d'administration Keycloak non configure.", role, compte);
            return;
        }
        String jeton = "Bearer " + jeton();
        Map<?, ?> r = http.get().uri("/admin/realms/{realm}/roles/{role}", realm, role)
                .header("Authorization", jeton).retrieve().body(Map.class);
        http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, compte)
                .header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                .body(List.of(r)).retrieve().toBodilessEntity();
        log.info("Role {} donne au compte {}.", role, compte);
    }

    @Override
    public UUID creerCompteMedecin(String email, String nomComplet, String telephone) {
        if (!configure()) {
            throw new IllegalStateException("Annuaire des comptes non configuré.");
        }
        String jeton = "Bearer " + jeton();
        String nom = nomComplet == null ? "" : nomComplet.trim();
        int coupe = nom.lastIndexOf(' ');
        Map<String, Object> u = new java.util.HashMap<>();
        u.put("username", email);
        u.put("email", email);
        u.put("firstName", coupe > 0 ? nom.substring(0, coupe) : nom);
        u.put("lastName", coupe > 0 ? nom.substring(coupe + 1) : "");
        u.put("enabled", true);
        u.put("emailVerified", true);
        if (telephone != null && !telephone.isBlank()) {
            u.put("attributes", Map.of("phoneNumber", List.of(telephone)));
        }
        java.net.URI emplacement = http.post().uri("/admin/realms/{realm}/users", realm)
                .header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                .body(u).retrieve().toBodilessEntity().getHeaders().getLocation();
        if (emplacement == null) {
            throw new IllegalStateException("Compte créé mais identifiant introuvable.");
        }
        String chemin = emplacement.getPath();
        UUID id = UUID.fromString(chemin.substring(chemin.lastIndexOf('/') + 1));
        accorderRole(id, "MEDECIN");
        http.put().uri("/admin/realms/{realm}/users/{id}/execute-actions-email", realm, id)
                .header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                .body(List.of("UPDATE_PASSWORD")).retrieve().toBodilessEntity();
        log.info("Compte medecin {} cree, e-mail de definition du mot de passe envoye.", id);
        return id;
    }

    @Override
    public List<UUID> avecRole(String role) {
        if (!configure()) {
            return List.of();
        }
        try {
            List<?> comptes = http.get().uri("/admin/realms/{realm}/roles/{role}/users?max=100", realm, role)
                    .header("Authorization", "Bearer " + jeton()).retrieve().body(List.class);
            return comptes == null ? List.of() : comptes.stream()
                    .map(u -> UUID.fromString((String) ((Map<?, ?>) u).get("id"))).toList();
        } catch (RuntimeException e) {
            log.warn("Liste des comptes {} impossible : {}", role, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void retirerRole(UUID compte, String role) {
        if (!configure()) {
            log.warn("Role {} a retirer a la main au compte {} : secret d'administration Keycloak non configure.", role, compte);
            return;
        }
        try {
            String jeton = "Bearer " + jeton();
            Map<?, ?> r = http.get().uri("/admin/realms/{realm}/roles/{role}", realm, role)
                    .header("Authorization", jeton).retrieve().body(Map.class);
            http.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, compte)
                    .header("Authorization", jeton).contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(r)).retrieve().toBodilessEntity();
            log.info("Role {} retire au compte {}.", role, compte);
        } catch (RuntimeException e) {
            log.warn("Retrait du role {} au compte {} impossible : {}", role, compte, e.getMessage());
        }
    }

    @Override
    public void supprimer(UUID compte) {
        cache.remove(compte);
        if (!configure()) {
            log.warn("Compte {} a supprimer a la main dans Keycloak : secret d'administration Keycloak non configure.", compte);
            return;
        }
        try {
            http.delete().uri("/admin/realms/{realm}/users/{id}", realm, compte)
                    .header("Authorization", "Bearer " + jeton()).retrieve().toBodilessEntity();
            log.info("Compte {} supprime de Keycloak.", compte);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.info("Compte {} deja absent de Keycloak.", compte);
        }
    }

    private static InfosCompte versInfos(Map<?, ?> u) {
        if (u == null || u.get("id") == null) {
            return null;
        }
        String prenom = u.get("firstName") instanceof String s ? s.trim() : "";
        String nom = u.get("lastName") instanceof String s ? s.trim() : "";
        String email = u.get("email") instanceof String s ? s : null;
        String telephone = null;
        if (u.get("attributes") instanceof Map<?, ?> attributs) {
            for (String cle : List.of("phoneNumber", "phone_number", "telephone", "phone")) {
                if (attributs.get(cle) instanceof List<?> l && !l.isEmpty()) {
                    telephone = String.valueOf(l.get(0));
                    break;
                }
            }
        }
        String complet = (prenom + " " + nom).trim();
        if (complet.isEmpty()) {
            complet = email != null ? email : (telephone != null ? telephone : null);
        }
        return new InfosCompte(UUID.fromString((String) u.get("id")), complet, email, telephone);
    }

    private String jeton() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> reponse = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        return (String) reponse.get("access_token");
    }
}
