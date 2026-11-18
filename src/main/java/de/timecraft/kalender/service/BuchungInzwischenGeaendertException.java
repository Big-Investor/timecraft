package de.timecraft.kalender.service;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.BuchungInfo;

/**
 * Die Buchung wurde seit dem Oeffnen des Formulars von jemand anderem geaendert (UC-M6-05 4d).
 * Es wird nichts gespeichert; der aktuelle Stand wird mitgegeben, damit das Formular ihn laden kann.
 */
public class BuchungInzwischenGeaendertException extends FachlicheException {

    private final BuchungInfo aktuellerStand;

    public BuchungInzwischenGeaendertException(BuchungInfo aktuellerStand) {
        super("Buchung wurde inzwischen geändert. Der aktuelle Stand ist geladen, es wurde nichts gespeichert.");
        this.aktuellerStand = aktuellerStand;
    }

    public BuchungInfo aktuellerStand() {
        return aktuellerStand;
    }
}
