package de.timecraft.benutzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Urlaubsanspruch und Resturlaub-Übertrag eines Mitarbeiters für ein Jahr
 * (Tabelle {@code urlaubskonto}, FA-M1-18). Je Mitarbeiter und Jahr gibt es höchstens eine Zeile.
 * Den aktuellen Resturlaub berechnet die Urlaubsverwaltung, nicht M1.
 *
 * <p>Die drei Zahlen sind in der Datenbank SMALLINT. In Java sind sie deshalb {@code short};
 * nach außen gibt die Klasse aber normale {@code int}-Werte heraus.
 *
 * @author Leon-Selim Kannenberg
 */
@Entity
@Table(name = "urlaubskonto",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mitarbeiter_id", "jahr"}))
public class Urlaubskonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "urlaubskonto_id")
    private Long urlaubskontoId;

    @ManyToOne
    @JoinColumn(name = "mitarbeiter_id", nullable = false)
    private Mitarbeiter mitarbeiter;

    @Column(name = "jahr", nullable = false)
    private short jahr;

    @Column(name = "anspruch_tage", nullable = false)
    private short anspruchTage;

    @Column(name = "uebertrag_tage", nullable = false)
    private short uebertragTage;

    protected Urlaubskonto() {
        // Nur für JPA
    }

    public Urlaubskonto(Mitarbeiter mitarbeiter, int jahr, int anspruchTage, int uebertragTage) {
        this.mitarbeiter = mitarbeiter;
        this.jahr = (short) jahr;
        this.anspruchTage = (short) anspruchTage;
        this.uebertragTage = (short) uebertragTage;
    }

    public Long getUrlaubskontoId() {
        return urlaubskontoId;
    }

    public Mitarbeiter getMitarbeiter() {
        return mitarbeiter;
    }

    public int getJahr() {
        return jahr;
    }

    public int getAnspruchTage() {
        return anspruchTage;
    }

    public void setAnspruchTage(int anspruchTage) {
        this.anspruchTage = (short) anspruchTage;
    }

    public int getUebertragTage() {
        return uebertragTage;
    }

    public void setUebertragTage(int uebertragTage) {
        this.uebertragTage = (short) uebertragTage;
    }
}
