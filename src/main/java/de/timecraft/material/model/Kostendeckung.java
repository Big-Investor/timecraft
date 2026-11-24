package de.timecraft.material.model;

/**
 * Ergebnis der Pruefung Angebotspreis gegen Gesamtauftragskosten (FA-M4-08).
 *
 * @author Tharsan Arumuganathar
 */
public enum Kostendeckung {
    GEDECKT,
    UNTERDECKUNG,
    /** Ohne Angebotspreis gibt es keine Warnung, nur den Hinweis "Angebotspreis fehlt". */
    ANGEBOTSPREIS_FEHLT
}
