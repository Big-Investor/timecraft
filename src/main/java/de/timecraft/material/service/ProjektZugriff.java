package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Liest Projekte ueber die api von M3 und prueft die projektbezogenen Rechte der M4-Services.
 * {@code @Lazy}: Im M4-Branch gibt es M3 noch nicht; der Kontext startet trotzdem (wie bei M2, M3, M6).
 *
 * @author Tharsan Arumuganathar
 */
@Component
class ProjektZugriff {

    private final ProjektAuskunft projektAuskunft;

    /**
     * @param projektAuskunft Projekte aus M3
     */
    ProjektZugriff(@Lazy ProjektAuskunft projektAuskunft) {
        this.projektAuskunft = projektAuskunft;
    }

    /**
     * Name fuer die Anzeige; ein inzwischen unbekanntes Projekt wird mit seiner ID genannt.
     *
     * @param projektId Projekt
     * @return Projektname
     */
    String projektname(long projektId) {
        return projektAuskunft.finde(projektId).map(ProjektInfo::name).orElse("Projekt " + projektId);
    }

    private ProjektInfo projekt(long projektId) {
        return projektAuskunft.finde(projektId)
                .orElseThrow(() -> new NichtGefundenException("Das Projekt wurde nicht gefunden."));
    }

    /**
     * Planen und Entscheiden darf nur die verantwortliche Projektleitung (Admin nicht).
     *
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person, null heisst nicht angemeldet
     * @param projektId Projekt
     * @return das Projekt
     */
    ProjektInfo pruefeVerantwortlich(long mitarbeiterId, Rolle rolle, long projektId) {
        Rechte.pruefeAngemeldet(rolle);
        ProjektInfo projekt = projekt(projektId);
        if (!istVerantwortlich(mitarbeiterId, rolle, projekt)) {
            throw new ZugriffVerweigertException("Das darf nur die verantwortliche Projektleitung dieses Projekts.");
        }
        return projekt;
    }

    /**
     * Kosten lesen duerfen die verantwortliche Projektleitung und der Admin.
     *
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person, null heisst nicht angemeldet
     * @param projektId Projekt
     * @return das Projekt
     */
    ProjektInfo pruefeKostensicht(long mitarbeiterId, Rolle rolle, long projektId) {
        Rechte.pruefePreissicht(rolle);
        ProjektInfo projekt = projekt(projektId);
        if (rolle == Rolle.PROJEKTLEITUNG && !istVerantwortlich(mitarbeiterId, rolle, projekt)) {
            throw new ZugriffVerweigertException("Sie sind nicht die verantwortliche Projektleitung dieses Projekts.");
        }
        return projekt;
    }

    /**
     * Mehrbedarf beantragen und geplante Materialien (ohne Preise) sehen duerfen alle Personen,
     * die dem Projekt zugeordnet sind, und die verantwortliche Projektleitung. Der Admin nicht.
     *
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person, null heisst nicht angemeldet
     * @param projektId Projekt
     * @return das Projekt
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

    /**
     * Projekte mit Kostensicht: die verantworteten der Projektleitung, beim Admin alle.
     *
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person (Projektleitung oder Admin)
     * @return verantwortete Projekte der Projektleitung, beim Admin alle
     */
    List<ProjektInfo> projekteMitKostensicht(long mitarbeiterId, Rolle rolle) {
        Rechte.pruefePreissicht(rolle);
        return rolle == Rolle.ADMIN ? projektAuskunft.alle() : projektAuskunft.verantworteteProjekte(mitarbeiterId);
    }

    /**
     * Aktive Projekte, fuer die die Person Mehrbedarf beantragen darf (zugeordnet oder verantwortlich).
     *
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person (nicht Admin)
     * @return aktive Projekte, denen die Person zugeordnet ist oder die sie verantwortet
     */
    List<ProjektInfo> projekteFuerMehrbedarf(long mitarbeiterId, Rolle rolle) {
        Rechte.pruefeAngemeldet(rolle);
        if (rolle == Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Administratoren stellen keinen Mehrbedarf.");
        }
        Map<Long, ProjektInfo> projekte = new LinkedHashMap<>();
        projektAuskunft.aktiveProjekteVon(mitarbeiterId).forEach(p -> projekte.put(p.projektId(), p));
        if (rolle == Rolle.PROJEKTLEITUNG) {
            projektAuskunft.verantworteteProjekte(mitarbeiterId).stream().filter(ProjektInfo::aktiv)
                    .forEach(p -> projekte.putIfAbsent(p.projektId(), p));
        }
        return List.copyOf(projekte.values());
    }

    /**
     * @param mitarbeiterId angemeldete Person
     * @param rolle Rolle der angemeldeten Person
     * @param projekt Projekt
     * @return true, wenn die Person die verantwortliche Projektleitung ist
     */
    static boolean istVerantwortlich(long mitarbeiterId, Rolle rolle, ProjektInfo projekt) {
        return rolle == Rolle.PROJEKTLEITUNG && projekt.projektleitungId() == mitarbeiterId;
    }

    /**
     * @param projekt Projekt
     * @throws de.timecraft.geruest.common.FachlicheException wenn das Projekt abgeschlossen ist
     */
    static void pruefeAktiv(ProjektInfo projekt) {
        if (!projekt.aktiv()) {
            throw new FachlicheException("Das Projekt ist abgeschlossen und kann nicht mehr geändert werden.");
        }
    }
}
