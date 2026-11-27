package de.timecraft.antraege.model;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Antrag auf Urlaub, Meldung einer Krankheit oder einer sonstigen Abwesenheit (Tabelle abwesenheitsantrag). */
@Entity
@Table(name = "abwesenheitsantrag")
@DiscriminatorValue("ABWESENHEIT")
@PrimaryKeyJoinColumn(name = "antrag_id")
public class Abwesenheitsantrag extends Antrag {

    @Enumerated(EnumType.STRING)
    @Column(name = "art", nullable = false, length = 10)
    private AbwesenheitsArt art;

    /** Nur bei Urlaub gesetzt. */
    @Enumerated(EnumType.STRING)
    @Column(name = "urlaubsart", length = 20)
    private Urlaubsart urlaubsart;

    @Column(name = "von", nullable = false)
    private LocalDate von;

    @Column(name = "bis", nullable = false)
    private LocalDate bis;

    public AbwesenheitsArt getArt() {
        return art;
    }

    public void setArt(AbwesenheitsArt art) {
        this.art = art;
    }

    public Urlaubsart getUrlaubsart() {
        return urlaubsart;
    }

    public void setUrlaubsart(Urlaubsart urlaubsart) {
        this.urlaubsart = urlaubsart;
    }

    public LocalDate getVon() {
        return von;
    }

    public void setVon(LocalDate von) {
        this.von = von;
    }

    public LocalDate getBis() {
        return bis;
    }

    public void setBis(LocalDate bis) {
        this.bis = bis;
    }
}
