package de.timecraft.projekte.model;

/**
 * Was eine angemeldete Person im Detailfenster eines Projekts sehen und tun darf. Der ProjektService
 * stellt sie zusammen, damit die Oberfläche keine Rechteregeln kennen muss: Sie zeigt nur an, was hier steht.
 * (Die Rechte werden trotzdem bei jeder Aktion noch einmal im Service geprüft.)
 *
 * @param projekt                das Projekt
 * @param projektleitungName     Name der verantwortlichen Projektleitung, leer wenn die Person nicht bekannt ist
 * @param teamSichtbar           Reiter "Team" zeigen: verantwortliche Projektleitung und zugeordnete Mitarbeiter,
 *                               nur bei einem aktiven Projekt (Admin und andere nie)
 * @param teamVollansicht        nur die verantwortliche Projektleitung: Status, Standort, Beginn, Filter,
 *                               Zuordnen und Entfernen (zugeordnete Mitarbeiter sehen nur die Namen)
 * @param beschreibungAenderbar  die verantwortliche Projektleitung darf die Beschreibung ihres aktiven Projekts ändern
 * @param verwaltbar             Admin: Projekt bearbeiten, archivieren, reaktivieren
 * @param materialplanungSichtbar Verweis auf die Materialplanung (M4) für Projektleitung und Admin, nicht für Mitarbeiter
 *
 * @author Tim Stukenbröker
 */
public record ProjektSicht(
        Projekt projekt, String projektleitungName, boolean teamSichtbar, boolean teamVollansicht,
        boolean beschreibungAenderbar, boolean verwaltbar, boolean materialplanungSichtbar) {
}
