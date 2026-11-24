package de.timecraft.material.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gesamtauftragskosten eines Projekts (FA-M4-07, FA-M4-08); keine Entity, wird bei jedem Aufruf berechnet.
 *
 * @param materialkosten Summe EK der eingeplanten Materialien
 * @param personalkosten Summe der Arbeitszeiten x Stundensatz
 * @param personalMinuten gebuchte Minuten
 * @param gesamtkosten Material plus Personal
 * @param angebotspreis Angebotspreis (netto), leer wenn er fehlt
 * @param differenz Angebotspreis minus Gesamtkosten; leer, wenn der Angebotspreis fehlt
 * @param kostendeckung gedeckt, Unterdeckung oder Angebotspreis fehlt
 * @param ohneStundensatz Namen der Personen mit Buchungen, aber ohne Stundensatz (mit 0 € gerechnet)
 *
 * @author Tharsan Arumuganathar
 */
public record Kostenuebersicht(BigDecimal materialkosten, BigDecimal personalkosten, int personalMinuten,
        BigDecimal gesamtkosten, BigDecimal angebotspreis, BigDecimal differenz, Kostendeckung kostendeckung,
        List<String> ohneStundensatz) {
}
