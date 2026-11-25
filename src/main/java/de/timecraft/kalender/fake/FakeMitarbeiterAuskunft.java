package de.timecraft.kalender.fake;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.NichtAngemeldetException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fake fuer die MitarbeiterAuskunft von M1: Personen liegen im Speicher.
 *
 * @author Bennet Antonio Brinkmeier
 */
public class FakeMitarbeiterAuskunft implements MitarbeiterAuskunft {

    private final Map<Long, MitarbeiterInfo> personen = new LinkedHashMap<>();

    /** Legt eine Person mit Beispielwerten an; nur ID, Name, Rolle und aktiv sind fuer den Kalender wichtig. */
    public MitarbeiterInfo hinzufuegen(long id, String vorname, String nachname, Rolle rolle, boolean aktiv) {
        MitarbeiterInfo person = new MitarbeiterInfo(id, "P" + id, vorname, nachname, rolle, aktiv, null,
                LocalDate.of(1990, 1, 1), new BigDecimal("40"),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalDate.of(2026, 1, 1), 0);
        personen.put(id, person);
        return person;
    }

    /** Der Kalender bekommt die angemeldete Person als Parameter aus dem Geruest; hier ist niemand angemeldet. */
    @Override
    public MitarbeiterInfo angemeldetePerson() {
        throw new NichtAngemeldetException("Bitte melden Sie sich an.");
    }

    @Override
    public Optional<MitarbeiterInfo> finde(long mitarbeiterId) {
        return Optional.ofNullable(personen.get(mitarbeiterId));
    }

    @Override
    public List<MitarbeiterInfo> aktive() {
        return personen.values().stream().filter(MitarbeiterInfo::aktiv).toList();
    }

    @Override
    public List<MitarbeiterInfo> alle() {
        return new ArrayList<>(personen.values());
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
