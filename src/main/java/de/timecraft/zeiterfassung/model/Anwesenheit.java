package de.timecraft.zeiterfassung.model;

import de.timecraft.zeiterfassung.api.Standort;

/**
 * Bewusst nur Name, Status, Standort und Projekt (FA-M2-23).
 *
 * @author Noah Cipa
 */
public record Anwesenheit(long mitarbeiterId, String name, StempelStatus status, Standort standort, Long projektId,
        String projektName) {
}
