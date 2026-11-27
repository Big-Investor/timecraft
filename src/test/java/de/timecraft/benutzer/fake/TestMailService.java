package de.timecraft.benutzer.fake;

import de.timecraft.benutzer.service.EinmalpasswortMail;
import de.timecraft.benutzer.service.MailService;
import de.timecraft.geruest.common.FachlicheException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mail-Versand für Tests: merkt sich alle gesendeten Mails, damit Tests Empfänger und
 * Einmalpasswort prüfen können. Mit {@link #versandSchlaegtFehl()} lässt sich ein Versandfehler
 * nachstellen, wie ihn der echte {@code SmtpMailService} meldet (QA-M1-11).
 *
 * @author Leon-Selim Kannenberg
 */
public class TestMailService implements MailService {

    public static final String RUECKMELDUNG = "E-Mail wurde zugestellt.";
    public static final String FEHLERTEXT = "Die E-Mail mit dem Einmalpasswort konnte nicht versendet werden.";

    private final List<EinmalpasswortMail> gesendet = new ArrayList<>();
    private boolean fehlerNachstellen = false;

    /** Ab jetzt schlägt jeder Versand fehl. */
    public void versandSchlaegtFehl() {
        fehlerNachstellen = true;
    }

    @Override
    public String senden(EinmalpasswortMail mail) {
        if (fehlerNachstellen) {
            throw new FachlicheException(FEHLERTEXT);
        }
        gesendet.add(mail);
        return RUECKMELDUNG;
    }

    public List<EinmalpasswortMail> gesendeteMails() {
        return new ArrayList<>(gesendet);
    }

    /** Liest das Einmalpasswort aus dem Text der zuletzt gesendeten Mail. */
    public String letztesEinmalpasswort() {
        String text = gesendet.get(gesendet.size() - 1).text();
        String vorDemPasswort = "Einmalpasswort: ";
        int anfang = text.indexOf(vorDemPasswort) + vorDemPasswort.length();
        int ende = text.indexOf('\n', anfang);
        return text.substring(anfang, ende);
    }
}
