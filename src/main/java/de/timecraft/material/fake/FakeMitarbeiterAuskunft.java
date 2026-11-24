package de.timecraft.material.fake;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.NichtAngemeldetException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake (Tests und Profil "fake") fuer die MitarbeiterAuskunft von M1: Personen und Stundensaetze im Speicher.
 *
 * @author Tharsan Arumuganathar
 */
public class FakeMitarbeiterAuskunft implements MitarbeiterAuskunft {

    private final Map<Long, MitarbeiterInfo> personen = new LinkedHashMap<>();
    private final Map<Long, BigDecimal> stundensaetze = new LinkedHashMap<>();

    /**
     * Legt eine aktive Person an.
     *
     * @param id Mitarbeiter-ID
     * @param vorname Vorname
     * @param nachname Nachname
     * @param rolle Rolle des Kontos
     * @param stundensatz Stundensatz in Euro; null heisst: kein Stundensatz hinterlegt
     */
    public void hinzufuegen(long id, String vorname, String nachname, Rolle rolle, BigDecimal stundensatz) {
        personen.put(id, new MitarbeiterInfo(id, "P-" + id, vorname, nachname, rolle, true, null,
                LocalDate.of(1990, 1, 1), new BigDecimal("40.0"),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 1, 1), 0));
        if (stundensatz != null) {
            stundensaetze.put(id, stundensatz);
        }
    }

    @Override
    public MitarbeiterInfo angemeldetePerson() {
        throw new NichtAngemeldetException("Im Fake gibt es keine Sitzung.");
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
        return List.copyOf(personen.values());
    }

    @Override
    public Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr) {
        return Optional.empty();
    }

    @Override
    public Optional<BigDecimal> stundensatz(long mitarbeiterId) {
        return Optional.ofNullable(stundensaetze.get(mitarbeiterId));
    }
}
