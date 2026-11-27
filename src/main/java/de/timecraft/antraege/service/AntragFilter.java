package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Antragsstatus;
import java.time.LocalDate;

/**
 * Filter der Projektleitung (US-M5-05): Typ, Status, Mitarbeiter und Antragszeitraum (Abwesenheit von–bis,
 * Zeitkorrektur Tag der Buchung, Storno Zeitraum der stornierten Abwesenheit).
 * Ein leeres Feld (null) filtert nicht.
 */
public record AntragFilter(Antragstyp typ, Antragsstatus status, Long mitarbeiterId, LocalDate von, LocalDate bis) {

    public static AntragFilter alle() {
        return new AntragFilter(null, null, null, null, null);
    }
}
