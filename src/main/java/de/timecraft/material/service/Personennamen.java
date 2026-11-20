package de.timecraft.material.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Namen zu Mitarbeiter-IDs ueber die api von M1 ({@code @Lazy}: M1 gibt es im M4-Branch noch nicht). */
@Component
class Personennamen {

    private final MitarbeiterAuskunft mitarbeiterAuskunft;

    Personennamen(@Lazy MitarbeiterAuskunft mitarbeiterAuskunft) {
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
    }

    String name(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::name)
                .orElse("Mitarbeiter " + mitarbeiterId);
    }

    String name(Long mitarbeiterId) {
        return mitarbeiterId == null ? "" : name(mitarbeiterId.longValue());
    }
}
