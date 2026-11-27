package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Qualifikation;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Alle Eingaben des Mitarbeiterformulars (Mockup „Mitarbeiter anlegen“) in einem Objekt:
 * Stammdaten, Arbeitszeit, Urlaub, Vorgesetzte/r und Benutzerkonto.
 * Die View füllt dieses Objekt und gibt es an den {@link MitarbeiterService}. Beim Bearbeiten
 * lädt der Service die gespeicherten Werte in ein solches Objekt.
 *
 * <p>Die Konstanten (z. B. {@link #PERSONALNUMMER}) sind die Feldnamen, unter denen
 * eine {@link EingabeFehlerException} ihre Meldungen ablegt.
 *
 * @author Leon-Selim Kannenberg
 */
public class MitarbeiterDaten {

    public static final String PERSONALNUMMER = "personalnummer";
    public static final String VORNAME = "vorname";
    public static final String NACHNAME = "nachname";
    public static final String EMAIL = "email";
    public static final String TELEFON = "telefon";
    public static final String GEBURTSDATUM = "geburtsdatum";
    public static final String EINTRITTSDATUM = "eintrittsdatum";
    public static final String QUALIFIKATION = "qualifikation";
    public static final String WOCHENSTUNDEN = "wochenstunden";
    public static final String ARBEITSTAGE = "arbeitstage";
    public static final String URLAUBSANSPRUCH = "urlaubsanspruch";
    public static final String URLAUBSUEBERTRAG = "urlaubsuebertrag";
    public static final String VORGESETZTER = "vorgesetzter";
    public static final String BENUTZERNAME = "benutzername";
    public static final String ROLLE = "rolle";

    // Stammdaten (FA-M1-10, Geburtsdatum laut Teamentscheidung Pflicht)
    private String personalnummer;
    private String vorname;
    private String nachname;
    private String email;
    private String telefon;
    private LocalDate geburtsdatum;
    private LocalDate eintrittsdatum;
    private Qualifikation qualifikation;

    // Arbeitszeit, Urlaub, Vorgesetzte/r (FA-M1-16 bis FA-M1-19)
    private BigDecimal wochenstunden;
    private Set<DayOfWeek> arbeitstage = new HashSet<>();
    /** Überstunden-Startsaldo in Minuten, darf negativ sein. Leer heißt 0. */
    private Integer startsaldoMinuten;
    private Integer urlaubsanspruch;
    /** Resturlaub aus dem Vorjahr. Leer heißt 0. */
    private Integer urlaubsuebertrag;
    private Long vorgesetzterId;

    // Benutzerkonto (FA-M1-03, FA-M1-13)
    private String benutzername;
    private Rolle rolle;

    // Nur zur Anzeige beim Bearbeiten, werden beim Speichern nicht übernommen
    private Long mitarbeiterId;
    private boolean aktiv = true;
    private LocalDate austrittsdatum;
    private boolean gesperrt;
    /** Darf die angemeldete Person diesen Mitarbeiter bearbeiten? Sonst zeigt das Formular nur an. */
    private boolean bearbeitbar = true;

    /**
     * Entfernt Leerzeichen am Anfang und Ende aller Texte. Ein leeres Telefonfeld wird zu {@code null}.
     * Der Service ruft das vor der Prüfung auf.
     */
    public void eingabenBereinigen() {
        personalnummer = ohneRandLeerzeichen(personalnummer);
        vorname = ohneRandLeerzeichen(vorname);
        nachname = ohneRandLeerzeichen(nachname);
        email = ohneRandLeerzeichen(email);
        telefon = ohneRandLeerzeichen(telefon);
        if (telefon != null && telefon.isEmpty()) {
            telefon = null;
        }
        benutzername = ohneRandLeerzeichen(benutzername);
    }

    private static String ohneRandLeerzeichen(String text) {
        if (text == null) {
            return null;
        }
        return text.strip();
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

    public Qualifikation getQualifikation() {
        return qualifikation;
    }

    public void setQualifikation(Qualifikation qualifikation) {
        this.qualifikation = qualifikation;
    }

    public BigDecimal getWochenstunden() {
        return wochenstunden;
    }

    public void setWochenstunden(BigDecimal wochenstunden) {
        this.wochenstunden = wochenstunden;
    }

    /** Gibt eine Kopie zurück, damit niemand die Menge von außen verändert. */
    public Set<DayOfWeek> getArbeitstage() {
        return new HashSet<>(arbeitstage);
    }

    public void setArbeitstage(Set<DayOfWeek> arbeitstage) {
        this.arbeitstage = new HashSet<>();
        if (arbeitstage != null) {
            this.arbeitstage.addAll(arbeitstage);
        }
    }

    public Integer getStartsaldoMinuten() {
        return startsaldoMinuten;
    }

    public void setStartsaldoMinuten(Integer startsaldoMinuten) {
        this.startsaldoMinuten = startsaldoMinuten;
    }

    public Integer getUrlaubsanspruch() {
        return urlaubsanspruch;
    }

    public void setUrlaubsanspruch(Integer urlaubsanspruch) {
        this.urlaubsanspruch = urlaubsanspruch;
    }

    public Integer getUrlaubsuebertrag() {
        return urlaubsuebertrag;
    }

    public void setUrlaubsuebertrag(Integer urlaubsuebertrag) {
        this.urlaubsuebertrag = urlaubsuebertrag;
    }

    public Long getVorgesetzterId() {
        return vorgesetzterId;
    }

    public void setVorgesetzterId(Long vorgesetzterId) {
        this.vorgesetzterId = vorgesetzterId;
    }

    public String getBenutzername() {
        return benutzername;
    }

    public void setBenutzername(String benutzername) {
        this.benutzername = benutzername;
    }

    public Rolle getRolle() {
        return rolle;
    }

    public void setRolle(Rolle rolle) {
        this.rolle = rolle;
    }

    public Long getMitarbeiterId() {
        return mitarbeiterId;
    }

    public void setMitarbeiterId(Long mitarbeiterId) {
        this.mitarbeiterId = mitarbeiterId;
    }

    public boolean isAktiv() {
        return aktiv;
    }

    public void setAktiv(boolean aktiv) {
        this.aktiv = aktiv;
    }

    public LocalDate getAustrittsdatum() {
        return austrittsdatum;
    }

    public void setAustrittsdatum(LocalDate austrittsdatum) {
        this.austrittsdatum = austrittsdatum;
    }

    public boolean isGesperrt() {
        return gesperrt;
    }

    public void setGesperrt(boolean gesperrt) {
        this.gesperrt = gesperrt;
    }

    public boolean isBearbeitbar() {
        return bearbeitbar;
    }

    public void setBearbeitbar(boolean bearbeitbar) {
        this.bearbeitbar = bearbeitbar;
    }
}
