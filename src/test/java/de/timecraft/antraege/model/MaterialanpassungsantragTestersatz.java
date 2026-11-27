package de.timecraft.antraege.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Nur fuer Tests: steht fuer den Materialanpassungsantrag aus M4 und prueft, dass ein anderes Modul
 * von Antrag erben kann. Eigener Tabellenname und Diskriminator, damit es nach dem Merge nicht
 * mit der echten Klasse aus M4 kollidiert.
 */
@Entity
@Table(name = "materialanpassungsantrag_testersatz")
@DiscriminatorValue("MATERIAL_TEST")
@PrimaryKeyJoinColumn(name = "antrag_id")
public class MaterialanpassungsantragTestersatz extends Antrag {

    @Column(name = "projekt_id", nullable = false)
    private long projektId;

    public long getProjektId() {
        return projektId;
    }

    public void setProjektId(long projektId) {
        this.projektId = projektId;
    }
}
