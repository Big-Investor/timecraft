package de.timecraft.benutzer.service;

import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.geruest.common.FachlicheException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Alles rund um Passwörter: Passwortregeln (QA-M1-03), BCrypt-Hash (QA-M1-01)
 * und Einmalpasswörter (zufällig, höchstens 24 Stunden gültig, QA-M1-12).
 * Die Klasse braucht keine Datenbank und lässt sich deshalb einfach testen.
 *
 * @author Leon-Selim Kannenberg
 */
@Service
public class PasswortService {

    static final int MINDESTLAENGE = 8;
    static final int EINMALPASSWORT_LAENGE = 12;
    static final int EINMALPASSWORT_GUELTIG_STUNDEN = 24;

    /** BCrypt verarbeitet höchstens 72 Byte, längere Passwörter lehnen wir ab. */
    static final int HOECHSTLAENGE_BYTE = 72;

    /** Zeichen für Einmalpasswörter, ohne leicht verwechselbare Zeichen wie 0/O oder 1/l/I. */
    private static final String ZEICHEN_FUER_EINMALPASSWORT =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final PasswordEncoder passwortVerschluesselung;
    private final Clock clock;
    private final SecureRandom zufall = new SecureRandom();

    public PasswortService(PasswordEncoder passwortVerschluesselung, Clock clock) {
        this.passwortVerschluesselung = passwortVerschluesselung;
        this.clock = clock;
    }

    /**
     * Prüft die Passwortregeln: mindestens 8 Zeichen und mindestens eine Ziffer.
     * Ist eine Regel verletzt, kommt eine {@link FachlicheException} mit deutschem Text.
     */
    public void pruefeRegeln(String passwort) {
        if (passwort == null || passwort.length() < MINDESTLAENGE) {
            throw new FachlicheException("Das Passwort muss mindestens 8 Zeichen lang sein.");
        }
        if (!enthaeltZiffer(passwort)) {
            throw new FachlicheException("Das Passwort muss mindestens eine Ziffer enthalten.");
        }
        if (istZuLang(passwort)) {
            throw new FachlicheException("Das Passwort ist zu lang. Bitte wählen Sie ein kürzeres Passwort.");
        }
    }

    /** Erzeugt den BCrypt-Hash eines Passworts. Nur der Hash wird gespeichert, nie der Klartext. */
    public String hashen(String passwort) {
        return passwortVerschluesselung.encode(passwort);
    }

    /** Prüft, ob ein eingegebenes Passwort zu einem gespeicherten Hash passt. Fehlt der Hash, passt nichts. */
    public boolean passt(String passwort, String hash) {
        if (passwort == null || hash == null || hash.isEmpty()) {
            return false;
        }
        if (istZuLang(passwort)) {
            return false;
        }
        return passwortVerschluesselung.matches(passwort, hash);
    }

    /** Erzeugt ein zufälliges Einmalpasswort (12 Zeichen, mindestens eine Ziffer). */
    public String einmalpasswortErzeugen() {
        String einmalpasswort = zufaelligeZeichen();
        while (!enthaeltZiffer(einmalpasswort)) {
            einmalpasswort = zufaelligeZeichen();
        }
        return einmalpasswort;
    }

    /**
     * Speichert ein Einmalpasswort als Hash im Konto: gültig für 24 Stunden ab jetzt.
     * Das bisherige Passwort wird ungültig, und die Person muss nach der Anmeldung
     * ein eigenes Passwort festlegen (UC-M1-10, FA-M1-09).
     */
    public void einmalpasswortSetzen(Benutzer benutzer, String einmalpasswort) {
        LocalDateTime gueltigBis = LocalDateTime.now(clock).plusHours(EINMALPASSWORT_GUELTIG_STUNDEN);
        benutzer.setEinmalpasswort(hashen(einmalpasswort), gueltigBis);
        benutzer.setPasswortHash(null);
        benutzer.setPasswortAenderungNoetig(true);
    }

    /** True, wenn das Einmalpasswort des Kontos fehlt oder nicht mehr gültig ist. */
    public boolean einmalpasswortAbgelaufen(Benutzer benutzer) {
        LocalDateTime gueltigBis = benutzer.getEinmalpasswortGueltigBis();
        if (gueltigBis == null) {
            return true;
        }
        LocalDateTime jetzt = LocalDateTime.now(clock);
        return jetzt.isAfter(gueltigBis);
    }

    private String zufaelligeZeichen() {
        StringBuilder ergebnis = new StringBuilder();
        for (int i = 0; i < EINMALPASSWORT_LAENGE; i++) {
            int position = zufall.nextInt(ZEICHEN_FUER_EINMALPASSWORT.length());
            ergebnis.append(ZEICHEN_FUER_EINMALPASSWORT.charAt(position));
        }
        return ergebnis.toString();
    }

    private boolean enthaeltZiffer(String text) {
        for (int i = 0; i < text.length(); i++) {
            char zeichen = text.charAt(i);
            if (zeichen >= '0' && zeichen <= '9') {
                return true;
            }
        }
        return false;
    }

    private boolean istZuLang(String passwort) {
        int anzahlByte = passwort.getBytes(StandardCharsets.UTF_8).length;
        return anzahlByte > HOECHSTLAENGE_BYTE;
    }
}
