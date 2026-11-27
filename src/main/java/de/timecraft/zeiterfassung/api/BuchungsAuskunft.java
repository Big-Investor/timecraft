// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface BuchungsAuskunft {
    Optional<BuchungInfo> buchung(long zeiteintragId);
    Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId);
    List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId);
    /** Buchungen, die das Intervall [von, bis) berühren (laufende und abgeschlossene). */
    List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis);
    /** Nur abgeschlossene Buchungen, je Kalendertag (Europe/Berlin) zerlegt, Monatsgrenzen eingehalten. */
    List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat);
    List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat);
    List<AenderungInfo> historie(long zeiteintragId);
}
