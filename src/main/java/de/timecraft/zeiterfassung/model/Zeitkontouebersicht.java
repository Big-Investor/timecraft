package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Zeitkonto des laufenden Jahres: Saldo am Anfang und je Monat eine Zeile.
 *
 * @author Noah Cipa
 */
public record Zeitkontouebersicht(LocalDate anfangssaldoDatum, int anfangssaldoMinuten,
        List<ZeitkontoZeile> monate) {
}
