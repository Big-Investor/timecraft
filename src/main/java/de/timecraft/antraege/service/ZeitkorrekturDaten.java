package de.timecraft.antraege.service;

import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;

/** Eingaben fuer eine Zeitkorrektur: betroffene Buchung und gewuenschter Stand (Projekt null = ohne Projekt). */
public record ZeitkorrekturDaten(Long zeiteintragId, Instant neuerBeginn, Instant neuesEnde, Standort neuerStandort,
        Long neuesProjektId, String begruendung) {
}
