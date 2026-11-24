package de.timecraft.material.model;

/**
 * Aktion eines Eintrags im Materialprotokoll (Spalte materialprotokoll.aktion).
 *
 * @author Tharsan Arumuganathar
 */
public enum Protokollaktion {
    /** Materialposition wurde einem Projekt zugewiesen. */
    GEBUCHT,
    /** Mehrbedarf wurde freigegeben, die Soll-Menge erhoeht. */
    GENEHMIGT
}
