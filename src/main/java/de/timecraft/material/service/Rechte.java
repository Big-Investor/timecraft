package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;

/**
 * Gemeinsame Rollenpruefungen der M4-Services. Eine Rolle null heisst: niemand angemeldet.
 *
 * @author Tharsan Arumuganathar
 */
final class Rechte {

    private Rechte() {
    }

    /**
     * @param rolle Rolle der angemeldeten Person
     * @throws de.timecraft.geruest.common.NichtAngemeldetException wenn rolle null ist
     */
    static void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
    }

    /**
     * Den Materialstamm pflegt nur der Admin.
     *
     * @param rolle Rolle der angemeldeten Person
     */
    static void pruefeStammpflege(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle != Rolle.ADMIN) {
            throw new ZugriffVerweigertException("Nur Administratoren dürfen den Materialstamm pflegen.");
        }
    }

    /**
     * Preise, Kosten und Margen sehen nur Projektleitung und Admin (QA-M4-02).
     *
     * @param rolle Rolle der angemeldeten Person
     */
    static void pruefePreissicht(Rolle rolle) {
        pruefeAngemeldet(rolle);
        if (rolle == Rolle.MITARBEITER) {
            throw new ZugriffVerweigertException("Preise und Kosten sind nur für Projektleitung und Admin sichtbar.");
        }
    }
}
