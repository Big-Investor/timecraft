package de.timecraft.zeiterfassung.model;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Anzahl je Status ueber alle angezeigten Konten, die zum Filter passenden Personen und der Stand der Abfrage.
 *
 * @author Noah Cipa
 */
public record AnwesenheitsUebersicht(Map<StempelStatus, Integer> anzahlJeStatus, List<Anwesenheit> treffer,
        LocalTime stand) {
}
