package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Qualifikation;
import java.time.LocalDate;

/**
 * Eine Zeile der Mitarbeiterliste (US-M1-14, Mockup „Mitarbeiterliste“):
 * Personalnummer, Name, Qualifikation, Rolle, Vorgesetzte/r und Status.
 * Wird auch für die Auswahlliste der Vorgesetzten benutzt.
 *
 * @param vorgesetzterName leer ({@code null}), wenn es keinen Vorgesetzten gibt
 * @param austrittsdatum   nur bei inaktiven Mitarbeitern gesetzt
 * @author Leon-Selim Kannenberg
 */
public record MitarbeiterZeile(long mitarbeiterId, String personalnummer, String name,
        Qualifikation qualifikation, Rolle rolle, String vorgesetzterName, boolean aktiv,
        LocalDate austrittsdatum) {
}
