package de.timecraft.kalender.model;

import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Inhalt des Detailfensters einer Buchung (FA-M6-05, 10). Zeiten sind Ortszeiten (Europe/Berlin).
 * Bei einer laufenden Buchung ist ende == null und die Dauer reicht bis jetzt.
 * Die Dauer abgeschlossener Buchungen ist die Nettodauer von M2 (nach Pausenabzug).
 *
 * @author Bennet Antonio Brinkmeier
 */
public record BuchungDetails(
        long zeiteintragId, Long projektId, String projektName, String projektleitung, Standort standort,
        LocalDateTime beginn, LocalDateTime ende, int dauerMinuten, boolean laeuft, boolean geaendert,
        List<HistorienEintrag> historie) { }
