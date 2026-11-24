package de.timecraft.material.service;

/**
 * Auswahl beim Mehrbedarf (Mockup); wird der Begruendung als "Verschnitt: ..." vorangestellt.
 *
 * @author Tharsan Arumuganathar
 */
public enum Mehrbedarfsgrund {
    VERSCHNITT("Verschnitt"),
    MATERIALFEHLER("Materialfehler"),
    KUNDENAENDERUNG("Kundenänderung");

    private final String text;

    Mehrbedarfsgrund(String text) {
        this.text = text;
    }

    /**
     * @return Text fuer Knopf und Begruendung
     */
    public String text() {
        return text;
    }
}
