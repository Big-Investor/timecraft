package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Liest Projekte ueber die api von M3 und prueft die projektbezogenen Rechte der M4-Services.
 * {@code @Lazy}: Im M4-Branch gibt es M3 noch nicht; der Kontext startet trotzdem (wie bei M2, M3, M6).
 */
@Component
class ProjektZugriff {

    private final ProjektAuskunft projektAuskunft;

    ProjektZugriff(@Lazy ProjektAuskunft projektAuskunft) {
        this.projektAuskunft = projektAuskunft;
    }

    private ProjektInfo projekt(long projektId) {
        return projektAuskunft.finde(projektId)
                .orElseThrow(() -> new NichtGefundenException("Das Projekt wurde nicht gefunden."));
    }

    /** Planen und Entscheiden darf nur die verantwortliche Projektleitung (Admin nicht). */
    ProjektInfo pruefeVerantwortlich(long mitarbeiterId, Rolle rolle, long projektId) {
        Rechte.pruefeAngemeldet(rolle);
        ProjektInfo projekt = projekt(projektId);
        if (!istVerantwortlich(mitarbeiterId, rolle, projekt)) {
            throw new ZugriffVerweigertException("Das darf nur die verantwortliche Projektleitung dieses Projekts.");
        }
        return projekt;
    }

    /** Kosten lesen duerfen die verantwortliche Projektleitung und der Admin. */
    ProjektInfo pruefeKostensicht(long mitarbeiterId, Rolle rolle, long projektId) {
        Rechte.pruefePreissicht(rolle);
        ProjektInfo projekt = projekt(projektId);
        if (rolle == Rolle.PROJEKTLEITUNG && projekt.projektleitungId() != mitarbeiterId) {
            throw new ZugriffVerweigertException("Sie sind nicht die verantwortliche Projektleitung dieses Projekts.");
        }
        return projekt;
    }

    /**
     * Mehrbedarf beantragen und die geplanten Materialien (ohne Preise) sehen duerfen alle Personen,
     * die dem Projekt zugeordnet sind, und die verantwortliche Projektleitung. Der Admin nicht.
     */
    ProjektInfo pruefeTeilnahme(long mitarbeiterId, Rolle rolle, long projektId) {
        Rechte.pruefeAngemeldet(rolle);
        ProjektInfo projekt = projekt(projektId);
        if (rolle == Rolle.ADMIN || !(istVerantwortlich(mitarbeiterId, rolle, projekt)
                || projektAuskunft.istZugeordnet(mitarbeiterId, projektId))) {
            throw new ZugriffVerweigertException("Sie sind diesem Projekt nicht zugeordnet.");
        }
        return projekt;
    }

    static boolean istVerantwortlich(long mitarbeiterId, Rolle rolle, ProjektInfo projekt) {
        return rolle == Rolle.PROJEKTLEITUNG && projekt.projektleitungId() == mitarbeiterId;
    }

    static void pruefeAktiv(ProjektInfo projekt) {
        if (!projekt.aktiv()) {
            throw new FachlicheException("Das Projekt ist abgeschlossen. Das Material kann nicht mehr geändert werden.");
        }
    }
}
