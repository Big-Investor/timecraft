package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;

/** Eingaben aus dem Materialformular. Geprueft wird im MaterialService. */
public record MaterialDaten(String artikelnummer, String bezeichnung, String kategorie, Einheit einheit,
        BigDecimal einkaufspreis, BigDecimal verkaufspreis) {
}
