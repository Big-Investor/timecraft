package de.timecraft.benutzer.service;

import de.timecraft.geruest.common.FachlicheException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Eine oder mehrere Eingaben im Mitarbeiterformular sind ungültig (QA-M1-07, UC-M1-04 5a/5b).
 * Zu jedem Feld (z. B. {@code "personalnummer"}) gibt es eine deutsche Meldung, die die
 * Oberfläche direkt am Feld anzeigen kann. Als Text der Ausnahme dient die erste Meldung.
 * Ist diese Ausnahme aufgetreten, wurde nichts gespeichert.
 *
 * @author Leon-Selim Kannenberg
 */
public class EingabeFehlerException extends FachlicheException {

    private final Map<String, String> fehlerJeFeld;

    public EingabeFehlerException(Map<String, String> fehlerJeFeld) {
        super(ersteMeldung(fehlerJeFeld));
        this.fehlerJeFeld = new LinkedHashMap<>(fehlerJeFeld);
    }

    /** Feldname → Meldung, in der Reihenfolge des Formulars. */
    public Map<String, String> getFehlerJeFeld() {
        return new LinkedHashMap<>(fehlerJeFeld);
    }

    /** Die Meldung zu einem Feld oder {@code null}, wenn das Feld in Ordnung ist. */
    public String meldungZu(String feld) {
        return fehlerJeFeld.get(feld);
    }

    private static String ersteMeldung(Map<String, String> fehlerJeFeld) {
        if (fehlerJeFeld.isEmpty()) {
            return "Bitte prüfen Sie Ihre Eingaben.";
        }
        return fehlerJeFeld.values().iterator().next();
    }
}
