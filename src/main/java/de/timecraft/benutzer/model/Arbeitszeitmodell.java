package de.timecraft.benutzer.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.Set;

/**
 * Wochenarbeitszeit und Arbeitstage eines Mitarbeiters (Tabelle {@code arbeitszeitmodell}, FA-M1-16).
 * Die Arbeitstage stehen in der eigenen Tabelle {@code arbeitstag}: eine Zeile je Wochentag.
 * Aus diesen Werten berechnet die Zeiterfassung (M2) das Tages-Soll.
 *
 * @author Leon-Selim Kannenberg
 */
@Entity
@Table(name = "arbeitszeitmodell")
public class Arbeitszeitmodell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "arbeitszeitmodell_id")
    private Long arbeitszeitmodellId;

    @OneToOne
    @JoinColumn(name = "mitarbeiter_id", nullable = false, unique = true)
    private Mitarbeiter mitarbeiter;

    /** Stunden pro Woche, z. B. 40,00. Höchstens zwei Stellen vor und zwei nach dem Komma. */
    @Column(name = "wochenstunden", nullable = false, precision = 4, scale = 2)
    private BigDecimal wochenstunden;

    /** Tabelle arbeitstag: Primärschlüssel ist (arbeitszeitmodell_id, wochentag). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "arbeitstag", joinColumns = @JoinColumn(name = "arbeitszeitmodell_id"))
    @Column(name = "wochentag", nullable = false)
    @Convert(converter = WochentagKonverter.class)
    private Set<DayOfWeek> arbeitstage = new HashSet<>();

    protected Arbeitszeitmodell() {
        // Nur für JPA
    }

    public Arbeitszeitmodell(Mitarbeiter mitarbeiter, BigDecimal wochenstunden, Set<DayOfWeek> arbeitstage) {
        this.mitarbeiter = mitarbeiter;
        this.wochenstunden = wochenstunden;
        setArbeitstage(arbeitstage);
    }

    public Long getArbeitszeitmodellId() {
        return arbeitszeitmodellId;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public BigDecimal getWochenstunden() {
        return wochenstunden;
    }

    public void setWochenstunden(BigDecimal wochenstunden) {
        this.wochenstunden = wochenstunden;
    }

    /** Gibt eine Kopie zurück, damit niemand die Liste von außen verändert. */
    public Set<DayOfWeek> getArbeitstage() {
        return new HashSet<>(arbeitstage);
    }

    /** Ersetzt die bisherigen Arbeitstage durch die neuen. */
    public void setArbeitstage(Set<DayOfWeek> neueArbeitstage) {
        arbeitstage.clear();
        arbeitstage.addAll(neueArbeitstage);
    }
}
