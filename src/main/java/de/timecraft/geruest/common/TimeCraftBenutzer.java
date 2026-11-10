package de.timecraft.geruest.common;

import de.timecraft.benutzer.api.Rolle;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Angemeldete Person fuer Spring Security: Benutzername, Passwort-Hash, Mitarbeiter-ID und Rolle.
 * Jeder {@code UserDetailsService} (Platzhalter jetzt, M1 spaeter) muss diese Klasse liefern,
 * und zwar bei jedem Aufruf als neues Objekt, weil Spring Security das Passwort nach der
 * Anmeldung aus dem Objekt loescht.
 */
public class TimeCraftBenutzer extends User {

    private final long mitarbeiterId;
    private final Rolle rolle;

    public TimeCraftBenutzer(long mitarbeiterId, String benutzername, String passwortHash, Rolle rolle) {
        super(benutzername, passwortHash, List.of(new SimpleGrantedAuthority("ROLE_" + rolle.name())));
        this.mitarbeiterId = mitarbeiterId;
        this.rolle = rolle;
    }

    public long mitarbeiterId() {
        return mitarbeiterId;
    }

    public Rolle rolle() {
        return rolle;
    }
}
