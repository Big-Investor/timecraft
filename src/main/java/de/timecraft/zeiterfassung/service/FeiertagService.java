package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.repository.FeiertagRepository;
import de.timecraft.zeiterfassung.service.EingabeFehler.Feld;

/** @author Noah Cipa */
@Service
public class FeiertagService {

    public static final int MAX_LAENGE_NAME = 50;
    private static final DateTimeFormatter DATUM_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FeiertagRepository feiertagRepository;
    private final Clock clock;

    public FeiertagService(FeiertagRepository feiertagRepository, Clock clock) {
        this.feiertagRepository = feiertagRepository;
        this.clock = clock;
    }

    /**
     * Gesetzliche Feiertage fuer das laufende und das naechste Jahr (FA-M2-26). Nur Jahre ohne jeden Feiertag
     * werden vorbelegt, damit Geloeschtes und Verschobenes nicht wiederkommt (Entscheidung Noah 27.11.2026).
     */
    @Transactional
    public void vorbelegen() {
        int diesesJahr = LocalDate.now(clock).getYear();
        for (int jahr = diesesJahr; jahr <= diesesJahr + 1; jahr++) {
            if (feiertagRepository.findByDatumBetweenOrderByDatum(LocalDate.of(jahr, 1, 1),
                    LocalDate.of(jahr, 12, 31)).isEmpty()) {
                feiertagRepository.saveAll(gesetzlicheFeiertageNrw(jahr));
            }
        }
    }

    public List<Feiertag> gesetzlicheFeiertageNrw(int jahr) {
        LocalDate ostern = ostersonntag(jahr);
        List<Feiertag> feiertage = new ArrayList<>();
        feiertage.add(new Feiertag(LocalDate.of(jahr, 1, 1), "Neujahr"));
        feiertage.add(new Feiertag(ostern.minusDays(2), "Karfreitag"));
        feiertage.add(new Feiertag(ostern.plusDays(1), "Ostermontag"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 5, 1), "Tag der Arbeit"));
        feiertage.add(new Feiertag(ostern.plusDays(39), "Christi Himmelfahrt"));
        feiertage.add(new Feiertag(ostern.plusDays(50), "Pfingstmontag"));
        feiertage.add(new Feiertag(ostern.plusDays(60), "Fronleichnam"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 10, 3), "Tag der Deutschen Einheit"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 11, 1), "Allerheiligen"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 12, 25), "1. Weihnachtstag"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 12, 26), "2. Weihnachtstag"));
        return feiertage;
    }

    public LocalDate ostersonntag(int jahr) {
        int a = jahr % 19;
        int b = jahr / 100;
        int c = jahr % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int monat = (h + l - 7 * m + 114) / 31;
        int tag = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(jahr, monat, tag);
    }

    @Transactional(readOnly = true)
    public boolean istFeiertag(LocalDate datum) {
        return feiertagRepository.existsByDatum(datum);
    }

    /** Alle Feiertage im Zeitraum mit einer Abfrage. Ohne Rechtepruefung, nur fuer andere Services dieses Moduls. */
    Set<LocalDate> datenImZeitraum(LocalDate von, LocalDate bis) {
        return feiertagRepository.findByDatumBetweenOrderByDatum(von, bis).stream()
                .map(Feiertag::getDatum).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Feiertag> imMonat(Rolle rolle, YearMonth monat) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        return feiertagRepository.findByDatumBetweenOrderByDatum(monat.atDay(1), monat.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<Feiertag> imZeitraum(Rolle rolle, LocalDate von, LocalDate bis) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        return feiertagRepository.findByDatumBetweenOrderByDatum(von, bis);
    }

    public YearMonth aktuellerMonat() {
        return YearMonth.now(clock);
    }

    @Transactional(readOnly = true)
    public List<Feiertag> alle(Rolle rolle) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        return feiertagRepository.findAllByOrderByDatum();
    }

    @Transactional
    public Feiertag anlegen(Rolle rolle, LocalDate datum, String name) {
        pruefeAdmin(rolle);
        String bereinigterName = pruefeEingabe(datum, name);
        if (feiertagRepository.existsByDatum(datum)) {
            throw doppelterFeiertag(datum);
        }
        return feiertagRepository.save(new Feiertag(datum, bereinigterName));
    }

    @Transactional
    public Feiertag aendern(Rolle rolle, long feiertagId, LocalDate datum, String name) {
        pruefeAdmin(rolle);
        String bereinigterName = pruefeEingabe(datum, name);
        Feiertag feiertag = feiertag(feiertagId);
        if (!datum.equals(feiertag.getDatum()) && feiertagRepository.existsByDatum(datum)) {
            throw doppelterFeiertag(datum);
        }
        feiertag.aendern(datum, bereinigterName);
        return feiertag;
    }

    @Transactional
    public void loeschen(Rolle rolle, long feiertagId) {
        pruefeAdmin(rolle);
        feiertagRepository.delete(feiertag(feiertagId));
    }

    private Feiertag feiertag(long feiertagId) {
        return feiertagRepository.findById(feiertagId)
                .orElseThrow(() -> new NichtGefundenException("Der Feiertag wurde nicht gefunden."));
    }

    private EingabeFehler doppelterFeiertag(LocalDate datum) {
        return new EingabeFehler("Am " + datum.format(DATUM_FORMATTER) + " gibt es schon einen Feiertag.", Feld.DATUM);
    }

    private String pruefeEingabe(LocalDate datum, String name) {
        if (datum == null) {
            throw new EingabeFehler("Bitte wählen Sie ein Datum aus.", Feld.DATUM);
        }
        if (name == null || name.isBlank()) {
            throw new EingabeFehler("Bitte geben Sie einen Namen ein.", Feld.NAME);
        }
        String bereinigterName = name.trim();
        if (bereinigterName.length() > MAX_LAENGE_NAME) {
            throw new EingabeFehler("Der Name darf höchstens " + MAX_LAENGE_NAME + " Zeichen lang sein.",
                    Feld.NAME);
        }
        return bereinigterName;
    }

    private void pruefeAdmin(Rolle rolle) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        if (rolle != Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Nur Admins dürfen Feiertage pflegen.");
        }
    }
}
