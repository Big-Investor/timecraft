package de.timecraft.projekte.model;

import java.time.LocalDate;

/**
 * Die Filter der Projektliste. Jeder Filter ist freiwillig: Ist ein Wert null, wird dieser Filter ignoriert.
 * Ein frisch erzeugtes Objekt hat nur null-Werte und filtert deshalb gar nichts. "Zurücksetzen" in der
 * Oberfläche heißt also einfach: ein neues ProjektFilter-Objekt nehmen.
 * <p>
 * Alle gesetzten Filter gelten gleichzeitig (UND). Datumsgrenzen gehören dazu: Ein Projekt mit dem
 * Startdatum 01.03. erscheint also auch bei "Start von 01.03.".
 */
public class ProjektFilter {

    /** Nur Projekte, denen dieser Mitarbeiter zugeordnet ist. */
    private Long mitarbeiterId;

    /** Nur Projekte mit diesem Status (aktiv oder archiviert). */
    private ProjektStatus status;

    /** Startdatum des Projekts frühestens an diesem Tag. */
    private LocalDate startVon;

    /** Startdatum des Projekts spätestens an diesem Tag. */
    private LocalDate startBis;

    /** Zieldatum des Projekts frühestens an diesem Tag. */
    private LocalDate zielVon;

    /** Zieldatum des Projekts spätestens an diesem Tag. */
    private LocalDate zielBis;

    /** Text, der im Projektnamen vorkommen muss (Groß- und Kleinschreibung egal). */
    private String name;

    public Long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public void setMitarbeiterId(Long mitarbeiterId) {
        this.mitarbeiterId = mitarbeiterId;
    }

    public ProjektStatus getStatus() {
        return status;
    }

    public void setStatus(ProjektStatus status) {
        this.status = status;
    }

    public LocalDate getStartVon() {
        return startVon;
    }

    public void setStartVon(LocalDate startVon) {
        this.startVon = startVon;
    }

    public LocalDate getStartBis() {
        return startBis;
    }

    public void setStartBis(LocalDate startBis) {
        this.startBis = startBis;
    }

    public LocalDate getZielVon() {
        return zielVon;
    }

    public void setZielVon(LocalDate zielVon) {
        this.zielVon = zielVon;
    }

    public LocalDate getZielBis() {
        return zielBis;
    }

    public void setZielBis(LocalDate zielBis) {
        this.zielBis = zielBis;
    }

    public String getName() {
        return name;
    }

    /** Ein leeres Textfeld (nichts oder nur Leerzeichen) bedeutet "nicht filtern" und wird zu null. */
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? null : name;
    }
}
