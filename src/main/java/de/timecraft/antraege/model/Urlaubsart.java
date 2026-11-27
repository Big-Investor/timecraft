package de.timecraft.antraege.model;

/**
 * Art eines Urlaubs (Entscheidung Alper Sayar, 27.11.2026). Erholungs- und Sonderurlaub zaehlen gegen
 * den Urlaubsanspruch; unbezahlter Urlaub nicht, er wird als Sondergenehmigung gekennzeichnet.
 */
public enum Urlaubsart {
    ERHOLUNGSURLAUB("Erholungsurlaub"),
    SONDERURLAUB("Sonderurlaub"),
    UNBEZAHLTER_URLAUB("Unbezahlter Urlaub");

    private final String bezeichnung;

    Urlaubsart(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String bezeichnung() {
        return bezeichnung;
    }
}
