package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektInfo;

/**
 * Kopfdaten eines Projekts fuer die Materialplanung (Projektleitung und Admin).
 *
 * @param darfPlanen true nur fuer die verantwortliche Projektleitung eines aktiven Projekts
 */
public record Projektkopf(long projektId, String name, boolean aktiv, boolean darfPlanen) {

    static Projektkopf aus(ProjektInfo projekt, long mitarbeiterId, Rolle rolle) {
        boolean planen = projekt.aktiv() && ProjektZugriff.istVerantwortlich(mitarbeiterId, rolle, projekt);
        return new Projektkopf(projekt.projektId(), projekt.name(), projekt.aktiv(), planen);
    }
}
