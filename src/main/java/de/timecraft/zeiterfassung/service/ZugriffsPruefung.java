package de.timecraft.zeiterfassung.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;

/**
 * Rechte in M2 (FA-M2-10, FA-M2-17, QA-A-01): Jeder liest seine eigenen Daten. Die Projektleitung
 * liest zusaetzlich die Daten der Mitarbeiter ihrer verantworteten Projekte (aktiv und archiviert).
 * Aendern darf jeder nur seine eigenen Zeiteintraege, dafuer gibt es keine Ausnahme. Admins fuehren keine
 * Zeiten und kein Zeitkonto (Anhang A, Entscheidung Noah 27.11.2026).
 *
 * @author Noah Cipa
 */
@Component
public class ZugriffsPruefung {

    private final ProjektAuskunft projektAuskunft;

    public ZugriffsPruefung(ProjektAuskunft projektAuskunft) {
        this.projektAuskunft = projektAuskunft;
    }

    public static void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }

    /** Zeiten und Zeitkonten gibt es nur fuer Mitarbeiter und Projektleitungen. */
    public void pruefeZeiterfassung(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle == Rolle.ADMIN) {
            throw new ZugriffVerweigertException(
                    "Zeiten und Zeitkonten gibt es nur für Mitarbeiter und Projektleitungen.");
        }
    }

    public void pruefeLesen(long angemeldeteId, Rolle rolle, long mitarbeiterId) {
        pruefeZeiterfassung(rolle);
        if (!darfLesen(angemeldeteId, rolle, mitarbeiterId)) {
            throw new ZugriffVerweigertException("Sie dürfen nur Ihre eigenen Zeiten und die der Mitarbeiter "
                    + "Ihrer Projekte ansehen.");
        }
    }

    private boolean darfLesen(long angemeldeteId, Rolle rolle, long mitarbeiterId) {
        return angemeldeteId == mitarbeiterId
                || projektMitarbeiter(angemeldeteId, rolle).contains(mitarbeiterId);
    }

    /** Mitarbeiter der verantworteten Projekte. Leer, wenn die Rolle nicht Projektleitung ist. */
    Set<Long> projektMitarbeiter(long angemeldeteId, Rolle rolle) {
        Set<Long> mitarbeiterIds = new LinkedHashSet<>();
        if (rolle != Rolle.PROJEKTLEITUNG) {
            return mitarbeiterIds;
        }
        for (ProjektInfo projekt : projektAuskunft.verantworteteProjekte(angemeldeteId)) {
            mitarbeiterIds.addAll(projektAuskunft.zugeordneteMitarbeiter(projekt.projektId()));
        }
        return mitarbeiterIds;
    }
}
