package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;

/**
 * Eine Abwesenheit im Monatskalender. {@code art} ist null, wenn man die Art nicht sehen darf.
 * {@code arbeitstage} zaehlt nur die Arbeitstage im angezeigten Monat.
 *
 * @author Noah Cipa
 */
public record AbwesenheitImKalender(long mitarbeiterId, String name, LocalDate von, LocalDate bis,
        AbwesenheitsArt art, int arbeitstage) {
}
