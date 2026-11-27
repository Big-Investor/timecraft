package de.timecraft.zeiterfassung.view;

import de.timecraft.projekte.api.ProjektInfo;

/**
 * Projekt mit Projekt-ID und Name wie in den Mockups (FA-M2-01).
 *
 * @author Noah Cipa
 */
public final class ProjektText {

    private ProjektText() {
        // Nur statische Methoden
    }

    public static String id(long projektId) {
        return "P-" + projektId;
    }

    /** Projekt in Listen, z. B. "P-1024 Einbauküche Wagner"; ohne bekannten Namen nur die ID. */
    public static String listenText(long projektId, String name) {
        return name == null ? id(projektId) : id(projektId) + " " + name;
    }

    /** Text in der Projektauswahl, die leere Auswahl heisst "ohne Projekt". */
    public static String auswahlText(ProjektInfo projekt) {
        if (projekt == null) {
            return "ohne Projekt";
        }
        return id(projekt.projektId()) + " · " + projekt.name();
    }
}
