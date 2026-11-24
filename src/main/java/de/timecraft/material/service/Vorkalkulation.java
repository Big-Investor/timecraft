package de.timecraft.material.service;

import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;
import java.util.List;

/**
 * Vorkalkulation eines Projekts (FA-M4-03). Jede Zeile wird kaufmaennisch gerundet,
 * die Summen addieren die gerundeten Zeilen (so stimmen Tabelle und Summe immer ueberein).
 *
 * @param zeilen eine Zeile je Position
 * @param summeKosten Summe EK
 * @param summeVerkauf Summe VK
 * @param summeDeckungsbeitrag Summe VK minus Summe EK
 *
 * @author Tharsan Arumuganathar
 */
public record Vorkalkulation(List<Kalkulationszeile> zeilen, BigDecimal summeKosten, BigDecimal summeVerkauf,
        BigDecimal summeDeckungsbeitrag) {

    /**
     * @param positionen Positionen eines Projekts
     * @return Zeilen und Summen der Vorkalkulation
     */
    static Vorkalkulation berechne(List<Materialposition> positionen) {
        List<Kalkulationszeile> zeilen = positionen.stream().map(Kalkulationszeile::aus).toList();
        BigDecimal kosten = summe(zeilen.stream().map(Kalkulationszeile::kosten).toList());
        BigDecimal verkauf = summe(zeilen.stream().map(Kalkulationszeile::verkauf).toList());
        return new Vorkalkulation(zeilen, kosten, verkauf, verkauf.subtract(kosten));
    }

    /** Runden aendert gerundete Zeilen nicht, sorgt aber fuer "0.00" bei leerer Liste. */
    private static BigDecimal summe(List<BigDecimal> werte) {
        return Betraege.runden(werte.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
