package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.zeiterfassung.api.BuchungInfo;

/**
 * Ein Antrag mit allen Daten, die Liste und Pruefung anzeigen (FA-M5-08, FA-M5-11, FA-M5-18).
 * Felder, die fuer den Typ nicht gelten, sind null.
 *
 * @param bezugsantrag        geaenderter oder stornierter Antrag (Aenderung/Storno)
 * @param buchung             aktuelle Buchung in M2 (Zeitkorrektur)
 * @param bisherigesProjekt   Projektname der Buchung (Zeitkorrektur)
 * @param gewuenschtesProjekt Projektname laut Antrag (Zeitkorrektur)
 * @param gewuenschteNettoMinuten Dauer des Wunschstands abzueglich der Pause der Buchung (Zeitkorrektur)
 * @param urlaubstage         Urlaubstage laut M2 (Urlaub)
 * @param abwesendImTeam      Teammitglieder mit Abwesenheit im Zeitraum (nur fuer die Projektleitung)
 * @param teamgroesse         uebrige Teammitglieder desselben Vorgesetzten (nur fuer die Projektleitung)
 * @param sondergenehmigung   unbezahlter Urlaub
 */
public record AntragAnsicht(Antrag antrag, Antragstyp typ, String antragstellerName, String entscheiderName,
        Antrag bezugsantrag, BuchungInfo buchung, String bisherigesProjekt, String gewuenschtesProjekt,
        Integer gewuenschteNettoMinuten, Integer urlaubstage, Integer abwesendImTeam, Integer teamgroesse,
        boolean sondergenehmigung) {
}
