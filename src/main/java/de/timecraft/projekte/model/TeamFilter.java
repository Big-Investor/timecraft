package de.timecraft.projekte.model;

import de.timecraft.zeiterfassung.api.Standort;

/**
 * Die Filter der Team-Übersicht für die verantwortliche Projektleitung (FA-M3-09).
 * Jeder Filter ist freiwillig: Ist ein Wert null, wird er ignoriert. Ein frisch erzeugtes Objekt
 * filtert also nichts, "Zurücksetzen" heißt: ein neues TeamFilter-Objekt nehmen.
 * <p>
 * Beide Filter wirken gemeinsam. Der Standortfilter gilt nur für eingestempelte Personen: Wer nicht
 * eingestempelt ist, hat keinen Standort und erscheint bei gesetztem Standortfilter nie.
 */
public class TeamFilter {

    /** true = nur eingestempelte Personen, false = nur nicht eingestempelte, null = alle. */
    private Boolean eingestempelt;

    /** Nur Personen, die gerade an diesem Standort eingestempelt sind; null = alle. */
    private Standort standort;

    public Boolean getEingestempelt() {
        return eingestempelt;
    }

    public void setEingestempelt(Boolean eingestempelt) {
        this.eingestempelt = eingestempelt;
    }

    public Standort getStandort() {
        return standort;
    }

    public void setStandort(Standort standort) {
        this.standort = standort;
    }
}
