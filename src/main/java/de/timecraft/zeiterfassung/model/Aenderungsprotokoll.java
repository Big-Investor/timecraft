package de.timecraft.zeiterfassung.model;

import java.time.Instant;

import de.timecraft.zeiterfassung.api.AenderungsArt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Unveraenderlich: keine Setter, das Repository bietet kein Update und kein Delete.
 *
 * @author Noah Cipa
 */
@Entity
@Table(name = "aenderungsprotokoll")
public class Aenderungsprotokoll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "protokoll_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zeiteintrag_id", nullable = false, updatable = false)
    private Zeiteintrag zeiteintrag;

    /** Mitarbeiter gehoert M1, deshalb nur die ID. */
    @Column(name = "geaendert_von_id", nullable = false, updatable = false)
    private long geaendertVonId;

    @Column(nullable = false, updatable = false)
    private Instant zeitpunkt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private AenderungsArt art;

    @Column(nullable = false, length = 30, updatable = false)
    private String feld;

    @Column(length = 100, updatable = false)
    private String alterWert;

    @Column(length = 100, updatable = false)
    private String neuerWert;

    @Column(length = 255, updatable = false)
    private String begruendung;

    protected Aenderungsprotokoll() {
        // Nur fuer JPA
    }

    public Aenderungsprotokoll(Zeiteintrag zeiteintrag, long geaendertVonId, Instant zeitpunkt, AenderungsArt art,
            String feld, String alterWert, String neuerWert, String begruendung) {
        this.zeiteintrag = zeiteintrag;
        this.geaendertVonId = geaendertVonId;
        this.zeitpunkt = zeitpunkt;
        this.art = art;
        this.feld = feld;
        this.alterWert = alterWert;
        this.neuerWert = neuerWert;
        this.begruendung = begruendung;
    }

    public Long getId() {
        return id;
    }

    public long getZeiteintragId() {
        return zeiteintrag.getId();
    }

    public long getGeaendertVonId() {
        return geaendertVonId;
    }

    public Instant getZeitpunkt() {
        return zeitpunkt;
    }

    public AenderungsArt getArt() {
        return art;
    }

    public String getFeld() {
        return feld;
    }

    public String getAlterWert() {
        return alterWert;
    }

    public String getNeuerWert() {
        return neuerWert;
    }

    public String getBegruendung() {
        return begruendung;
    }
}
