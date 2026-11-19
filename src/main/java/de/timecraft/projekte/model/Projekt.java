package de.timecraft.projekte.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ein Projekt. Jede Instanz dieser Klasse ist eine Zeile der Tabelle "projekt".
 * <p>
 * Hibernate legt die Tabelle beim Start selbst an. Die Spaltennamen entstehen aus den Feldnamen
 * (projektleitungId wird zu projektleitung_id). Alle Spalten entsprechen dem Relationenmodell.
 * <p>
 * Diese Klasse enthält nur Daten, keine Fachregeln. Die Regeln (z. B. "Zieldatum nicht vor
 * Startdatum") prüft später der ProjektService.
 */
@Entity
@Table(name = "projekt")
public class Projekt {

    /** Spalte projekt_id (BIGINT, Primärschlüssel). Die Datenbank zählt automatisch hoch. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long projektId;

    /**
     * Spalte projektleitung_id (BIGINT, Pflicht): die Mitarbeiter-ID der verantwortlichen Projektleitung.
     * Nur die Zahl, keine Verbindung zur Mitarbeiter-Tabelle (die gehört zu Modul M1).
     */
    @Column(nullable = false)
    private long projektleitungId;

    /** Spalte name (VARCHAR(100), Pflicht). Der Name muss nicht eindeutig sein. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Spalte beschreibung (TEXT, darf leer sein). TEXT hat keine feste Längengrenze. */
    @Column(columnDefinition = "TEXT")
    private String beschreibung;

    /** Spalte startdatum (DATE, Pflicht). */
    @Column(nullable = false)
    private LocalDate startdatum;

    /** Spalte zieldatum (DATE, Pflicht). */
    @Column(nullable = false)
    private LocalDate zieldatum;

    /** Spalte status (VARCHAR(10), Pflicht): als Text gespeichert, z. B. "AKTIV". */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProjektStatus status;

    /**
     * Spalte angebotspreis (NUMERIC(10,2), darf leer sein): bis zu 10 Stellen, davon 2 Nachkommastellen.
     * Gehört fachlich zu Modul M4. M3 legt nur die Spalte an und hat dafür keine Oberfläche.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal angebotspreis;

    protected Projekt() {
        // Nur für JPA: Hibernate braucht einen Konstruktor ohne Parameter, um Zeilen zu laden.
    }

    /** Legt ein neues Projekt an. Ein neues Projekt ist immer AKTIV und hat noch keinen Angebotspreis. */
    public Projekt(long projektleitungId, String name, String beschreibung, LocalDate startdatum, LocalDate zieldatum) {
        this.projektleitungId = projektleitungId;
        this.name = name;
        this.beschreibung = beschreibung;
        this.startdatum = startdatum;
        this.zieldatum = zieldatum;
        this.status = ProjektStatus.AKTIV;
    }

    public Long getProjektId() {
        return projektId;
    }

    public long getProjektleitungId() {
        return projektleitungId;
    }

    public String getName() {
        return name;
    }

    public String getBeschreibung() {
        return beschreibung;
    }

    public LocalDate getStartdatum() {
        return startdatum;
    }

    public LocalDate getZieldatum() {
        return zieldatum;
    }

    public ProjektStatus getStatus() {
        return status;
    }

    public BigDecimal getAngebotspreis() {
        return angebotspreis;
    }

    // Setter zum Ändern eines bestehenden Projekts. Sie speichern nur, was sie bekommen:
    // Ob ein Wert erlaubt ist, prüft der ProjektService, bevor er einen Setter aufruft.
    // Die Projekt-ID hat absichtlich keinen Setter: Sie bleibt für immer gleich.

    public void setProjektleitungId(long projektleitungId) {
        this.projektleitungId = projektleitungId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBeschreibung(String beschreibung) {
        this.beschreibung = beschreibung;
    }

    public void setStartdatum(LocalDate startdatum) {
        this.startdatum = startdatum;
    }

    public void setZieldatum(LocalDate zieldatum) {
        this.zieldatum = zieldatum;
    }

    public void setStatus(ProjektStatus status) {
        this.status = status;
    }

    public void setAngebotspreis(BigDecimal angebotspreis) {
        this.angebotspreis = angebotspreis;
    }
}
