package de.timecraft.benutzer.model;

/**
 * Berufliche Qualifikation eines Mitarbeiters (FA-M1-10).
 * In der Datenbank steht der Name als Text in der Spalte {@code mitarbeiter.qualifikation}.
 *
 * @author Leon-Selim Kannenberg
 */
public enum Qualifikation {
    AZUBI("Azubi"),
    GESELLE("Geselle"),
    MEISTER("Meister");

    private final String anzeigename;

    Qualifikation(String anzeigename) {
        this.anzeigename = anzeigename;
    }

    /** Text für die Oberfläche, z. B. "Geselle". */
    public String getAnzeigename() {
        return anzeigename;
    }
}
