package de.timecraft.antraege.model;

import de.timecraft.zeiterfassung.api.Standort;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Antrag auf Korrektur einer abgeschlossenen Buchung (Tabelle zeitkorrekturantrag).
 * Die alten Werte liest das System beim Erstellen aus M2; die neuen Werte sind der gewuenschte
 * Stand der Buchung. Buchung und Projekt sind nur IDs (gehoeren M2 bzw. M3).
 */
@Entity
@Table(name = "zeitkorrekturantrag")
@DiscriminatorValue("ZEITKORREKTUR")
@PrimaryKeyJoinColumn(name = "antrag_id")
public class Zeitkorrekturantrag extends Antrag {

    @Column(name = "zeiteintrag_id", nullable = false)
    private long zeiteintragId;

    /** Gewuenschtes Projekt; null heisst: Buchung ohne Projekt. */
    @Column(name = "neues_projekt_id")
    private Long neuesProjektId;

    @Column(name = "alter_beginn", nullable = false)
    private Instant alterBeginn;

    @Column(name = "altes_ende", nullable = false)
    private Instant altesEnde;

    @Enumerated(EnumType.STRING)
    @Column(name = "alter_standort", length = 12)
    private Standort alterStandort;

    @Column(name = "neuer_beginn", nullable = false)
    private Instant neuerBeginn;

    @Column(name = "neues_ende", nullable = false)
    private Instant neuesEnde;

    @Enumerated(EnumType.STRING)
    @Column(name = "neuer_standort", nullable = false, length = 12)
    private Standort neuerStandort;

    public long getZeiteintragId() {
        return zeiteintragId;
    }

    public void setZeiteintragId(long zeiteintragId) {
        this.zeiteintragId = zeiteintragId;
    }

    public Long getNeuesProjektId() {
        return neuesProjektId;
    }

    public void setNeuesProjektId(Long neuesProjektId) {
        this.neuesProjektId = neuesProjektId;
    }

    public Instant getAlterBeginn() {
        return alterBeginn;
    }

    public void setAlterBeginn(Instant alterBeginn) {
        this.alterBeginn = alterBeginn;
    }

    public Instant getAltesEnde() {
        return altesEnde;
    }

    public void setAltesEnde(Instant altesEnde) {
        this.altesEnde = altesEnde;
    }

    public Standort getAlterStandort() {
        return alterStandort;
    }

    public void setAlterStandort(Standort alterStandort) {
        this.alterStandort = alterStandort;
    }

    public Instant getNeuerBeginn() {
        return neuerBeginn;
    }

    public void setNeuerBeginn(Instant neuerBeginn) {
        this.neuerBeginn = neuerBeginn;
    }

    public Instant getNeuesEnde() {
        return neuesEnde;
    }

    public void setNeuesEnde(Instant neuesEnde) {
        this.neuesEnde = neuesEnde;
    }

    public Standort getNeuerStandort() {
        return neuerStandort;
    }

    public void setNeuerStandort(Standort neuerStandort) {
        this.neuerStandort = neuerStandort;
    }
}
