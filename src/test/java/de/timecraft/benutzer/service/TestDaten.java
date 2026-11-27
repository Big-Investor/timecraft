package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Hilfsmethoden für die Tests der Service-Schicht: feste Uhr und einfache Testpersonen.
 * Die Tests der Views ({@code benutzer.view}) legen ihre Testpersonen ebenfalls hiermit an.
 *
 * @author Leon-Selim Kannenberg
 */
public final class TestDaten {

    /** Feste Uhr: Samstag, 14.11.2026, 10:00 Uhr in Berlin. */
    static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-11-14T09:00:00Z"), ZoneId.of("Europe/Berlin"));

    public static final String PASSWORT = "werkbank42";

    /** Kleinste erlaubte BCrypt-Stärke, damit die Tests schnell laufen. */
    static final BCryptPasswordEncoder VERSCHLUESSELUNG = new BCryptPasswordEncoder(4);

    private TestDaten() {
    }

    static PasswortService passwortService() {
        return new PasswortService(VERSCHLUESSELUNG, FESTE_UHR);
    }

    /**
     * Ein echter {@link MitarbeiterService} mit fester Uhr und dem angegebenen Mail-Versand
     * (z. B. {@code TestMailService}), damit auch die View-Tests Versand und Versandfehler prüfen können.
     */
    public static MitarbeiterService mitarbeiterService(BenutzerRepository benutzerRepository,
            MitarbeiterRepository mitarbeiterRepository, ArbeitszeitmodellRepository arbeitszeitmodellRepository,
            ZeitkontoRepository zeitkontoRepository, UrlaubskontoRepository urlaubskontoRepository,
            MailService mailService) {
        MitarbeiterPruefung pruefung = new MitarbeiterPruefung(benutzerRepository, mitarbeiterRepository);
        EinmalpasswortVersand versand = new EinmalpasswortVersand(passwortService(), mailService, benutzerRepository);
        return new MitarbeiterService(mitarbeiterRepository, benutzerRepository, arbeitszeitmodellRepository,
                zeitkontoRepository, urlaubskontoRepository, pruefung, versand, FESTE_UHR);
    }

    /** Speichert ein Konto mit dem Passwort {@link #PASSWORT} und einen aktiven Mitarbeiter dazu. */
    public static Mitarbeiter personAnlegen(BenutzerRepository benutzerRepository,
            MitarbeiterRepository mitarbeiterRepository, String benutzername, Rolle rolle, String personalnummer) {
        Benutzer benutzer = new Benutzer(benutzername, rolle);
        benutzer.setPasswortHash(VERSCHLUESSELUNG.encode(PASSWORT));
        benutzerRepository.save(benutzer);

        Mitarbeiter mitarbeiter = new Mitarbeiter(benutzer, personalnummer, "Test", "Person " + personalnummer);
        mitarbeiter.setEmail(benutzername + "@test.example");
        mitarbeiter.setGeburtsdatum(LocalDate.of(1990, 1, 1));
        mitarbeiter.setEintrittsdatum(LocalDate.of(2020, 1, 1));
        mitarbeiter.setQualifikation(Qualifikation.GESELLE);
        return mitarbeiterRepository.save(mitarbeiter);
    }

    /**
     * Ein vollständig und richtig ausgefülltes Formular wie im Mockup „Mitarbeiter anlegen“
     * (Lukas Meyer, Azubi, 40 Stunden Montag bis Freitag, 30 Urlaubstage).
     */
    static MitarbeiterDaten formular(String personalnummer, String benutzername, Rolle rolle, Long vorgesetzterId) {
        MitarbeiterDaten daten = new MitarbeiterDaten();
        daten.setPersonalnummer(personalnummer);
        daten.setVorname("Lukas");
        daten.setNachname("Meyer");
        daten.setEmail(benutzername + "@tischlerei-hartmann.example");
        daten.setTelefon("0521 123 45 67");
        daten.setGeburtsdatum(LocalDate.of(2009, 3, 14));
        daten.setEintrittsdatum(LocalDate.of(2026, 8, 1));
        daten.setQualifikation(Qualifikation.AZUBI);
        daten.setWochenstunden(new BigDecimal("40.00"));
        daten.setArbeitstage(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        daten.setStartsaldoMinuten(0);
        daten.setUrlaubsanspruch(30);
        daten.setUrlaubsuebertrag(0);
        daten.setVorgesetzterId(vorgesetzterId);
        daten.setBenutzername(benutzername);
        daten.setRolle(rolle);
        return daten;
    }

    /** Löscht alle Benutzer und Mitarbeiter (für Tests ohne automatisches Zurückrollen). */
    public static void allesLoeschen(BenutzerRepository benutzerRepository, MitarbeiterRepository mitarbeiterRepository) {
        mitarbeiterRepository.deleteAll();
        benutzerRepository.deleteAll();
    }
}
