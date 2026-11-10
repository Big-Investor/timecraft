package de.timecraft.geruest.common;

/**
 * Ein gesuchter Datensatz existiert nicht.
 */
public class NichtGefundenException extends RuntimeException {

    public NichtGefundenException(String meldung) {
        super(meldung);
    }
}
