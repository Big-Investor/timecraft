package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eintrag der Projekthistorie Material (FA-M4-06): eingeplante Positionen und genehmigte Mehrbedarfe.
 *
 * @param menge bei EINGEPLANT die Soll-Menge, bei GENEHMIGT die Zusatzmenge
 * @param begruendung nur bei GENEHMIGT
 */
public record HistorienEintrag(LocalDateTime zeitpunkt, Art art, String bezeichnung, Einheit einheit,
        BigDecimal menge, String begruendung, String person) {

    public enum Art { EINGEPLANT, GENEHMIGT }
}
