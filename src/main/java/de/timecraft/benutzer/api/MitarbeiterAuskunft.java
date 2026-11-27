// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface MitarbeiterAuskunft {
    /** Angemeldete Person (aus der Sitzung). Wirft eine Fachausnahme, wenn niemand angemeldet ist. */
    MitarbeiterInfo angemeldetePerson();
    /** Auch deaktivierte Mitarbeiter. */
    Optional<MitarbeiterInfo> finde(long mitarbeiterId);
    List<MitarbeiterInfo> aktive();
    /** Inklusive ehemaliger Mitarbeiter. */
    List<MitarbeiterInfo> alle();
    Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr);
    /** Vertraulich (nur Projektleitung/Admin dürfen es sehen) – für M4. */
    Optional<BigDecimal> stundensatz(long mitarbeiterId);
}
