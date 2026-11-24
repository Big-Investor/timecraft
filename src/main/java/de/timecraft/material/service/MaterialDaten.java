package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;

/**
 * Eingaben aus dem Materialformular. Geprueft wird im MaterialService.
 *
 * @param artikelnummer Artikelnummer
 * @param bezeichnung Bezeichnung
 * @param kategorie Kategorie, darf leer sein
 * @param einheit Einheit
 * @param einkaufspreis EK je Einheit
 * @param verkaufspreis VK je Einheit
 *
 * @author Tharsan Arumuganathar
 */
public record MaterialDaten(String artikelnummer, String bezeichnung, String kategorie, Einheit einheit,
        BigDecimal einkaufspreis, BigDecimal verkaufspreis) {
}
