package de.timecraft.material.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Namen zu Mitarbeiter-IDs ueber die api von M1 ({@code @Lazy}: M1 gibt es im M4-Branch noch nicht).
 *
 * @author Tharsan Arumuganathar
 */
@Component
class Personennamen {

    private final MitarbeiterAuskunft mitarbeiterAuskunft;

    /**
     * @param mitarbeiterAuskunft Mitarbeiterdaten aus M1
     */
    Personennamen(@Lazy MitarbeiterAuskunft mitarbeiterAuskunft) {
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
    }

    /**
     * @param mitarbeiterId Person
     * @return Vor- und Nachname, sonst {@code "Mitarbeiter <id>"}
     */
    String name(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::name)
                .orElse("Mitarbeiter " + mitarbeiterId);
    }

    /**
     * @param mitarbeiterId Person, darf leer sein
     * @return Vor- und Nachname, leer wenn keine ID
     */
    String name(Long mitarbeiterId) {
        return mitarbeiterId == null ? "" : name(mitarbeiterId.longValue());
    }
}
