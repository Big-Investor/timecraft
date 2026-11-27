package de.timecraft.antraege.fake;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.NichtAngemeldetException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Ersatz fuer M1 (nur Tests und Profil "fake"). Beispieldaten passen zu den Platzhalter-Benutzern
 * des Geruests: 1 = Mitarbeiter, 2 = Projektleitung, 3 = Admin. Urlaubsanspruch: 30 Tage je Jahr,
 * sofern nicht anders gesetzt.
 */
public class FakeMitarbeiterAuskunft implements MitarbeiterAuskunft {

    public static final long JONAS = 1;
    public static final long LENA = 2;
    public static final long ADMIN = 3;
    public static final long MEHMET = 4;
    public static final long FELIX = 5;
    public static final long SARA = 6;
    public static final long PETER = 7;

    private final Map<Long, MitarbeiterInfo> personen = new HashMap<>();
    private final Map<String, UrlaubsDaten> urlaubsdaten = new HashMap<>();

    /** Lena (2) ist Vorgesetzte von Jonas, Mehmet und Felix; Peter (7) von Lena und Sara. */
    public static FakeMitarbeiterAuskunft beispiel() {
        FakeMitarbeiterAuskunft fake = new FakeMitarbeiterAuskunft();
        fake.hinzufuegen(person(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, LENA));
        fake.hinzufuegen(person(LENA, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, PETER));
        fake.hinzufuegen(person(ADMIN, "Anna", "Admin", Rolle.ADMIN, null));
        fake.hinzufuegen(person(MEHMET, "Mehmet", "Yılmaz", Rolle.MITARBEITER, LENA));
        fake.hinzufuegen(person(FELIX, "Felix", "Brandt", Rolle.MITARBEITER, LENA));
        fake.hinzufuegen(person(SARA, "Sara", "Klein", Rolle.MITARBEITER, PETER));
        fake.hinzufuegen(person(PETER, "Peter", "Hoffmann", Rolle.PROJEKTLEITUNG, null));
        return fake;
    }

    public static MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle, Long vorgesetzterId) {
        return new MitarbeiterInfo(id, "P-" + id, vorname, nachname, rolle, true, vorgesetzterId,
                LocalDate.of(1990, 1, 1), new BigDecimal("40"),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalDate.of(2026, 1, 1), 0);
    }

    public void hinzufuegen(MitarbeiterInfo info) {
        personen.put(info.mitarbeiterId(), info);
    }

    public void setzeUrlaubsdaten(long mitarbeiterId, UrlaubsDaten daten) {
        urlaubsdaten.put(mitarbeiterId + "/" + daten.jahr(), daten);
    }

    @Override
    public MitarbeiterInfo angemeldetePerson() {
        long id = new AktuelleSitzung().mitarbeiterId();
        return finde(id).orElseThrow(() -> new NichtAngemeldetException("Bitte melden Sie sich an."));
    }

    @Override
    public Optional<MitarbeiterInfo> finde(long mitarbeiterId) {
        return Optional.ofNullable(personen.get(mitarbeiterId));
    }

    @Override
    public List<MitarbeiterInfo> aktive() {
        return alle().stream().filter(MitarbeiterInfo::aktiv).toList();
    }

    @Override
    public List<MitarbeiterInfo> alle() {
        return new ArrayList<>(personen.values());
    }

    @Override
    public Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr) {
        if (!personen.containsKey(mitarbeiterId)) {
            return Optional.empty();
        }
        return Optional.of(urlaubsdaten.getOrDefault(mitarbeiterId + "/" + jahr, new UrlaubsDaten(jahr, 30, 0)));
    }

    @Override
    public Optional<BigDecimal> stundensatz(long mitarbeiterId) {
        return Optional.empty();
    }
}
