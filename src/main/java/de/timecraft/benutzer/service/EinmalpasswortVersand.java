package de.timecraft.benutzer.service;

import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import org.springframework.stereotype.Component;

/**
 * Erzeugt ein Einmalpasswort und schickt es per E-Mail an einen Mitarbeiter
 * (FA-M1-08 beim Zurücksetzen, FA-M1-09 beim Anlegen).
 *
 * <p>Wichtig ist die Reihenfolge (QA-M1-11): Erst wird die Mail gesendet, erst danach wird das
 * Einmalpasswort im Konto gespeichert. Schlägt der Versand fehl, bleibt das Konto unverändert
 * und das bisherige Passwort gültig.
 *
 * @author Leon-Selim Kannenberg
 */
@Component
class EinmalpasswortVersand {

    private final PasswortService passwortService;
    private final MailService mailService;
    private final BenutzerRepository benutzerRepository;

    EinmalpasswortVersand(PasswortService passwortService, MailService mailService,
            BenutzerRepository benutzerRepository) {
        this.passwortService = passwortService;
        this.mailService = mailService;
        this.benutzerRepository = benutzerRepository;
    }

    /**
     * Sendet ein neues Einmalpasswort und speichert es danach als Hash im Konto (24 Stunden gültig).
     * Gibt die Rückmeldung des Mailversands zurück (z. B. „E-Mail wurde zugestellt.“).
     * Bei ungültiger Adresse oder Versandfehler kommt eine {@code FachlicheException}, und nichts ändert sich.
     */
    String sendenUndSpeichern(Mitarbeiter mitarbeiter) {
        Benutzer benutzer = mitarbeiter.getBenutzer();
        String einmalpasswort = passwortService.einmalpasswortErzeugen();
        EinmalpasswortMail mail = EinmalpasswortMail.erstellen(mitarbeiter.getEmail(), mitarbeiter.getName(),
                benutzer.getBenutzername(), einmalpasswort);

        String rueckmeldung = mailService.senden(mail);

        // Erst nach erfolgreichem Versand: Hash speichern, altes Passwort wird ungültig.
        passwortService.einmalpasswortSetzen(benutzer, einmalpasswort);
        benutzerRepository.save(benutzer);
        return rueckmeldung;
    }
}
