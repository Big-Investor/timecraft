package de.timecraft.geruest.common;

/**
 * Die Rolle der angemeldeten Person reicht fuer die Aktion nicht aus.
 */
public class ZugriffVerweigertException extends RuntimeException {

    public ZugriffVerweigertException(String meldung) {
        super(meldung);
    }
}
