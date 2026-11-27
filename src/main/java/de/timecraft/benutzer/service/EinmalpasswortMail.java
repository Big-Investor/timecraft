package de.timecraft.benutzer.service;

import de.timecraft.geruest.common.FachlicheException;

/**
 * Inhalt der E-Mail mit einem Einmalpasswort (FA-M1-08, FA-M1-09): Empfänger, Betreff und Text.
 * Mit {@link #erstellen} entsteht die Mail; dabei wird die E-Mail-Adresse grob geprüft (UC-M1-10, 3b).
 *
 * @author Leon-Selim Kannenberg
 */
public record EinmalpasswortMail(String empfaenger, String betreff, String text) {

    static final String BETREFF = "Ihr Einmalpasswort für TimeCraft";

    /** Baut die Mail für eine Person. Wirft eine {@link FachlicheException}, wenn die Adresse fehlt oder ungültig ist. */
    public static EinmalpasswortMail erstellen(String empfaenger, String name, String benutzername,
            String einmalpasswort) {
        pruefeAdresse(empfaenger);
        String text = "Hallo " + name + ",\n\n"
                + "für Sie wurde ein Einmalpasswort für TimeCraft erstellt.\n\n"
                + "Benutzername: " + benutzername + "\n"
                + "Einmalpasswort: " + einmalpasswort + "\n\n"
                + "Das Einmalpasswort ist 24 Stunden gültig und funktioniert nur für eine Anmeldung.\n"
                + "Nach der Anmeldung legen Sie bitte ein eigenes Passwort fest.\n\n"
                + "Ihre Verwaltung";
        return new EinmalpasswortMail(empfaenger, BETREFF, text);
    }

    /**
     * Einfache Prüfung einer E-Mail-Adresse: Text vor dem @, ein Punkt im Teil nach dem @,
     * keine Leerzeichen. Wird auch im Mitarbeiterformular benutzt.
     */
    public static boolean istGueltigeAdresse(String adresse) {
        if (adresse == null) {
            return false;
        }
        int positionAt = adresse.indexOf('@');
        return positionAt > 0
                && adresse.indexOf('.', positionAt) > positionAt + 1
                && !adresse.endsWith(".")
                && !adresse.contains(" ");
    }

    private static void pruefeAdresse(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            throw new FachlicheException("Für diesen Mitarbeiter ist keine E-Mail-Adresse hinterlegt.");
        }
        if (!istGueltigeAdresse(adresse)) {
            throw new FachlicheException("Die E-Mail-Adresse „" + adresse
                    + "“ ist ungültig. Bitte korrigieren Sie sie in den Stammdaten.");
        }
    }
}
