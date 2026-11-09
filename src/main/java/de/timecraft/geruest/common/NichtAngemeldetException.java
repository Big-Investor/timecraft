package de.timecraft.geruest.common;

/**
 * Es ist niemand angemeldet.
 */
public class NichtAngemeldetException extends RuntimeException {

    public NichtAngemeldetException(String meldung) {
        super(meldung);
    }
}
