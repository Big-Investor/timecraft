package de.timecraft.projekte.model;

/**
 * Der Zustand eines Projekts.
 * <p>
 * Ein neues Projekt ist AKTIV. Ein abgeschlossenes Projekt wird ARCHIVIERT, aber nie gelöscht,
 * damit die bisherigen Buchungen erhalten bleiben. Auf ein archiviertes Projekt kann nicht
 * mehr eingestempelt werden.
 * <p>
 * In der Datenbank steht der Name als Text (z. B. "AKTIV") in der Spalte projekt.status.
 */
public enum ProjektStatus {
    AKTIV,
    ARCHIVIERT
}
