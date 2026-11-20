package de.timecraft.material.service;

import de.timecraft.geruest.common.FachlicheException;
import java.math.BigDecimal;

/** Prueft Mengeneingaben: groesser als 0, kaufmaennisch auf 2 Stellen gerundet, passend in NUMERIC(10,2). */
final class Mengen {

    private Mengen() {
    }

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
