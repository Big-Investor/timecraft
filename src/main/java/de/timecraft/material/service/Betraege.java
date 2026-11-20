package de.timecraft.material.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Kaufmaennische Rundung auf zwei Nachkommastellen (QA-M4-03). */
final class Betraege {

    /** Groesster Wert, der in NUMERIC(10,2) passt. */
    static final BigDecimal HOECHSTWERT = new BigDecimal("99999999.99");

    private Betraege() {
    }

    static BigDecimal runden(BigDecimal wert) {
        return wert.setScale(2, RoundingMode.HALF_UP);
    }
}
