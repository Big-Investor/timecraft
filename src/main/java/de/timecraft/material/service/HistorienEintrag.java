package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Eintrag der Projekthistorie Material (FA-M4-06): eingeplante Positionen und genehmigte Mehrbedarfe.
 *
 * @param anzahl Zahl der an diesem Tag von dieser Person eingeplanten Positionen (bei GENEHMIGT 1)
 * @param menge Soll-Menge der (ersten) eingeplanten Position, bei GENEHMIGT die Zusatzmenge
 * @param begruendung nur bei GENEHMIGT
 */
public record HistorienEintrag(LocalDateTime zeitpunkt, Art art, int anzahl, String bezeichnung, Einheit einheit,
        BigDecimal menge, String begruendung, String person) {

    /** ANGELEGT: erste Planung des Projekts; EINGEPLANT: spaeter hinzugefuegte Positionen. */
    public enum Art { ANGELEGT, EINGEPLANT, GENEHMIGT }
}
