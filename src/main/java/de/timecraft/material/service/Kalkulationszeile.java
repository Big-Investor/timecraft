package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;

/**
 * Eine Zeile der Vorkalkulation: Kosten (EK), Verkaufswert (VK) und Deckungsbeitrag einer Materialposition.
 *
 * @param positionId Materialposition
 * @param artikelnummer Artikelnummer
 * @param bezeichnung Bezeichnung
 * @param einheit Einheit
 * @param sollMenge geplante Menge
 * @param einkaufspreis EK je Einheit
 * @param verkaufspreis VK je Einheit
 * @param kosten Menge x EK
 * @param verkauf Menge x VK
 * @param deckungsbeitrag Verkauf minus Kosten
 *
 * @author Tharsan Arumuganathar
 */
public record Kalkulationszeile(long positionId, String artikelnummer, String bezeichnung, Einheit einheit,
        BigDecimal sollMenge, BigDecimal einkaufspreis, BigDecimal verkaufspreis,
        BigDecimal kosten, BigDecimal verkauf, BigDecimal deckungsbeitrag) {

    /**
     * @param position eingeplante Position
     * @return Zeile mit Kosten, Verkaufswert und Deckungsbeitrag, je kaufmaennisch gerundet
     */
    static Kalkulationszeile aus(Materialposition position) {
        BigDecimal menge = position.getSollMenge();
        BigDecimal ek = position.getMaterial().getEinkaufspreis();
        BigDecimal vk = position.getMaterial().getVerkaufspreis();
        BigDecimal kosten = Betraege.runden(menge.multiply(ek));
        BigDecimal verkauf = Betraege.runden(menge.multiply(vk));
        return new Kalkulationszeile(position.getId(), position.getMaterial().getArtikelnummer(),
                position.getMaterial().getBezeichnung(), position.getMaterial().getEinheit(),
                menge, ek, vk, kosten, verkauf, verkauf.subtract(kosten));
    }
}
