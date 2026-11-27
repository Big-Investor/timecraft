// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.LocalDate;
import java.util.Optional;

/** Wird von M5 innerhalb dessen Transaktion aufgerufen (Genehmigung muss atomar sein). */
public interface AbwesenheitsUebernahme {
    /** Urlaubstage im Zeitraum ohne Feiertage und arbeitsfreie Tage laut Arbeitszeitmodell. */
    int urlaubstage(long mitarbeiterId, LocalDate von, LocalDate bis);
    boolean ueberschneidetAbwesenheit(long mitarbeiterId, LocalDate von, LocalDate bis);
    /** Legt die genehmigte Abwesenheit an und gibt deren ID zurück. */
    long uebernehmen(long antragId, long mitarbeiterId, AbwesenheitsArt art, LocalDate von, LocalDate bis);
    void aufheben(long abwesenheitId);
    Optional<Long> abwesenheitZuAntrag(long antragId);
}
