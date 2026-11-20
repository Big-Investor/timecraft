package de.timecraft.material.model;

/** Aktion eines Eintrags im Materialprotokoll (Spalte materialprotokoll.aktion). */
public enum Protokollaktion {
    /** Materialposition wurde einem Projekt zugewiesen. */
    GEBUCHT,
    /** Mehrbedarf wurde freigegeben, die Soll-Menge erhoeht. */
    GENEHMIGT
}
