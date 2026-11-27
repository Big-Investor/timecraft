package de.timecraft.zeiterfassung.view;

import de.timecraft.zeiterfassung.api.Standort;

/**
 * Deutsche Bezeichnung eines Standorts fuer die Oberflaeche.
 *
 * @author Noah Cipa
 */
public final class StandortText {

    private StandortText() {
        // Nur statische Methoden
    }

    public static String text(Standort standort) {
        return switch (standort) {
            case BUERO -> "Büro";
            case HOMEOFFICE -> "Homeoffice";
            case UNTERWEGS -> "unterwegs";
        };
    }
}
