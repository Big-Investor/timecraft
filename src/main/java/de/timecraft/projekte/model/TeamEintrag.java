package de.timecraft.projekte.model;

import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;

/**
 * Eine Zeile der Team-Übersicht: ein Mitarbeiter, der dem Projekt zugeordnet ist.
 * <p>
 * Datensparsamkeit: Die Felder eingestempelt, standort und beginn sind nur gefüllt, wenn die Person die
 * Übersicht als verantwortliche Projektleitung öffnet. Zugeordnete Mitarbeiter bekommen dort null,
 * sie sehen nur den Namen ihres Teams. Arbeitszeiten, Salden und andere Projekte gibt es hier nie.
 *
 * @param mitarbeiterId die ID der Person (nötig, damit die Projektleitung die Zuordnung entfernen kann)
 * @param name          Vor- und Nachname
 * @param eingestempelt true = hat eine laufende Buchung auf dieses Projekt, false = nicht, null = darf die Rolle nicht sehen
 * @param standort      Standort der laufenden Buchung; null, wenn nicht eingestempelt oder nicht sichtbar
 * @param beginn        Beginn der laufenden Buchung; null, wenn nicht eingestempelt oder nicht sichtbar
 */
public record TeamEintrag(long mitarbeiterId, String name, Boolean eingestempelt, Standort standort, Instant beginn) { }
