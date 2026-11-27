package de.timecraft.benutzer.fake;

import de.timecraft.benutzer.service.EinmalpasswortMail;
import de.timecraft.benutzer.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mail-Versand für den lokalen Start im Profil {@code fake}: Es wird keine E-Mail verschickt,
 * sondern die Mail (mit Einmalpasswort) ins Log geschrieben. Danach meldet die Klasse eine
 * simulierte Zustellung zurück, die die Oberfläche anzeigt (Teamentscheidung 04.11.2026).
 *
 * @author Leon-Selim Kannenberg
 */
@Service
@Profile("fake")
public class LogMailService implements MailService {

    static final String RUECKMELDUNG = "E-Mail wurde zugestellt.";

    private static final Logger LOG = LoggerFactory.getLogger(LogMailService.class);

    @Override
    public String senden(EinmalpasswortMail mail) {
        LOG.info("Testbetrieb, keine echte E-Mail. An: {} | Betreff: {}\n{}",
                mail.empfaenger(), mail.betreff(), mail.text());
        return RUECKMELDUNG;
    }
}
