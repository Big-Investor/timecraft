package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;

/**
 * Ein Monat der Jahresuebersicht. {@code ab} ist im Startmonat des Kontos gesetzt, {@code bis} im laufenden
 * Monat, sobald dort schon ein Tag gerechnet ist. Sonst sind beide null.
 *
 * @author Noah Cipa
 */
public record ZeitkontoZeile(ZeitkontoMonat konto, boolean laufend, LocalDate ab, LocalDate bis) {
}
