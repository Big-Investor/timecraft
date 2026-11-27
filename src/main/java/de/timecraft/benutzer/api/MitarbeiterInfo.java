// Schnittstellenvertrag des Teams (bestätigt am 04.11.2026) – nur nach Absprache ändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Lesende Sicht auf einen Mitarbeiter (keine Entität).
 * @author Leon-Selim Kannenberg */
public record MitarbeiterInfo(
        long mitarbeiterId, String personalnummer, String vorname, String nachname,
        Rolle rolle, boolean aktiv, Long vorgesetzterId, LocalDate geburtsdatum,
        BigDecimal wochenstunden, Set<DayOfWeek> arbeitstage,
        LocalDate zeitkontoStartdatum, int startsaldoMinuten) {

    public String name() { return vorname + " " + nachname; }
}
