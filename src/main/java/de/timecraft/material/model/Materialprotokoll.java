package de.timecraft.material.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unveraenderlicher Eintrag der Materialhistorie (Tabelle materialprotokoll).
 * Keine Setter, alle Spalten nicht aenderbar; das Repository bietet kein Loeschen an.
 *
 * @author Tharsan Arumuganathar
 */
@Entity
@Table(name = "materialprotokoll")
public class Materialprotokoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "materialprotokoll_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "materialposition_id", nullable = false, updatable = false)
    private Materialposition materialposition;

    @Column(name = "geaendert_von_id", nullable = false, updatable = false)
    private long geaendertVonId;

    @Column(name = "zeitpunkt", nullable = false, updatable = false)
    private LocalDateTime zeitpunkt;

    @Enumerated(EnumType.STRING)
    @Column(name = "aktion", nullable = false, updatable = false, length = 20)
    private Protokollaktion aktion;

    /** Leer bei GEBUCHT, weil es vorher keinen Wert gab. */
    @Column(name = "alter_wert", updatable = false, precision = 10, scale = 2)
    private BigDecimal alterWert;

    @Column(name = "neuer_wert", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal neuerWert;

    protected Materialprotokoll() {
        // Nur fuer JPA
    }

    /**
     * @param materialposition betroffene Position
     * @param geaendertVonId Person, die die Aenderung ausgeloest hat
     * @param zeitpunkt Zeitpunkt der Aenderung (Europe/Berlin)
     * @param aktion Art der Aenderung
     * @param alterWert Menge vorher, leer bei GEBUCHT
     * @param neuerWert Menge nachher
     */
    public Materialprotokoll(Materialposition materialposition, long geaendertVonId, LocalDateTime zeitpunkt,
            Protokollaktion aktion, BigDecimal alterWert, BigDecimal neuerWert) {
        this.materialposition = materialposition;
        this.geaendertVonId = geaendertVonId;
        this.zeitpunkt = zeitpunkt;
        this.aktion = aktion;
        this.alterWert = alterWert;
        this.neuerWert = neuerWert;
    }

    public Long getId() {
        return id;
    }

    public Materialposition getMaterialposition() {
        return materialposition;
    }

    public long getGeaendertVonId() {
        return geaendertVonId;
    }

    public LocalDateTime getZeitpunkt() {
        return zeitpunkt;
    }

    public Protokollaktion getAktion() {
        return aktion;
    }

    public BigDecimal getAlterWert() {
        return alterWert;
    }

    public BigDecimal getNeuerWert() {
        return neuerWert;
    }
}
