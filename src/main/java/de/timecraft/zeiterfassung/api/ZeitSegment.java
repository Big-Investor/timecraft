// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;
import java.time.LocalDate;

/** Anrechenbarer Zeitanteil einer abgeschlossenen Buchung an einem Kalendertag (Europe/Berlin), Pausenabzug schon durch M2. */
public record ZeitSegment(
        long zeiteintragId, long mitarbeiterId, Long projektId, Standort standort,
        LocalDate tag, Instant beginn, Instant ende, int anrechenbareMinuten) { }
