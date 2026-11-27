// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

/**
 * M2 prüft alle Integritätsregeln (Ende nach Beginn, nicht in der Zukunft, keine Überschneidung,
 * Buchung nicht laufend) und schreibt Buchung + Protokoll in EINER Transaktion.
 * Bei Fehlern: Fachausnahme mit verständlicher deutscher Meldung, nichts wird gespeichert.
 * Rechte und Projektregeln (wer darf ändern, Zielprojekt aktiv/zugeordnet) prüft der Aufrufer.
 */
public interface BuchungsAenderung {
    void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
                 AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung);

    /** Gibt die ID der neuen Buchung zurück. */
    long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
                    long geaendertVonMitarbeiterId, String begruendung);
}
