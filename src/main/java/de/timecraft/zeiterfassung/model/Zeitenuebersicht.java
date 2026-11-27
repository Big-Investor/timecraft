package de.timecraft.zeiterfassung.model;

import java.time.Duration;
import java.util.List;

/**
 * Zeiteintraege eines Zeitraums je Woche und Tag mit den Netto-Summen (FA-M2-08). Alle Summen zaehlen nur
 * Eintraege, die im Zeitraum beginnen.
 *
 * @author Noah Cipa
 */
public record Zeitenuebersicht(List<UebersichtWoche> wochen, Duration summe) {

    /** Wahr, wenn im Zeitraum kein Zeiteintrag liegt, auch wenn Feiertage oder Abwesenheiten da sind (FA-M2-09). */
    public boolean ohneZeiteintraege() {
        return wochen.stream().flatMap(woche -> woche.tage().stream()).allMatch(tag -> tag.eintraege().isEmpty());
    }
}
