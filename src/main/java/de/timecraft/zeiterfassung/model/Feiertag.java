package de.timecraft.zeiterfassung.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** @author Noah Cipa */
@Entity
@Table(name = "feiertag")
public class Feiertag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feiertag_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate datum;

    @Column(nullable = false, length = 50)
    private String name;

    protected Feiertag() {
        // Nur fuer JPA
    }

    public Feiertag(LocalDate datum, String name) {
        this.datum = datum;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDatum() {
        return datum;
    }

    public String getName() {
        return name;
    }

    public void aendern(LocalDate datum, String name) {
        this.datum = datum;
        this.name = name;
    }
}
