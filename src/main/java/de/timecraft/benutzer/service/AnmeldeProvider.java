package de.timecraft.benutzer.service;

import de.timecraft.geruest.common.TimeCraftBenutzer;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Verbindet die Anmeldeseite (Spring Security) mit dem {@link AnmeldeService}.
 * Spring Security findet diese Klasse automatisch und nutzt sie für jede Anmeldung.
 *
 * <p>Wir nutzen einen eigenen {@code AuthenticationProvider} statt eines {@code UserDetailsService},
 * weil neben dem Passwort auch ein Einmalpasswort passen kann und Fehlversuche gezählt werden.
 * Die eigentliche Prüfung steht im {@link AnmeldeService}; diese Klasse reicht nur weiter.
 *
 * @author Leon-Selim Kannenberg
 */
@Component
public class AnmeldeProvider implements AuthenticationProvider {

    private final AnmeldeService anmeldeService;

    public AnmeldeProvider(AnmeldeService anmeldeService) {
        this.anmeldeService = anmeldeService;
    }

    @Override
    public Authentication authenticate(Authentication anmeldung) {
        String benutzername = anmeldung.getName();
        String passwort = "";
        if (anmeldung.getCredentials() != null) {
            passwort = anmeldung.getCredentials().toString();
        }
        TimeCraftBenutzer benutzer = anmeldeService.anmelden(benutzername, passwort);
        return UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities());
    }

    /** Wir prüfen nur Anmeldungen mit Benutzername und Passwort. */
    @Override
    public boolean supports(Class<?> art) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(art);
    }
}
