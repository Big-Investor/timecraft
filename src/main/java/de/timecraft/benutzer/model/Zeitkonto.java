package de.timecraft.benutzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Startwert des Zeitkontos eines Mitarbeiters (Tabelle {@code zeitkonto}, FA-M1-17).
 * M1 speichert nur den Überstunden-Startsaldo, den weiteren Saldo berechnet M2.
 * Der Saldo ist in Minuten gespeichert und darf negativ sein.
 *
 * @author Leon-Selim Kannenberg
 */
@Entity
@Table(name = "zeitkonto")
public class Zeitkonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zeitkonto_id")
    private Long zeitkontoId;

    @OneToOne
    @JoinColumn(name = "mitarbeiter_id", nullable = false, unique = true)
    private Mitarbeiter mitarbeiter;

    @Column(name = "startdatum", nullable = false)
    private LocalDate startdatum;

    @Column(name = "anfangssaldo_min", nullable = false)
    private int anfangssaldoMin;

    protected Zeitkonto() {
        // Nur für JPA
    }

    public Zeitkonto(Mitarbeiter mitarbeiter, LocalDate startdatum, int anfangssaldoMin) {
        this.mitarbeiter = mitarbeiter;
        this.startdatum = startdatum;
        this.anfangssaldoMin = anfangssaldoMin;
    }

    public Long getZeitkontoId() {
        return zeitkontoId;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public LocalDate getStartdatum() {
        return startdatum;
    }

    public int getAnfangssaldoMin() {
        return anfangssaldoMin;
    }

    public void setAnfangssaldoMin(int anfangssaldoMin) {
        this.anfangssaldoMin = anfangssaldoMin;
    }
}
