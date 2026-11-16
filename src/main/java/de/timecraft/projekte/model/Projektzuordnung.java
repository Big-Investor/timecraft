package de.timecraft.projekte.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Die Zuordnung eines Mitarbeiters zu einem Projekt. Jede Instanz ist eine Zeile der Tabelle "projektzuordnung".
 * <p>
 * Der Primärschlüssel besteht aus ZWEI Spalten: mitarbeiter_id und projekt_id. Dadurch kann derselbe
 * Mitarbeiter einem Projekt nur ein einziges Mal zugeordnet sein, in mehreren Projekten aber schon.
 * JPA braucht für so einen zusammengesetzten Schlüssel eine kleine Hilfsklasse ({@link Schluessel}).
 * <p>
 * Auch hier keine Fachregeln, nur Daten. Wer wen zuordnen darf, prüft später der Service.
 */
@Entity
@Table(name = "projektzuordnung")
@IdClass(Projektzuordnung.Schluessel.class)
public class Projektzuordnung {

    /**
     * Spalte mitarbeiter_id (BIGINT, Teil des Primärschlüssels): die Mitarbeiter-ID.
     * Nur die Zahl, keine Verbindung zur Mitarbeiter-Tabelle (die gehört zu Modul M1).
     */
    @Id
    private long mitarbeiterId;

    /**
     * Spalte projekt_id (BIGINT, Teil des Primärschlüssels): die Projekt-ID.
     * Der Spaltenname steht hier ausdrücklich, weil dieselbe Spalte unten noch einmal vorkommt
     * (Verbindung zu Projekt). Beide müssen den Namen gleich schreiben, sonst lehnt Hibernate es ab.
     */
    @Id
    @Column(name = "projekt_id")
    private long projektId;

    /** Spalte zugeordnet_am (TIMESTAMP, Pflicht): wann die Zuordnung angelegt wurde. */
    @Column(nullable = false)
    private LocalDateTime zugeordnetAm;

    /**
     * Diese Verbindung wird im Code nie benutzt. Sie ist nur dafür da, dass die Datenbank einen
     * Fremdschlüssel von projektzuordnung.projekt_id auf projekt.projekt_id anlegt. So kann keine
     * Zuordnung auf ein Projekt zeigen, das es nicht gibt. Geschrieben wird weiter über das Feld projektId
     * (insertable = false, updatable = false: diese Verbindung schreibt nichts in die Datenbank).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projekt_id", insertable = false, updatable = false)
    private Projekt projekt;

    protected Projektzuordnung() {
        // Nur für JPA: Hibernate braucht einen Konstruktor ohne Parameter, um Zeilen zu laden.
    }

    public Projektzuordnung(long mitarbeiterId, long projektId, LocalDateTime zugeordnetAm) {
        this.mitarbeiterId = mitarbeiterId;
        this.projektId = projektId;
        this.zugeordnetAm = zugeordnetAm;
    }

    public long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public long getProjektId() {
        return projektId;
    }

    public LocalDateTime getZugeordnetAm() {
        return zugeordnetAm;
    }

    /**
     * Der zusammengesetzte Primärschlüssel: das Paar (mitarbeiterId, projektId).
     * Die Feldnamen müssen genau so heißen wie die mit @Id markierten Felder oben.
     * equals und hashCode vergleichen beide Werte, damit JPA zwei Schlüssel als gleich erkennt.
     */
    public static class Schluessel implements Serializable {

        private long mitarbeiterId;
        private long projektId;

        protected Schluessel() {
            // Nur für JPA
        }

        public Schluessel(long mitarbeiterId, long projektId) {
            this.mitarbeiterId = mitarbeiterId;
            this.projektId = projektId;
        }

        @Override
        public boolean equals(Object anderes) {
            if (this == anderes) {
                return true;
            }
            if (!(anderes instanceof Schluessel schluessel)) {
                return false;
            }
            return mitarbeiterId == schluessel.mitarbeiterId && projektId == schluessel.projektId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mitarbeiterId, projektId);
        }
    }
}
