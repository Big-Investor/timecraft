package de.timecraft.geruest.common;

/**
 * Ungueltige Eingabe oder verletzte Fachregel. Die Meldung ist ein deutscher Text, den die Person lesen kann.
 */
public class FachlicheException extends RuntimeException {

    public FachlicheException(String meldung) {
        super(meldung);
    }
}
