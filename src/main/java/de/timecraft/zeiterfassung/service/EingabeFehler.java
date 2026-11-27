package de.timecraft.zeiterfassung.service;

import java.util.Set;

import de.timecraft.geruest.common.FachlicheException;

/**
 * Ungueltige Eingabe, die zu bestimmten Feldern gehoert: Die Oberflaeche markiert diese Felder (QA-A-02).
 *
 * @author Noah Cipa
 */
public class EingabeFehler extends FachlicheException {

    public enum Feld { DATUM, BEGINN, ENDE, PAUSE_VON, PAUSE_BIS, STANDORT, PROJEKT, VON, BIS, NAME }

    private final Set<Feld> felder;

    EingabeFehler(String meldung, Set<Feld> felder) {
        super(meldung);
        this.felder = Set.copyOf(felder);
    }

    EingabeFehler(String meldung, Feld... felder) {
        this(meldung, Set.of(felder));
    }

    public Set<Feld> felder() {
        return felder;
    }
}
