package de.timecraft.benutzer.service;

/**
 * Ergebnis von „Speichern &amp; Einmalpasswort senden“ (UC-M1-04).
 * Der Mitarbeiter ist immer gespeichert. Hat der Mailversand nicht geklappt (Fehlerfall 7a),
 * ist {@code mailVersendet} false und die Meldung bittet darum, das Einmalpasswort erneut zu senden.
 *
 * @param meldung Text für die Oberfläche
 * @author Leon-Selim Kannenberg
 */
public record AnlegeErgebnis(long mitarbeiterId, boolean mailVersendet, String meldung) {
}
