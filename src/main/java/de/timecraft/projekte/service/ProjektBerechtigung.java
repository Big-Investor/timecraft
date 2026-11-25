package de.timecraft.projekte.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.model.Projekt;
import org.springframework.stereotype.Component;

/**
 * Die Rechteregeln von Modul M3 an einer Stelle gesammelt.
 * <p>
 * Jede Methode prüft eine Regel und wirft bei einem Verstoß eine Ausnahme mit einer deutschen
 * Meldung. Hält die Person die Regel ein, passiert nichts und der Service macht weiter.
 * <p>
 * Die Person kommt als Parameter (Mitarbeiter-ID und Rolle), so wie im Gerüst vorgegeben.
 * Eine Rolle null bedeutet: Es ist niemand angemeldet.
 *
 * @author Tim Stukenbröker
 */
@Component
public class ProjektBerechtigung {

    /** Ohne Anmeldung darf niemand etwas. */
    public void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }

    /** Nur der Admin darf Projekte anlegen, ändern (Name, Termine, Projektleitung), archivieren und reaktivieren. */
    public void pruefeAdmin(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle != Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Nur Administratoren dürfen diese Aktion ausführen.");
        }
    }

    /**
     * Ist die Person die verantwortliche Projektleitung genau dieses Projekts?
     * Dafür müssen zwei Dinge stimmen: Sie hat die Rolle PROJEKTLEITUNG (ein Admin ist nie
     * verantwortliche Projektleitung) und sie steht im Projekt als Projektleitung eingetragen.
     * Eine Projektleitung eines anderen Projekts ist also genauso ausgeschlossen wie ein Mitarbeiter.
     */
    public boolean istVerantwortlicheProjektleitung(long mitarbeiterId, Rolle rolle, Projekt projekt) {
        return rolle == Rolle.PROJEKTLEITUNG && projekt.getProjektleitungId() == mitarbeiterId;
    }

    /** Verlangt die verantwortliche Projektleitung dieses Projekts, siehe {@link #istVerantwortlicheProjektleitung}. */
    public void pruefeVerantwortlicheProjektleitung(long mitarbeiterId, Rolle rolle, Projekt projekt) {
        pruefeAngemeldet(rolle);
        if (!istVerantwortlicheProjektleitung(mitarbeiterId, rolle, projekt)) {
            throw new ZugriffVerweigertException("Nur die verantwortliche Projektleitung dieses Projekts darf diese Aktion ausführen.");
        }
    }
}
