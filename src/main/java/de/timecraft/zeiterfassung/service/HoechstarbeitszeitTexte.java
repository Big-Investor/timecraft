package de.timecraft.zeiterfassung.service;

/**
 * Deutsche Hinweis- und Warntexte zur Hoechstarbeitszeit (FA-M2-15). Die Grenzen kennt der PausenRegelService.
 *
 * @author Noah Cipa
 */
class HoechstarbeitszeitTexte {

    private final PausenRegelService regeln;

    HoechstarbeitszeitTexte(PausenRegelService regeln) {
        this.regeln = regeln;
    }

    /** Leer, wenn die Tagesgrenze nicht ueberschritten ist. */
    String tagUeberschritten(int tagesMinuten, boolean minderjaehrig) {
        if (!regeln.hoechstarbeitszeitUeberschritten(tagesMinuten, minderjaehrig)) {
            return "";
        }
        return "Sie haben an diesem Tag die erlaubte Höchstarbeitszeit von " + tagesStunden(minderjaehrig)
                + " Stunden überschritten. ";
    }

    /** Leer, wenn die Wochengrenze nicht ueberschritten ist. */
    String wocheUeberschritten(int wochenMinuten, boolean minderjaehrig) {
        if (!regeln.wochenhoechstarbeitszeitUeberschritten(wochenMinuten, minderjaehrig)) {
            return "";
        }
        return "Sie haben in dieser Woche die erlaubte Höchstarbeitszeit von " + wochenStunden()
                + " Stunden überschritten. ";
    }

    /** Laufender Eintrag: Vorwarnung, Erreichen oder Ueberschreitung der Tagesgrenze. */
    String tagesWarnung(int tagesMinuten, boolean minderjaehrig) {
        int grenze = regeln.tagesHoechstMinuten(minderjaehrig);
        if (tagesMinuten > grenze) {
            return tagUeberschritten(tagesMinuten, minderjaehrig);
        }
        if (tagesMinuten == grenze) {
            return "Sie haben die erlaubte Höchstarbeitszeit von " + tagesStunden(minderjaehrig)
                    + " Stunden am Tag erreicht. ";
        }
        if (regeln.vorwarnungTag(tagesMinuten, minderjaehrig)) {
            return "In " + (grenze - tagesMinuten) + " Minuten erreichen Sie die erlaubte Höchstarbeitszeit von "
                    + tagesStunden(minderjaehrig) + " Stunden am Tag. ";
        }
        return "";
    }

    /** Laufender Eintrag: wie {@link #tagesWarnung}, fuer die Wochengrenze der Minderjaehrigen. */
    String wochenWarnung(int wochenMinuten, boolean minderjaehrig) {
        if (regeln.wochenhoechstarbeitszeitUeberschritten(wochenMinuten, minderjaehrig)) {
            return wocheUeberschritten(wochenMinuten, minderjaehrig);
        }
        if (regeln.wochenhoechstarbeitszeitErreicht(wochenMinuten, minderjaehrig)) {
            return "Sie haben die erlaubte Höchstarbeitszeit von " + wochenStunden()
                    + " Stunden in der Woche erreicht. ";
        }
        if (regeln.vorwarnungWoche(wochenMinuten, minderjaehrig)) {
            return "In " + (PausenRegelService.WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE - wochenMinuten)
                    + " Minuten erreichen Sie die erlaubte Höchstarbeitszeit von " + wochenStunden()
                    + " Stunden in der Woche. ";
        }
        return "";
    }

    private int tagesStunden(boolean minderjaehrig) {
        return regeln.tagesHoechstMinuten(minderjaehrig) / 60;
    }

    private int wochenStunden() {
        return PausenRegelService.WOCHEN_HOECHST_MINUTEN_MINDERJAEHRIGE / 60;
    }
}
