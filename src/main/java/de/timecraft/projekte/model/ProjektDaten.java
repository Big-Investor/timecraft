package de.timecraft.projekte.model;

import java.time.LocalDate;

/**
 * Die Eingaben für ein Projekt, so wie die Oberfläche sie (später im Formular) liefert.
 * Sie dienen zum Anlegen und zum Ändern eines Projekts und sind noch nicht geprüft:
 * Jeder Wert kann leer (null) sein. Ob die Eingaben gültig sind, entscheidet der ProjektService.
 * <p>
 * Ein "record" ist eine kurze Schreibweise für eine Klasse, die nur Werte hält: Java erzeugt
 * Konstruktor und Abfragemethoden (daten.name(), daten.startdatum() ...) selbst.
 *
 * @param name             Projektname (Pflicht, höchstens 100 Zeichen)
 * @param beschreibung     freier Text, darf leer sein
 * @param startdatum       Pflicht
 * @param zieldatum        Pflicht, nicht vor dem Startdatum
 * @param projektleitungId Mitarbeiter-ID der verantwortlichen Projektleitung (Pflicht); null = noch nicht gewählt
 *
 * @author Tim Stukenbröker
 */
public record ProjektDaten(
        String name, String beschreibung, LocalDate startdatum, LocalDate zieldatum, Long projektleitungId) {
}
