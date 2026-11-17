package dz.tabibi.backend.commun.adapter;

import dz.tabibi.backend.commun.domain.Courriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * E-mails par SMTP (OVH : ssl0.ovh.net, port 587, STARTTLS), avec le meme compte que Keycloak
 * (no-reply@tabibi.doctor). Actif seulement si TABIBI_SMTP_PASSWORD est renseigne dans le .env du serveur.
 */
@Component
public class SmtpCourriel implements Courriel {

    private static final Logger log = LoggerFactory.getLogger(SmtpCourriel.class);

    private final JavaMailSenderImpl envoi = new JavaMailSenderImpl();
    private final String expediteur;
    private final boolean actif;

    public SmtpCourriel(@Value("${TABIBI_SMTP_HOST:ssl0.ovh.net}") String hote,
                        @Value("${TABIBI_SMTP_PORT:587}") int port,
                        @Value("${TABIBI_SMTP_USER:no-reply@tabibi.doctor}") String utilisateur,
                        @Value("${TABIBI_SMTP_PASSWORD:}") String motDePasse,
                        @Value("${TABIBI_SMTP_FROM:Ndawik <no-reply@tabibi.doctor>}") String expediteur) {
        this.actif = !motDePasse.isBlank();
        this.expediteur = expediteur;
        envoi.setHost(hote);
        envoi.setPort(port);
        envoi.setUsername(utilisateur);
        envoi.setPassword(motDePasse);
        envoi.setDefaultEncoding("UTF-8");
        Properties p = envoi.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.starttls.required", "true");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        log.info("E-mails a l'equipe : {}", actif ? "SMTP actif (" + hote + ")" : "desactives (TABIBI_SMTP_PASSWORD vide)");
    }

    @Override
    public boolean disponible() {
        return actif;
    }

    @Override
    public void envoyer(String destinataire, String sujet, String texte) {
        if (!actif) {
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(expediteur);
        m.setTo(destinataire);
        m.setSubject(sujet);
        m.setText(texte);
        envoi.send(m);
    }
}
