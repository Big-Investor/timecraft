package de.timecraft.projekte.fake;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ersatz für die MitarbeiterAuskunft von Modul M1, nur im Profil "fake" (lokaler Betrieb ohne M1).
 * Erfundene Personen: Die IDs 1, 2 und 3 gehören zu den Platzhalter-Benutzern des Gerüsts
 * (mitarbeiter, projektleitung, admin). Person 8 ist ein ehemaliger Mitarbeiter.
 * Die angemeldete Person hat die Rolle aus der Property timecraft.fake.rolle.
 *
 * @author Tim Stukenbröker
 */
@Component
@Profile("fake")
public class FakeMitarbeiterAuskunft implements MitarbeiterAuskunft {

    private static final Set<DayOfWeek> ARBEITSTAGE = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final List<MitarbeiterInfo> personen = List.of(
            person(1, "Jonas", "Becker", Rolle.MITARBEITER, true),
            person(2, "Lena", "Schröder", Rolle.PROJEKTLEITUNG, true),
            person(3, "Paul", "Hartmann", Rolle.ADMIN, true),
            person(4, "Mehmet", "Yılmaz", Rolle.MITARBEITER, true),
            person(5, "Felix", "Brandt", Rolle.MITARBEITER, true),
            person(6, "Anna", "Vogt", Rolle.MITARBEITER, true),
            person(7, "Sabine", "Keller", Rolle.PROJEKTLEITUNG, true),
            person(8, "Tom", "Weber", Rolle.MITARBEITER, false));

    private final Rolle angemeldeteRolle;

    public FakeMitarbeiterAuskunft(@Value("${timecraft.fake.rolle:PROJEKTLEITUNG}") Rolle angemeldeteRolle) {
        this.angemeldeteRolle = angemeldeteRolle;
    }

    private static MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle, boolean aktiv) {
        return new MitarbeiterInfo(id, "P" + (1000 + id), vorname, nachname, rolle, aktiv, null, null,
                new BigDecimal("40"), ARBEITSTAGE, null, 0);
    }

    @Override
    public MitarbeiterInfo angemeldetePerson() {
        return personen.stream().filter(person -> person.rolle() == angemeldeteRolle).findFirst().orElseThrow();
    }

    @Override
    public Optional<MitarbeiterInfo> finde(long mitarbeiterId) {
        return personen.stream().filter(person -> person.mitarbeiterId() == mitarbeiterId).findFirst();
    }

    @Override
    public List<MitarbeiterInfo> aktive() {
        return personen.stream().filter(MitarbeiterInfo::aktiv).toList();
    }

    @Override
    public List<MitarbeiterInfo> alle() {
        return personen;
    }

    @Override
    public Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr) {
        return Optional.empty();
    }

    @Override
    public Optional<BigDecimal> stundensatz(long mitarbeiterId) {
        return Optional.empty();
    }
}
