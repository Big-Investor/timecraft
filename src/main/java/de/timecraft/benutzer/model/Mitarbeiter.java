package de.timecraft.benutzer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Die Personalstammdaten einer beschäftigten Person (Tabelle {@code mitarbeiter}).
 * Jeder Mitarbeiter ist mit genau einem Benutzerkonto verknüpft (1:1, FA-M1-13).
 * Der Vorgesetzte ist selbst ein Mitarbeiter (Fremdschlüssel auf dieselbe Tabelle).
 *
 * @author Leon-Selim Kannenberg
 */
@Entity
@Table(name = "mitarbeiter")
public class Mitarbeiter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mitarbeiter_id")
    private Long mitarbeiterId;

    @OneToOne
    @JoinColumn(name = "benutzer_id", nullable = false, unique = true)
    private Benutzer benutzer;

    /** Darf leer sein (z. B. beim Admin), aber nie der Mitarbeiter selbst. */
    @ManyToOne
    @JoinColumn(name = "vorgesetzter_id")
    private Mitarbeiter vorgesetzter;

    @Column(name = "personalnummer", nullable = false, unique = true, length = 10)
    private String personalnummer;

    @Column(name = "vorname", nullable = false, length = 50)
    private String vorname;

    @Column(name = "nachname", nullable = false, length = 50)
    private String nachname;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "telefon", length = 30)
    private String telefon;

    @Column(name = "geburtsdatum", nullable = false)
    private LocalDate geburtsdatum;

    @Column(name = "eintrittsdatum", nullable = false)
    private LocalDate eintrittsdatum;

    @Column(name = "austrittsdatum")
    private LocalDate austrittsdatum;

    /** Als Text gespeichert (VARCHAR), nicht als datenbankeigener Aufzählungstyp. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "qualifikation", nullable = false, length = 10)
    private Qualifikation qualifikation;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv;

    /** Interner Stundensatz in Euro pro Stunde (FA-M1-K4, zurückgestellt): Spalte und Lesen über die api sind vorbereitet, es gibt noch keine Eingabe. */
    @Column(name = "stundensatz", precision = 8, scale = 2)
    private BigDecimal stundensatz;

    protected Mitarbeiter() {
        // Nur für JPA
    }

    /** Neuer Mitarbeiter: Er ist sofort aktiv. Die übrigen Felder werden mit den Settern gefüllt. */
    public Mitarbeiter(Benutzer benutzer, String personalnummer, String vorname, String nachname) {
        this.benutzer = benutzer;
        this.personalnummer = personalnummer;
        this.vorname = vorname;
        this.nachname = nachname;
        this.aktiv = true;
    }

    /** Vor- und Nachname zusammen, z. B. "Lena Schröder". */
    public String getName() {
        return vorname + " " + nachname;
    }

    public Long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public Benutzer getBenutzer() {
        return benutzer;
    }

    public Mitarbeiter getVorgesetzter() {
        return vorgesetzter;
    }

    public void setVorgesetzter(Mitarbeiter vorgesetzter) {
        this.vorgesetzter = vorgesetzter;
    }

    public String getPersonalnummer() {
        return personalnummer;
    }

    public void setPersonalnummer(String personalnummer) {
        this.personalnummer = personalnummer;
    }

    public String getVorname() {
        return vorname;
    }

    public void setVorname(String vorname) {
        this.vorname = vorname;
    }

    public String getNachname() {
        return nachname;
    }

    public void setNachname(String nachname) {
        this.nachname = nachname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefon() {
        return telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }

    public LocalDate getGeburtsdatum() {
        return geburtsdatum;
    }

    public void setGeburtsdatum(LocalDate geburtsdatum) {
        this.geburtsdatum = geburtsdatum;
    }

    public LocalDate getEintrittsdatum() {
        return eintrittsdatum;
    }

    public void setEintrittsdatum(LocalDate eintrittsdatum) {
        this.eintrittsdatum = eintrittsdatum;
    }

    public LocalDate getAustrittsdatum() {
        return austrittsdatum;
    }

    public void setAustrittsdatum(LocalDate austrittsdatum) {
        this.austrittsdatum = austrittsdatum;
    }

    public Qualifikation getQualifikation() {
        return qualifikation;
    }

    public void setQualifikation(Qualifikation qualifikation) {
        this.qualifikation = qualifikation;
    }

    public boolean isAktiv() {
        return aktiv;
    }

    public void setAktiv(boolean aktiv) {
        this.aktiv = aktiv;
    }

    public BigDecimal getStundensatz() {
        return stundensatz;
    }

    public void setStundensatz(BigDecimal stundensatz) {
        this.stundensatz = stundensatz;
    }
}
