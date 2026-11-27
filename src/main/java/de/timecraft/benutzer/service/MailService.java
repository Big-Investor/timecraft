package de.timecraft.benutzer.service;

/**
 * Versendet E-Mails mit Einmalpasswörtern (RB-M1-01).
 * Es gibt zwei Varianten: {@link SmtpMailService} verschickt echte E-Mails über SMTP,
 * {@code LogMailService} (nur im Profil {@code fake}) schreibt die Mail ins Log.
 *
 * @author Leon-Selim Kannenberg
 */
public interface MailService {

    /**
     * Versendet die Mail und gibt eine Rückmeldung für die Oberfläche zurück
     * (z. B. „E-Mail wurde zugestellt.“). Schlägt der Versand fehl, kommt eine
     * {@code FachlicheException} mit deutschem Text (QA-M1-11).
     */
    String senden(EinmalpasswortMail mail);
}
