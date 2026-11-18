package de.timecraft.kalender.service;

import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Liest den Kalender einer Person ueber die api von M2 (Buchungen) und M3 (Projektnamen).
 * Aendert nichts. Die Zeitzone kommt aus der Clock des Geruests (Europe/Berlin).
 */
@Service
public class KalenderService {

    private final BuchungsAuskunft buchungsAuskunft;
    private final ProjektAuskunft projektAuskunft;
    private final Clock clock;

    /**
     * "@Lazy": Spring holt BuchungsAuskunft (M2) und ProjektAuskunft (M3) erst beim ersten Aufruf.
     * So startet die Anwendung auch in diesem Branch, in dem es M2 und M3 noch nicht gibt
     * (gleiche Loesung wie in M3).
     */
    public KalenderService(@Lazy BuchungsAuskunft buchungsAuskunft, @Lazy ProjektAuskunft projektAuskunft,
            Clock clock) {
        this.buchungsAuskunft = buchungsAuskunft;
        this.projektAuskunft = projektAuskunft;
        this.clock = clock;
    }

    /** Heutiges Datum in der Zeitzone der Clock (Europe/Berlin). */
    public LocalDate heute() {
        return LocalDate.now(clock);
    }

    /** Erster Tag des Zeitraums, in dem der Bezugstag liegt. Eine Woche beginnt am Montag. */
    public LocalDate ersterTag(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag;
            case WOCHE -> bezugstag.minusDays(bezugstag.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            case MONAT -> bezugstag.withDayOfMonth(1);
        };
    }

    /** Letzter Tag des Zeitraums (einschliesslich). Eine Woche endet am Sonntag. */
    public LocalDate letzterTag(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag;
            case WOCHE -> ersterTag(ansicht, bezugstag).plusDays(6);
            case MONAT -> bezugstag.withDayOfMonth(bezugstag.lengthOfMonth());
        };
    }

    /** Bezugstag des naechsten Zeitraums („Vor“). */
    public LocalDate vor(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag.plusDays(1);
            case WOCHE -> bezugstag.plusWeeks(1);
            case MONAT -> bezugstag.plusMonths(1);
        };
    }

    /** Bezugstag des vorigen Zeitraums („Zurück“). */
    public LocalDate zurueck(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag.minusDays(1);
            case WOCHE -> bezugstag.minusWeeks(1);
            case MONAT -> bezugstag.minusMonths(1);
        };
    }
}
