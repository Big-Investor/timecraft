package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektInfo;

/**
 * Kopfdaten eines Projekts fuer die Materialplanung (Projektleitung und Admin).
 *
 * @param projektId Projekt
 * @param name Projektname
 * @param aktiv Projekt ist aktiv
 * @param verantwortlich die angemeldete Person ist die verantwortliche Projektleitung (darf entscheiden)
 * @param darfPlanen verantwortlich und Projekt aktiv
 *
 * @author Tharsan Arumuganathar
 */
public record Projektkopf(long projektId, String name, boolean aktiv, boolean verantwortlich, boolean darfPlanen) {

    /**
     * @param projekt Projekt aus M3
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person
     * @return Kopfdaten mit den Rechten der Person
     */
    static Projektkopf aus(ProjektInfo projekt, long mitarbeiterId, Rolle rolle) {
        boolean verantwortlich = ProjektZugriff.istVerantwortlich(mitarbeiterId, rolle, projekt);
        return new Projektkopf(projekt.projektId(), projekt.name(), projekt.aktiv(), verantwortlich,
                verantwortlich && projekt.aktiv());
    }
}
