package de.timecraft.antraege.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;

/** Rollenpruefungen der M5-Services (QA-A-01). Rolle null heisst: niemand angemeldet. */
final class AntragRechte {

    private AntragRechte() {
    }

    /** Antraege stellen und eigene ansehen duerfen Mitarbeiter und Projektleitung, nicht Admin. */
    static void pruefeAntragsteller(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle == Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Administratoren haben in der Antragsverwaltung keine Aufgaben.");
        }
    }

    /** Antraege pruefen und entscheiden darf nur die Projektleitung. */
    static void pruefeProjektleitung(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle != Rolle.PROJEKTLEITUNG) {
            throw new ZugriffVerweigertException("Nur die Projektleitung darf Anträge prüfen und entscheiden.");
        }
    }

    private static void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }
}
