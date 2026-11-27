package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;

/**
 * Prüft die Rolle der angemeldeten Person (QA-A-01). Die Services rufen diese Methoden am Anfang
 * jeder Funktion auf, damit die Rechte auch ohne Oberfläche gelten.
 * Eine Rolle {@code null} heißt: niemand ist angemeldet.
 *
 * @author Leon-Selim Kannenberg
 */
final class Rechte {

    static final String NICHT_ANGEMELDET = "Bitte melden Sie sich an.";
    static final String NUR_ADMIN = "Diese Funktion ist nur für die Verwaltung (Admin) freigegeben.";
    static final String NUR_LEITUNG_UND_ADMIN =
            "Diese Funktion ist nur für Projektleitung und Verwaltung (Admin) freigegeben.";
    static final String NUR_ROLLE_MITARBEITER =
            "Die Projektleitung darf nur Personen mit der Rolle „Mitarbeiter“ bearbeiten.";

    private Rechte() {
    }

    /** Nur der Admin darf weiter. */
    static void nurAdmin(Rolle rolle) {
        angemeldet(rolle);
        if (rolle != Rolle.ADMIN) {
            throw new ZugriffVerweigertException(NUR_ADMIN);
        }
    }

    /** Projektleitung und Admin dürfen weiter, ein Mitarbeiter nicht. */
    static void projektleitungOderAdmin(Rolle rolle) {
        angemeldet(rolle);
        if (rolle == Rolle.MITARBEITER) {
            throw new ZugriffVerweigertException(NUR_LEITUNG_UND_ADMIN);
        }
    }

    /**
     * Darf die angemeldete Person diesen Mitarbeiter bearbeiten? Der Admin darf alle. Die Projektleitung
     * darf nur Personen mit der Rolle Mitarbeiter bearbeiten, also weder den Admin noch andere
     * Projektleitungen noch sich selbst.
     */
    static boolean darfBearbeiten(Rolle angemeldeteRolle, Rolle rolleDesMitarbeiters) {
        if (angemeldeteRolle == Rolle.ADMIN) {
            return true;
        }
        return angemeldeteRolle == Rolle.PROJEKTLEITUNG && rolleDesMitarbeiters == Rolle.MITARBEITER;
    }

    private static void angemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException(NICHT_ANGEMELDET);
        }
    }
}
