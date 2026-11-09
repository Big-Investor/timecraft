package de.timecraft.geruest.common;

import de.timecraft.benutzer.api.Rolle;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Liefert die angemeldete Person. Nur Views benutzen diese Klasse und reichen die Werte als
 * Parameter an Services weiter; Services kennen den Webkontext nicht.
 */
@Component
public class AktuelleSitzung {

    public boolean istAngemeldet() {
        return benutzerOderNull() != null;
    }

    public long mitarbeiterId() {
        return benutzer().mitarbeiterId();
    }

    public String benutzername() {
        return benutzer().getUsername();
    }

    public Rolle rolle() {
        return benutzer().rolle();
    }

    private TimeCraftBenutzer benutzer() {
        TimeCraftBenutzer benutzer = benutzerOderNull();
        if (benutzer == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
        return benutzer;
    }

    private TimeCraftBenutzer benutzerOderNull() {
        Authentication anmeldung = SecurityContextHolder.getContext().getAuthentication();
        if (anmeldung != null && anmeldung.isAuthenticated()
                && anmeldung.getPrincipal() instanceof TimeCraftBenutzer benutzer) {
            return benutzer;
        }
        return null;
    }
}
