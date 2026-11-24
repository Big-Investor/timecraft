package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;

/**
 * Geplantes Material eines Projekts fuer den Mehrbedarf, ohne Preise (QA-M4-02).
 *
 * @param positionId Materialposition
 * @param bezeichnung Material
 * @param einheit Einheit
 * @param sollMenge geplante Menge
 *
 * @author Tharsan Arumuganathar
 */
public record PlanungOhnePreis(long positionId, String bezeichnung, Einheit einheit, BigDecimal sollMenge) {
}
