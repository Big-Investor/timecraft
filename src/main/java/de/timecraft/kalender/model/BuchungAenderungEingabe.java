package de.timecraft.kalender.model;

import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDateTime;

/**
 * Neue Werte einer Buchung aus dem Aenderungsformular (FA-M6-08). Beginn und Ende sind Ortszeiten
 * (Europe/Berlin), wie die Person sie eingibt. projektId == null heisst „ohne Projekt“.
 */
public record BuchungAenderungEingabe(
        LocalDateTime beginn, LocalDateTime ende, Standort standort, Long projektId, String begruendung) { }
