package de.timecraft.kalender.model;

import de.timecraft.zeiterfassung.api.Standort;

/**
 * Filter des Kalenders nach Projekt und Standort (FA-M6-06). Ein Wert null heisst „alle“.
 * Beide Filter wirken gemeinsam; KEINER setzt alles zurueck.
 */
public record KalenderFilter(Long projektId, Standort standort) {

    public static final KalenderFilter KEINER = new KalenderFilter(null, null);
}
