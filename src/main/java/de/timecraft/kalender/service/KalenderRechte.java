package de.timecraft.kalender.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gemeinsame Rechteregeln von KalenderService und BuchungAenderungService (FA-M6-07, 08, 11):
 * Welche Personen und welche Projekte darf eine Projektleitung im Kalender sehen und aendern?
 * Keine Spring-Bean: Die Services legen sie mit ihren Auskuenften selbst an.
 *
 * @author Bennet Antonio Brinkmeier
 */
class KalenderRechte {

    private final ProjektAuskunft projektAuskunft;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;

    KalenderRechte(ProjektAuskunft projektAuskunft, MitarbeiterAuskunft mitarbeiterAuskunft) {
        this.projektAuskunft = projektAuskunft;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
    }

    static void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }

    /** Aktive Mitarbeiter, die mindestens einem aktiven verantworteten Projekt zugeordnet sind. */
    List<MitarbeiterInfo> teamDerProjektleitung(long projektleitungId) {
        Set<Long> ids = new HashSet<>();
        for (ProjektInfo projekt : aktiveVerantworteteProjekte(projektleitungId)) {
            ids.addAll(projektAuskunft.zugeordneteMitarbeiter(projekt.projektId()));
        }
        List<MitarbeiterInfo> team = new ArrayList<>();
        for (long id : ids) {
            mitarbeiterAuskunft.finde(id).filter(MitarbeiterInfo::aktiv).ifPresent(team::add);
        }
        return team;
    }

    /**
     * Projekte, deren Buchungen die Projektleitung im Kalender der Person sieht und aendern darf:
     * ihre aktiven Projekte, denen die Person noch zugeordnet ist. Leer, wenn die Person nicht
     * (mehr) waehlbar ist (deaktiviert, entfernt, fremdes Team, unbekannt).
     */
    Set<Long> sichtbareProjekteFuerProjektleitung(long projektleitungId, long personId) {
        boolean personAktiv = mitarbeiterAuskunft.finde(personId).map(MitarbeiterInfo::aktiv).orElse(false);
        Set<Long> projekte = new HashSet<>();
        if (!personAktiv) {
            return projekte;
        }
        for (ProjektInfo projekt : aktiveVerantworteteProjekte(projektleitungId)) {
            if (projektAuskunft.istZugeordnet(personId, projekt.projektId())) {
                projekte.add(projekt.projektId());
            }
        }
        return projekte;
    }

    private List<ProjektInfo> aktiveVerantworteteProjekte(long projektleitungId) {
        return projektAuskunft.verantworteteProjekte(projektleitungId).stream().filter(ProjektInfo::aktiv).toList();
    }
}
