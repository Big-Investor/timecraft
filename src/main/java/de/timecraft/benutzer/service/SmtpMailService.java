package de.timecraft.benutzer.service;

import de.timecraft.geruest.common.FachlicheException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Verschickt E-Mails über einen SMTP-Server mit Spring Boot Mail (RB-M1-01).
 * Server und Zugangsdaten kommen aus {@code application.properties} bzw. Umgebungsvariablen.
 * Im Profil {@code fake} ist stattdessen der {@code LogMailService} aktiv.
 *
 * @author Leon-Selim Kannenberg
 */
@Service
@Profile("!fake")
public class SmtpMailService implements MailService {

    static final String VERSAND_FEHLGESCHLAGEN = "Die E-Mail mit dem Einmalpasswort konnte nicht versendet werden. "
            + "Bitte versuchen Sie es später erneut.";

    private static final Logger LOG = LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailVersand;
    private final String absender;

    public SmtpMailService(JavaMailSender mailVersand, @Value("${timecraft.mail.absender}") String absender) {
        this.mailVersand = mailVersand;
        this.absender = absender;
    }

    @Override
    public String senden(EinmalpasswortMail mail) {
        SimpleMailMessage nachricht = new SimpleMailMessage();
        nachricht.setFrom(absender);
        nachricht.setTo(mail.empfaenger());
        nachricht.setSubject(mail.betreff());
        nachricht.setText(mail.text());
        try {
            mailVersand.send(nachricht);
        } catch (MailException fehler) {
            // Technische Details nur ins Log, die Person sieht einen verständlichen Text.
            LOG.warn("E-Mail an {} konnte nicht versendet werden", mail.empfaenger(), fehler);
            throw new FachlicheException(VERSAND_FEHLGESCHLAGEN);
        }
        return "Das Einmalpasswort wurde per E-Mail an " + mail.empfaenger() + " gesendet.";
    }
}
