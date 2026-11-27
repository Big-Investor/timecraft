package de.timecraft.zeiterfassung.model;

import java.time.Instant;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.Standort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** @author Noah Cipa */
@Entity
@Table(name = "zeiteintrag")
public class Zeiteintrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zeiteintrag_id")
    private Long id;

    @Column(name = "mitarbeiter_id", nullable = false)
    private long mitarbeiterId;

    @Column(name = "projekt_id")
    private Long projektId;

    @Column(nullable = false)
    private Instant beginn;

    /** null: Der Zeiteintrag laeuft noch. */
    private Instant ende;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Standort standort;

    /** Kann-Anforderung FA-M2-K1, zurueckgestellt: nur die Spalte, keine Oberflaeche. */
    @Column(length = 255)
    private String notiz;

    private Boolean geaendert;

    protected Zeiteintrag() {
        // Nur fuer JPA
    }

    public Zeiteintrag(long mitarbeiterId, Instant beginn, Standort standort, Long projektId) {
        this.mitarbeiterId = mitarbeiterId;
        this.beginn = beginn;
        this.standort = standort;
        this.projektId = projektId;
    }

    public Long getId() {
        return id;
    }

    public long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public Long getProjektId() {
        return projektId;
    }

    public Instant getBeginn() {
        return beginn;
    }

    public Instant getEnde() {
        return ende;
    }

    public Standort getStandort() {
        return standort;
    }

    public boolean isGeaendert() {
        return Boolean.TRUE.equals(geaendert);
    }

    public void aendern(Instant beginn, Instant ende, Standort standort, Long projektId) {
        this.beginn = beginn;
        this.ende = ende;
        this.standort = standort;
        this.projektId = projektId;
        this.geaendert = true;
    }

    public void alsGeaendertMarkieren() {
        this.geaendert = true;
    }

    public void ausstempeln(Instant ende) {
        if (this.ende != null) {
            throw new FachlicheException("Der Zeiteintrag ist schon beendet.");
        }
        if (ende.isBefore(beginn)) {
            throw new FachlicheException("Das Ende darf nicht vor dem Beginn liegen.");
        }
        this.ende = ende;
    }
}
