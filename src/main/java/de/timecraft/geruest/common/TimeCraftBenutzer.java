package de.timecraft.geruest.common;

import de.timecraft.benutzer.api.Rolle;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Angemeldete Person fuer Spring Security: Benutzername, Passwort-Hash, Mitarbeiter-ID, Rolle und
 * Klarname (fuer die Kopfzeile). Die Anmeldung von M1 liefert diese Klasse, und zwar bei jedem
 * Aufruf als neues Objekt, weil Spring Security das Passwort nach der Anmeldung aus dem Objekt loescht.
 */
public class TimeCraftBenutzer extends User {

    private final long mitarbeiterId;
    private final Rolle rolle;
    private final String name;

    /** Ohne Klarnamen: Als Name gilt dann der Benutzername. */
    public TimeCraftBenutzer(long mitarbeiterId, String benutzername, String passwortHash, Rolle rolle) {
        this(mitarbeiterId, benutzername, passwortHash, rolle, benutzername);
    }

    public TimeCraftBenutzer(long mitarbeiterId, String benutzername, String passwortHash, Rolle rolle, String name) {
        super(benutzername, passwortHash, List.of(new SimpleGrantedAuthority("ROLE_" + rolle.name())));
        this.mitarbeiterId = mitarbeiterId;
        this.rolle = rolle;
        this.name = name;
    }

    public long mitarbeiterId() {
        return mitarbeiterId;
    }

    public Rolle rolle() {
        return rolle;
    }

    /** Vor- und Nachname, z. B. "Paul Hartmann". */
    public String name() {
        return name;
    }
}
