package de.timecraft.kalender.service;

import de.timecraft.geruest.common.FachlicheException;

/**
 * Eine Eingabe im Aenderungsformular ist ungueltig (UC-M6-05 4a, 4c). Nennt das Feld,
 * damit die Oberflaeche die Meldung am Feld zeigen kann. Es wird nichts gespeichert.
 */
public class UngueltigeAenderungException extends FachlicheException {

    /** Die Felder des Aenderungsformulars. */
    public enum Feld { BEGINN, ENDE, STANDORT, PROJEKT, BEGRUENDUNG }

    private final Feld feld;

    public UngueltigeAenderungException(Feld feld, String meldung) {
        super(meldung);
        this.feld = feld;
    }

    public Feld feld() {
        return feld;
    }
}
