package de.timecraft.projekte.model;

import de.timecraft.zeiterfassung.api.Standort;

/**
 * Die Filter der Team-Übersicht für die verantwortliche Projektleitung (FA-M3-09).
 * Jeder Filter ist freiwillig: Ist ein Wert null, wird er ignoriert. "Zurücksetzen" heißt also:
 * beide Werte null.
 * <p>
 * Beide Filter wirken gemeinsam. Der Standortfilter gilt nur für eingestempelte Personen: Wer nicht
 * eingestempelt ist, hat keinen Standort und erscheint bei gesetztem Standortfilter nie.
 *
 * @param eingestempelt true = nur eingestempelte Personen, false = nur nicht eingestempelte, null = alle
 * @param standort      nur Personen, die gerade an diesem Standort eingestempelt sind; null = alle
 *
 * @author Tim Stukenbröker
 */
public record TeamFilter(Boolean eingestempelt, Standort standort) { }
