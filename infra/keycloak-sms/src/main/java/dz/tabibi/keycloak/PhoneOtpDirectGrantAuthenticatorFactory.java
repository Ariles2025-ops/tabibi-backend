package dz.tabibi.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/** Flux « direct grant » du client mobile : voir {@link PhoneOtpDirectGrantAuthenticator}. */
public class PhoneOtpDirectGrantAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "tabibi-phone-otp-direct";

    private SmsSender sms;
    private ReviewAccess review;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new PhoneOtpDirectGrantAuthenticator(sms, review);
    }

    @Override
    public void init(Config.Scope config) {
        sms = SmsSender.fromEnv();
        review = ReviewAccess.fromEnv(System.getenv(), java.time.Instant.now());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getDisplayType() {
        return "Tabibi : téléphone + code SMS depuis l'appli";
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
        return "Connexion depuis les écrans de l'appli mobile (direct grant) : numéro puis code SMS, sans page web.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
