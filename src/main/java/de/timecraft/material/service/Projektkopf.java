package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektInfo;

/**
 * Kopfdaten eines Projekts fuer die Materialplanung (Projektleitung und Admin).
 *
 * @param verantwortlich die angemeldete Person ist die verantwortliche Projektleitung (darf entscheiden)
 * @param darfPlanen verantwortlich und Projekt aktiv
 */
public record Projektkopf(long projektId, String name, boolean aktiv, boolean verantwortlich, boolean darfPlanen) {

    static Projektkopf aus(ProjektInfo projekt, long mitarbeiterId, Rolle rolle) {
        boolean verantwortlich = ProjektZugriff.istVerantwortlich(mitarbeiterId, rolle, projekt);
        return new Projektkopf(projekt.projektId(), projekt.name(), projekt.aktiv(), verantwortlich,
                verantwortlich && projekt.aktiv());
    }
}
