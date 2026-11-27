package de.timecraft.zeiterfassung.view;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;

/**
 * Deutsche Bezeichnung der Abwesenheitsart. Ist die Art nicht sichtbar (null), heisst es "abwesend".
 *
 * @author Noah Cipa
 */
public final class AbwesenheitsArtText {

    private AbwesenheitsArtText() {
        // Nur statische Methoden
    }

    public static String text(AbwesenheitsArt art) {
        if (art == null) {
            return "abwesend";
        }
        return switch (art) {
            case URLAUB -> "Urlaub";
            case KRANKHEIT -> "Krankheit";
            case SONSTIGE -> "Sonstige Abwesenheit";
        };
    }
}
