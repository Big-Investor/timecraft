package de.timecraft.material.service;

import de.timecraft.geruest.common.FachlicheException;
import java.math.BigDecimal;

/**
 * Prueft Mengeneingaben: groesser als 0, kaufmaennisch auf 2 Stellen gerundet, passend in NUMERIC(10,2).
 *
 * @author Tharsan Arumuganathar
 */
final class Mengen {

    private Mengen() {
    }

    /**
     * @param menge eingegebene Menge
     * @param feld Feldname fuer die Meldung, z. B. "die Soll-Menge"
     * @return auf zwei Stellen gerundete Menge
     * @throws de.timecraft.geruest.common.FachlicheException wenn leer, nicht groesser als 0 oder zu gross
     */
    static BigDecimal pruefe(BigDecimal menge, String feld) {
        if (menge == null) {
            throw new FachlicheException("Bitte geben Sie " + feld + " ein.");
        }
        BigDecimal gerundet = Betraege.runden(menge);
        if (gerundet.signum() <= 0) {
            throw new FachlicheException("Bitte geben Sie " + feld + " größer als 0 ein.");
        }
        if (gerundet.compareTo(Betraege.HOECHSTWERT) > 0) {
            throw new FachlicheException("Bitte geben Sie " + feld + " kleiner als 100.000.000 ein.");
        }
        return gerundet;
    }
}
