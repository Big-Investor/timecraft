package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt beim Start erfundene Demo-Daten an, aber nur wenn die Tabelle {@code benutzer} noch leer ist:
 * die sieben Personen aus dem Mockup „Mitarbeiterliste“ (Tischlerei Hartmann).
 * Die Benutzernamen {@code mitarbeiter}, {@code projektleitung} und {@code admin} bleiben für das Team
 * erhalten; sie werden zuerst angelegt und bekommen in einer leeren Datenbank die IDs 1, 2 und 3
 * (wie früher die Platzhalter-Anmeldung des Gerüsts).
 * In Tests (Profil {@code test}) läuft die Klasse nicht automatisch.
 *
 * @author Leon-Selim Kannenberg
 */
@Component
@Profile("!test")
public class DemoDaten implements ApplicationRunner {

    private static final String MAIL_DOMAIN = "@tischlerei-hartmann.example";
    private static final BigDecimal VOLLZEIT = new BigDecimal("40.00");
    private static final int URLAUBSANSPRUCH = 30;

    private final BenutzerRepository benutzerRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final ArbeitszeitmodellRepository arbeitszeitmodellRepository;
    private final ZeitkontoRepository zeitkontoRepository;
    private final UrlaubskontoRepository urlaubskontoRepository;
    private final PasswortService passwortService;
    private final Clock clock;
    private final String demoPasswort;

    public DemoDaten(BenutzerRepository benutzerRepository, MitarbeiterRepository mitarbeiterRepository,
            ArbeitszeitmodellRepository arbeitszeitmodellRepository, ZeitkontoRepository zeitkontoRepository,
            UrlaubskontoRepository urlaubskontoRepository, PasswortService passwortService, Clock clock,
            @Value("${timecraft.demo.passwort}") String demoPasswort) {
        this.benutzerRepository = benutzerRepository;
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.arbeitszeitmodellRepository = arbeitszeitmodellRepository;
        this.zeitkontoRepository = zeitkontoRepository;
        this.urlaubskontoRepository = urlaubskontoRepository;
        this.passwortService = passwortService;
        this.clock = clock;
        this.demoPasswort = demoPasswort;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumente) {
        anlegenFallsLeer();
    }

    /** Legt die Demo-Daten an, wenn es noch keinen Benutzer gibt. Sonst passiert nichts. */
    public void anlegenFallsLeer() {
        if (benutzerRepository.count() > 0) {
            return;
        }
        // Reihenfolge wie bei der früheren Platzhalter-Anmeldung: mitarbeiter, projektleitung, admin.
        Mitarbeiter jonas = person("mitarbeiter", Rolle.MITARBEITER, "1012", "Jonas", "Becker",
                "jonas.becker", Qualifikation.GESELLE, LocalDate.of(1994, 5, 17), LocalDate.of(2019, 8, 1));
        Mitarbeiter lena = person("projektleitung", Rolle.PROJEKTLEITUNG, "1004", "Lena", "Schröder",
                "lena.schroeder", Qualifikation.MEISTER, LocalDate.of(1985, 2, 3), LocalDate.of(2012, 4, 1));
        Mitarbeiter paul = person("admin", Rolle.ADMIN, "1001", "Paul", "Hartmann",
                "paul.hartmann", Qualifikation.MEISTER, LocalDate.of(1968, 9, 22), LocalDate.of(2001, 1, 2));
        Mitarbeiter mehmet = person("m.yilmaz", Rolle.MITARBEITER, "1015", "Mehmet", "Yılmaz",
                "mehmet.yilmaz", Qualifikation.GESELLE, LocalDate.of(1997, 11, 8), LocalDate.of(2020, 3, 1));
        Mitarbeiter anna = person("a.vogt", Rolle.MITARBEITER, "1018", "Anna", "Vogt",
                "anna.vogt", Qualifikation.GESELLE, LocalDate.of(1999, 7, 30), LocalDate.of(2021, 9, 1));
        // Felix ist Azubi und noch unter 18 (Beispiel für den Hinweis zum Jugendarbeitsschutz).
        Mitarbeiter felix = person("f.brandt", Rolle.MITARBEITER, "1021", "Felix", "Brandt",
                "felix.brandt", Qualifikation.AZUBI, LocalDate.of(2009, 3, 12), LocalDate.of(2025, 8, 1));
        Mitarbeiter sophie = person("s.kuehn", Rolle.MITARBEITER, "1009", "Sophie", "Kühn",
                "sophie.kuehn", Qualifikation.GESELLE, LocalDate.of(1990, 12, 5), LocalDate.of(2016, 6, 1));

        // Vorgesetzte sind immer Projektleitungen (Teamentscheidung). Paul (Admin) und Lena haben keinen.
        jonas.setVorgesetzter(lena);
        mehmet.setVorgesetzter(lena);
        anna.setVorgesetzter(lena);
        felix.setVorgesetzter(lena);
        sophie.setVorgesetzter(lena);

        // Sophie ist ausgetreten: deaktiviert, nicht gelöscht.
        sophie.setAustrittsdatum(LocalDate.of(2026, 6, 30));
        sophie.setAktiv(false);

        mitarbeiterRepository.saveAll(List.of(jonas, lena, paul, mehmet, anna, felix, sophie));
    }

    /**
     * Legt eine Person vollständig an: Benutzerkonto, Mitarbeiter, Arbeitszeitmodell (40 Stunden,
     * Montag bis Freitag), Zeitkonto (Startsaldo 0 zum Jahresanfang) und Urlaubskonto (30 Tage).
     */
    private Mitarbeiter person(String benutzername, Rolle rolle, String personalnummer, String vorname,
            String nachname, String mailName, Qualifikation qualifikation, LocalDate geburtsdatum,
            LocalDate eintrittsdatum) {
        Benutzer benutzer = new Benutzer(benutzername, rolle);
        benutzer.setPasswortHash(passwortService.hashen(demoPasswort));
        benutzerRepository.save(benutzer);

        Mitarbeiter mitarbeiter = new Mitarbeiter(benutzer, personalnummer, vorname, nachname);
        mitarbeiter.setEmail(mailName + MAIL_DOMAIN);
        mitarbeiter.setTelefon("0521 4711-" + personalnummer);
        mitarbeiter.setQualifikation(qualifikation);
        mitarbeiter.setGeburtsdatum(geburtsdatum);
        mitarbeiter.setEintrittsdatum(eintrittsdatum);
        mitarbeiterRepository.save(mitarbeiter);

        Set<DayOfWeek> montagBisFreitag = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        arbeitszeitmodellRepository.save(new Arbeitszeitmodell(mitarbeiter, VOLLZEIT, montagBisFreitag));

        int jahr = LocalDate.now(clock).getYear();
        zeitkontoRepository.save(new Zeitkonto(mitarbeiter, LocalDate.of(jahr, 1, 1), 0));
        urlaubskontoRepository.save(new Urlaubskonto(mitarbeiter, jahr, URLAUBSANSPRUCH, 0));
        return mitarbeiter;
    }
}
