package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;

/**
 * Ein Arbeitstag mit genehmigter Abwesenheit. {@code art} ist null, wenn man die Art nicht sehen darf.
 *
 * @author Noah Cipa
 */
public record AbwesenheitsTag(LocalDate datum, AbwesenheitsArt art) {
}
