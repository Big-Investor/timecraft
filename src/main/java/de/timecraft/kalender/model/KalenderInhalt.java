package de.timecraft.kalender.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Was der Kalender fuer einen Zeitraum zeigt: die Eintraege, die Summe je Tag
 * (jeder Tag des Zeitraums, auch mit 0) und die Summe des ganzen Zeitraums, alles in Minuten.
 * projekte sind die Projekte, auf die im Zeitraum sichtbar gebucht wurde (ohne Filter),
 * sortiert nach Name: die Auswahl fuer den Projektfilter.
 *
 * @author Bennet Antonio Brinkmeier
 */
public record KalenderInhalt(
        LocalDate ersterTag, LocalDate letzterTag, List<KalenderEintrag> eintraege,
        Map<LocalDate, Integer> tagessummen, int gesamtMinuten, List<ProjektAuswahl> projekte) {

    /** Ohne Treffer zeigt die Oberflaeche „Keine Buchungen gefunden“. */
    public boolean keineTreffer() {
        return eintraege.isEmpty();
    }
}
