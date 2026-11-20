package de.timecraft.material.model;

import de.timecraft.antraege.model.Antrag;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Antrag auf Mehrbedarf fuer eine Materialposition (Tabelle materialanpassungsantrag).
 * Die gemeinsamen Felder (Antragsteller, Status, Begruendung ...) stehen in der Tabelle antrag von M5.
 */
@Entity
@Table(name = "materialanpassungsantrag")
@DiscriminatorValue("MATERIALANPASSUNG")
@PrimaryKeyJoinColumn(name = "antrag_id")
public class Materialanpassungsantrag extends Antrag {

    @ManyToOne(optional = false)
    @JoinColumn(name = "materialposition_id", nullable = false)
    private Materialposition materialposition;

    @Column(name = "zusatzmenge", nullable = false, precision = 10, scale = 2)
    private BigDecimal zusatzmenge;

    protected Materialanpassungsantrag() {
        // Nur fuer JPA
    }

    public Materialanpassungsantrag(Materialposition materialposition, BigDecimal zusatzmenge) {
        this.materialposition = materialposition;
        this.zusatzmenge = zusatzmenge;
    }

    public Materialposition getMaterialposition() {
        return materialposition;
    }

    public BigDecimal getZusatzmenge() {
        return zusatzmenge;
    }
}
