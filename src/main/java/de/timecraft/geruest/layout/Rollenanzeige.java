package de.timecraft.geruest.layout;

import de.timecraft.benutzer.api.Rolle;
import java.util.Locale;

/** Lesbarer Text einer Rolle fuer die Oberflaeche, z. B. "Projektleitung". */
public final class Rollenanzeige {

    private Rollenanzeige() {
    }

    public static String text(Rolle rolle) {
        String name = rolle.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.GERMAN);
    }
}
