package dz.tabibi.backend.identite;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Renvoie l'identite de l'utilisateur courant, telle que portee par le JWT. */
@RestController
public class MoiController {

    @GetMapping("/api/moi")
    @SuppressWarnings("unchecked")
    public Map<String, Object> moi(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        Object roles = realmAccess == null ? List.of() : realmAccess.getOrDefault("roles", List.of());
        return Map.of(
                "sujet", jwt.getSubject(),
                "nom", jwt.getClaimAsString("preferred_username"),
                "roles", roles
        );
    }
}
