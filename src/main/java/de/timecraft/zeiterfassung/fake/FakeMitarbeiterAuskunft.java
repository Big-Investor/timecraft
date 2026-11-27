package de.timecraft.zeiterfassung.fake;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.NichtAngemeldetException;

/**
 * Ersatz fuer M1 in Tests (keine Bean: Die laufende Anwendung nutzt das echte Modul). Die IDs 1 bis 3
 * passen zu den Platzhalter-Benutzern des Geruests (mitarbeiter, projektleitung, admin).
 *
 * @author Noah Cipa
 */
public class FakeMitarbeiterAuskunft implements MitarbeiterAuskunft {

    public static final long JONAS_BECKER = 1;
    public static final long PAUL_HARTMANN = 2;
    public static final long PETRA_KRUEGER = 3;
    public static final long LENA_SCHROEDER = 4;
    public static final long MEHMET_YILMAZ = 5;
    public static final long FELIX_BRANDT = 6;
    public static final long ANNA_VOGT = 7;
    public static final long LUKAS_MEYER = 8;
    public static final long UWE_SCHULZ = 9;
    public static final long SABINE_ENGEL = 10;

    private static final Set<DayOfWeek> MONTAG_BIS_FREITAG = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> MONTAG_BIS_MITTWOCH = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY);
    private static final LocalDate KONTOSTART = LocalDate.of(2026, 1, 1);
    private static final BigDecimal VOLLZEIT = new BigDecimal("40");

    private final List<MitarbeiterInfo> mitarbeiter = List.of(
            person(JONAS_BECKER, "Jonas", "Becker", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(1994, 3, 12), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            person(PAUL_HARTMANN, "Paul", "Hartmann", Rolle.PROJEKTLEITUNG, true, null,
                    LocalDate.of(1981, 7, 2), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            person(PETRA_KRUEGER, "Petra", "Krüger", Rolle.ADMIN, true, null,
                    LocalDate.of(1988, 11, 30), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            // Teilzeit: 20 Stunden an drei Tagen
            person(LENA_SCHROEDER, "Lena", "Schröder", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(1990, 5, 21), new BigDecimal("20"), MONTAG_BIS_MITTWOCH, 0),
            // Minderjaehrig bis einschliesslich 22.10.2027
            person(MEHMET_YILMAZ, "Mehmet", "Yılmaz", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(2009, 10, 23), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            person(FELIX_BRANDT, "Felix", "Brandt", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(1985, 1, 9), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            // Startsaldo: zwei Ueberstunden aus dem alten System
            person(ANNA_VOGT, "Anna", "Vogt", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(1992, 9, 14), VOLLZEIT, MONTAG_BIS_FREITAG, 120),
            person(LUKAS_MEYER, "Lukas", "Meyer", Rolle.MITARBEITER, true, PAUL_HARTMANN,
                    LocalDate.of(1998, 12, 1), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            // Ehemaliger Mitarbeiter
            person(UWE_SCHULZ, "Uwe", "Schulz", Rolle.MITARBEITER, false, PAUL_HARTMANN,
                    LocalDate.of(1970, 4, 4), VOLLZEIT, MONTAG_BIS_FREITAG, 0),
            // Zweite Projektleitung, niemandes Vorgesetzte (fuer Rechte-Tests)
            person(SABINE_ENGEL, "Sabine", "Engel", Rolle.PROJEKTLEITUNG, true, null,
                    LocalDate.of(1983, 2, 17), VOLLZEIT, MONTAG_BIS_FREITAG, 0));

    private final Rolle angemeldeteRolle;

    /** {@code angemeldeteRolle == null}: niemand ist angemeldet. */
    public FakeMitarbeiterAuskunft(Rolle angemeldeteRolle) {
        this.angemeldeteRolle = angemeldeteRolle;
    }

    @Override
    public MitarbeiterInfo angemeldetePerson() {
        if (angemeldeteRolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
        return switch (angemeldeteRolle) {
            case MITARBEITER -> finde(JONAS_BECKER).orElseThrow();
            case PROJEKTLEITUNG -> finde(PAUL_HARTMANN).orElseThrow();
            case ADMIN -> finde(PETRA_KRUEGER).orElseThrow();
        };
    }

    @Override
    public Optional<MitarbeiterInfo> finde(long mitarbeiterId) {
        for (MitarbeiterInfo info : mitarbeiter) {
            if (info.mitarbeiterId() == mitarbeiterId) {
                return Optional.of(info);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<MitarbeiterInfo> aktive() {
        List<MitarbeiterInfo> aktive = new ArrayList<>();
        for (MitarbeiterInfo info : mitarbeiter) {
            if (info.aktiv()) {
                aktive.add(info);
            }
        }
        return aktive;
    }

    @Override
    public List<MitarbeiterInfo> alle() {
        return mitarbeiter;
    }

    @Override
    public Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr) {
        if (finde(mitarbeiterId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UrlaubsDaten(jahr, 30, 2));
    }

    @Override
    public Optional<BigDecimal> stundensatz(long mitarbeiterId) {
        if (finde(mitarbeiterId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal("48.50"));
    }

    private static MitarbeiterInfo person(long id, String vorname, String nachname, Rolle rolle, boolean aktiv,
            Long vorgesetzterId, LocalDate geburtsdatum, BigDecimal wochenstunden, Set<DayOfWeek> arbeitstage,
            int startsaldoMinuten) {
        return new MitarbeiterInfo(id, "P-" + (1000 + id), vorname, nachname, rolle, aktiv, vorgesetzterId,
                geburtsdatum, wochenstunden, arbeitstage, KONTOSTART, startsaldoMinuten);
    }
}
