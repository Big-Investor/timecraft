package de.timecraft.kalender.model;

/**
 * Ein Projekt in einer Auswahlliste (z. B. Zielprojekt beim Aendern). Nur ID und Name,
 * damit die Oberflaeche keine weiteren Projektdaten (Angebotspreis …) bekommt.
 */
public record ProjektAuswahl(long projektId, String name) { }
