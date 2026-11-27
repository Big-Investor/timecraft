package de.timecraft.antraege.model;

/**
 * Antragstyp aus Sicht der Person (FA-M5-01). In der Datenbank steht der Typ als Diskriminator
 * (ZEITKORREKTUR, ABWESENHEIT, STORNO); Urlaub, Krankheit und sonstige Abwesenheit unterscheidet
 * abwesenheitsantrag.art. Eine Aenderung ist ein Antrag dieser Typen mit Bezugsantrag.
 */
public enum Antragstyp {
    ZEITKORREKTUR("Zeitkorrektur"),
    URLAUB("Urlaub"),
    KRANKHEIT("Krankheit"),
    SONSTIGE_ABWESENHEIT("Sonstige Abwesenheit"),
    STORNO("Storno");

    private final String bezeichnung;

    Antragstyp(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    public String bezeichnung() {
        return bezeichnung;
    }

    /** Typ eines Antrags; null fuer Antraege anderer Module (z. B. Materialanpassung aus M4). */
    public static Antragstyp von(Antrag antrag) {
        if (antrag instanceof Zeitkorrekturantrag) {
            return ZEITKORREKTUR;
        }
        if (antrag instanceof Stornoantrag) {
            return STORNO;
        }
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            return switch (abwesenheit.getArt()) {
                case URLAUB -> URLAUB;
                case KRANKHEIT -> KRANKHEIT;
                case SONSTIGE -> SONSTIGE_ABWESENHEIT;
            };
        }
        return null;
    }
}
