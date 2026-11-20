package de.timecraft.material.model;

/** Mengeneinheit eines Materials. In der Datenbank steht das Kuerzel (Spalte material.einheit, hoechstens 5 Zeichen). */
public enum Einheit {
    QUADRATMETER("m²"),
    STUECK("Stk."),
    LAUFMETER("lfm"),
    METER("m"),
    KILOGRAMM("kg"),
    LITER("l");

    private final String kuerzel;

    Einheit(String kuerzel) {
        this.kuerzel = kuerzel;
    }

    public String kuerzel() {
        return kuerzel;
    }

    /** Sucht die Einheit zum Kuerzel aus der Datenbank. */
    public static Einheit ausKuerzel(String kuerzel) {
        for (Einheit einheit : values()) {
            if (einheit.kuerzel.equals(kuerzel)) {
                return einheit;
            }
        }
        throw new IllegalArgumentException("Unbekannte Einheit: " + kuerzel);
    }
}
