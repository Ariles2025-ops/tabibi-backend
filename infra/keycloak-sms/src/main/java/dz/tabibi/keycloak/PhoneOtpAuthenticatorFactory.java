package dz.tabibi.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class PhoneOtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "tabibi-phone-otp";

    private SmsSender sms;
    private ReviewAccess review;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PhoneOtpAuthenticator(sms, review);
    }

    @Override
    public void init(Config.Scope config) {
        sms = SmsSender.fromEnv();
        review = ReviewAccess.fromEnv(System.getenv(), java.time.Instant.now());
        if (review.enabled()) {
            org.jboss.logging.Logger.getLogger(PhoneOtpAuthenticatorFactory.class).warnf(
                    "[REVUE STORES] accès de revue ACTIF pour %s jusqu'au %s inclus, heure d'Alger (code fixe, sans SMS)", review.phones(), review.lastDay());
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getDisplayType() {
        return "Tabibi : téléphone + code SMS";
    }

    @Override
    public String getReferenceCategory() {
        return "otp";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] {Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Connexion et inscription par numéro mobile algérien et code SMS à 6 chiffres.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
