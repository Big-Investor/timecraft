package de.timecraft.material.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Ein Material im Stammkatalog (Tabelle material). */
@Entity
@Table(name = "material")
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_id")
    private Long id;

    @Column(name = "artikelnummer", nullable = false, length = 20)
    private String artikelnummer;

    @Column(name = "bezeichnung", nullable = false, length = 100)
    private String bezeichnung;

    @Column(name = "kategorie", length = 50)
    private String kategorie;

    @Convert(converter = EinheitKonverter.class)
    @Column(name = "einheit", nullable = false, length = 5)
    private Einheit einheit;

    @Column(name = "einkaufspreis", nullable = false, precision = 10, scale = 2)
    private BigDecimal einkaufspreis;

    @Column(name = "verkaufspreis", nullable = false, precision = 10, scale = 2)
    private BigDecimal verkaufspreis;

    @Column(name = "aktiv", nullable = false)
    private boolean aktiv;

    protected Material() {
        // Nur fuer JPA
    }

    /** Neues Material ist aktiv. */
    public Material(String artikelnummer, String bezeichnung, String kategorie, Einheit einheit,
            BigDecimal einkaufspreis, BigDecimal verkaufspreis) {
        this.artikelnummer = artikelnummer;
        this.bezeichnung = bezeichnung;
        this.kategorie = kategorie;
        this.einheit = einheit;
        this.einkaufspreis = einkaufspreis;
        this.verkaufspreis = verkaufspreis;
        this.aktiv = true;
    }

    /** Uebernimmt geaenderte Stammdaten; die Pruefung macht der Service. */
    public void aendern(String artikelnummer, String bezeichnung, String kategorie, Einheit einheit,
            BigDecimal einkaufspreis, BigDecimal verkaufspreis) {
        this.artikelnummer = artikelnummer;
        this.bezeichnung = bezeichnung;
        this.kategorie = kategorie;
        this.einheit = einheit;
        this.einkaufspreis = einkaufspreis;
        this.verkaufspreis = verkaufspreis;
    }

    /** Material kann nicht mehr neu eingeplant werden, bestehende Positionen bleiben. */
    public void deaktivieren() {
        this.aktiv = false;
    }

    public void aktivieren() {
        this.aktiv = true;
    }

    public Long getId() {
        return id;
    }

    public String getArtikelnummer() {
        return artikelnummer;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public String getKategorie() {
        return kategorie;
    }

    public Einheit getEinheit() {
        return einheit;
    }

    public BigDecimal getEinkaufspreis() {
        return einkaufspreis;
    }

    public BigDecimal getVerkaufspreis() {
        return verkaufspreis;
    }

    public boolean isAktiv() {
        return aktiv;
    }
}
