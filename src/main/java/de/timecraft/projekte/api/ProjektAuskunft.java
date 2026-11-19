// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface ProjektAuskunft {
    Optional<ProjektInfo> finde(long projektId);
    /** Aktive Projekte, denen der Mitarbeiter zugeordnet ist (Auswahl beim Einstempeln). */
    List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId);
    /** Alle Projekte (aktiv und archiviert), die diese Projektleitung verantwortet. */
    List<ProjektInfo> verantworteteProjekte(long projektleitungId);
    List<ProjektInfo> alle();
    List<Long> zugeordneteMitarbeiter(long projektId);
    boolean istZugeordnet(long mitarbeiterId, long projektId);
}
