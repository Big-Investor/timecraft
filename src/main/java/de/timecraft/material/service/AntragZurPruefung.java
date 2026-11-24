package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Offener Mehrbedarf fuer die Projektleitung: Alt-/Neuwert und Kostendifferenz (EK) samt Begruendung (FA-M4-05).
 *
 * @param antragId Antrag
 * @param antragsteller Name der Person, die ihn gestellt hat
 * @param bezeichnung Material
 * @param einheit Einheit
 * @param alteMenge Soll-Menge vorher
 * @param zusatzmenge beantragte Menge
 * @param neueMenge Soll-Menge nach Freigabe
 * @param kostendifferenz Zusatzmenge x EK
 * @param begruendung Begruendung
 * @param eingereichtAm Zeitpunkt des Antrags
 *
 * @author Tharsan Arumuganathar
 */
public record AntragZurPruefung(long antragId, String antragsteller, String bezeichnung, Einheit einheit,
        BigDecimal alteMenge, BigDecimal zusatzmenge, BigDecimal neueMenge, BigDecimal kostendifferenz,
        String begruendung, Instant eingereichtAm) {

    /**
     * @param antrag offener Antrag
     * @param antragsteller Name der Person, die ihn gestellt hat
     * @return Pruefdaten mit Kostendifferenz (Zusatzmenge x EK)
     */
    static AntragZurPruefung aus(Materialanpassungsantrag antrag, String antragsteller) {
        Materialposition position = antrag.getMaterialposition();
        BigDecimal zusatz = antrag.getZusatzmenge();
        return new AntragZurPruefung(antrag.getId(), antragsteller,
                position.getMaterial().getBezeichnung(), position.getMaterial().getEinheit(),
                position.getSollMenge(), zusatz, position.getSollMenge().add(zusatz),
                Betraege.runden(zusatz.multiply(position.getMaterial().getEinkaufspreis())),
                antrag.getBegruendung(), antrag.getEingereichtAm());
    }
}
