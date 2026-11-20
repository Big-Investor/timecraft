package de.timecraft.material.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Geplantes Material eines Projekts mit Soll-Menge (Tabelle materialposition). Das Projekt (M3) nur als ID. */
@Entity
@Table(name = "materialposition")
public class Materialposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "materialposition_id")
    private Long id;

    @Column(name = "projekt_id", nullable = false)
    private long projektId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "soll_menge", nullable = false, precision = 10, scale = 2)
    private BigDecimal sollMenge;

    protected Materialposition() {
        // Nur fuer JPA
    }

    public Materialposition(long projektId, Material material, BigDecimal sollMenge) {
        this.projektId = projektId;
        this.material = material;
        this.sollMenge = sollMenge;
    }

    /** Erhoeht die Soll-Menge nach einem genehmigten Mehrbedarf; die Pruefung macht der Service. */
    public void erhoeheSollMenge(BigDecimal zusatzmenge) {
        this.sollMenge = this.sollMenge.add(zusatzmenge);
    }

    public Long getId() {
        return id;
    }

    public long getProjektId() {
        return projektId;
    }

    public Material getMaterial() {
        return material;
    }

    public BigDecimal getSollMenge() {
        return sollMenge;
    }
}
