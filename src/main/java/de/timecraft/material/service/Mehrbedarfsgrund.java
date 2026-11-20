package de.timecraft.material.service;

/** Auswahl beim Mehrbedarf (Mockup); wird der Begruendung als "Verschnitt: ..." vorangestellt. */
public enum Mehrbedarfsgrund {
    VERSCHNITT("Verschnitt"),
    MATERIALFEHLER("Materialfehler"),
    KUNDENAENDERUNG("Kundenänderung");

    private final String text;

    Mehrbedarfsgrund(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
