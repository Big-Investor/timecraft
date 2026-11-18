package de.timecraft.kalender.model;

import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ein Block im Kalender: der Teil einer Buchung an einem Kalendertag (Europe/Berlin).
 * Eine Buchung ueber Mitternacht ergibt zwei Eintraege. Bei einer laufenden Buchung
 * ist das Ende die aktuelle Uhrzeit. Minuten sind die anrechenbaren Minuten des Tages.
 */
public record KalenderEintrag(
        long zeiteintragId, LocalDate tag, LocalDateTime beginn, LocalDateTime ende, int minuten,
        Long projektId, String projektName, Standort standort, boolean laeuft, boolean geaendert) { }
