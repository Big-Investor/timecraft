package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
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
@Table(name = "abwesenheit")
public class Abwesenheit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "abwesenheit_id")
    private Long id;

    @Column(name = "mitarbeiter_id", nullable = false)
    private long mitarbeiterId;

    @Column(name = "antrag_id")
    private Long antragId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AbwesenheitsArt art;

    @Column(nullable = false)
    private LocalDate von;

    @Column(nullable = false)
    private LocalDate bis;

    protected Abwesenheit() {
        // Nur fuer JPA
    }

    public Abwesenheit(long mitarbeiterId, Long antragId, AbwesenheitsArt art, LocalDate von, LocalDate bis) {
        this.mitarbeiterId = mitarbeiterId;
        this.antragId = antragId;
        this.art = art;
        this.von = von;
        this.bis = bis;
    }

    public Long getId() {
        return id;
    }

    public long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public Long getAntragId() {
        return antragId;
    }

    public AbwesenheitsArt getArt() {
        return art;
    }

    public LocalDate getVon() {
        return von;
    }

    public LocalDate getBis() {
        return bis;
    }
}
