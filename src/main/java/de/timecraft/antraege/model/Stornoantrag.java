package de.timecraft.antraege.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * Storno eines genehmigten Antrags (Tabelle stornoantrag, nur antrag_id; Teamentscheidung 04.11.2026).
 * Der stornierte Antrag steht in bezugsantrag_id, der Grund in begruendung.
 */
@Entity
@Table(name = "stornoantrag")
@DiscriminatorValue("STORNO")
@PrimaryKeyJoinColumn(name = "antrag_id")
public class Stornoantrag extends Antrag {
}
