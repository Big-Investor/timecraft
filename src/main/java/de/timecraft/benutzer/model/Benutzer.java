package de.timecraft.benutzer.model;

import de.timecraft.benutzer.api.Rolle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Das Zugangskonto einer Person (Tabelle {@code benutzer}).
 * Es regelt, wer sich anmelden darf und welche Rolle er hat.
 * Passwörter stehen nur als BCrypt-Hash in der Datenbank, nie im Klartext (QA-M1-01).
 *
 * @author Leon-Selim Kannenberg
 */
@Entity
@Table(name = "benutzer")
public class Benutzer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "benutzer_id")
    private Long benutzerId;

    @Column(name = "benutzername", nullable = false, unique = true, length = 50)
    private String benutzername;

    /** Leer, solange die Person noch kein eigenes Passwort festgelegt hat. */
    @Column(name = "passwort_hash", length = 60)
    private String passwortHash;

    /** Als Text gespeichert (VARCHAR), nicht als datenbankeigener Aufzählungstyp. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "rolle", nullable = false, length = 15)
    private Rolle rolle;

    @Column(name = "gesperrt", nullable = false)
    private boolean gesperrt;

    /** Fehlversuche in Folge bei der Anmeldung. In der Datenbank als SMALLINT gespeichert. */
    @Column(name = "fehlversuche", nullable = false)
    private short fehlversuche;

    @Column(name = "einmalpasswort_hash", length = 60)
    private String einmalpasswortHash;

    @Column(name = "einmalpasswort_gueltig_bis")
    private LocalDateTime einmalpasswortGueltigBis;

    @Column(name = "passwort_aenderung_noetig", nullable = false)
    private boolean passwortAenderungNoetig;

    protected Benutzer() {
        // Nur für JPA
    }

    /** Neues Konto: nicht gesperrt, keine Fehlversuche, noch ohne Passwort. */
    public Benutzer(String benutzername, Rolle rolle) {
        this.benutzername = benutzername;
        this.rolle = rolle;
        this.gesperrt = false;
        this.fehlversuche = 0;
        this.passwortAenderungNoetig = false;
    }

    public Long getBenutzerId() {
        return benutzerId;
    }

    public String getBenutzername() {
        return benutzername;
    }

    public void setBenutzername(String benutzername) {
        this.benutzername = benutzername;
    }

    public String getPasswortHash() {
        return passwortHash;
    }

    public void setPasswortHash(String passwortHash) {
        this.passwortHash = passwortHash;
    }

    public Rolle getRolle() {
        return rolle;
    }

    public void setRolle(Rolle rolle) {
        this.rolle = rolle;
    }

    public boolean isGesperrt() {
        return gesperrt;
    }

    public void setGesperrt(boolean gesperrt) {
        this.gesperrt = gesperrt;
    }

    public int getFehlversuche() {
        return fehlversuche;
    }

    /** Zählt einen weiteren Fehlversuch bei der Anmeldung. */
    public void fehlversuchHochzaehlen() {
        fehlversuche = (short) (fehlversuche + 1);
    }

    /** Nach einer erfolgreichen Anmeldung beginnt die Zählung wieder bei null. */
    public void fehlversucheZuruecksetzen() {
        fehlversuche = 0;
    }

    public String getEinmalpasswortHash() {
        return einmalpasswortHash;
    }

    public LocalDateTime getEinmalpasswortGueltigBis() {
        return einmalpasswortGueltigBis;
    }

    /** Setzt ein neues Einmalpasswort (als Hash) mit Ablaufzeitpunkt. */
    public void setEinmalpasswort(String hash, LocalDateTime gueltigBis) {
        this.einmalpasswortHash = hash;
        this.einmalpasswortGueltigBis = gueltigBis;
    }

    /** Löscht das Einmalpasswort, z. B. nachdem es einmal benutzt wurde. */
    public void einmalpasswortLoeschen() {
        this.einmalpasswortHash = null;
        this.einmalpasswortGueltigBis = null;
    }

    public boolean isPasswortAenderungNoetig() {
        return passwortAenderungNoetig;
    }

    public void setPasswortAenderungNoetig(boolean passwortAenderungNoetig) {
        this.passwortAenderungNoetig = passwortAenderungNoetig;
    }
}
