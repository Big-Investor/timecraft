package de.timecraft.geruest.config;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Feste Testbenutzer nur im Speicher, ohne Datenbank. M1 ersetzt diese Klasse durch die echte
 * Benutzerverwaltung: Klasse loeschen, eigenen UserDetailsService bereitstellen.
 */
@Configuration
public class PlatzhalterBenutzer {

    @Bean
    public UserDetailsService platzhalterBenutzerDienst(
            PasswordEncoder passwortVerschluesselung,
            @Value("${timecraft.platzhalter.passwort}") String passwort) {
        String hash = passwortVerschluesselung.encode(passwort);
        return benutzername -> switch (benutzername) {
            case "mitarbeiter" -> new TimeCraftBenutzer(1, benutzername, hash, Rolle.MITARBEITER);
            case "projektleitung" -> new TimeCraftBenutzer(2, benutzername, hash, Rolle.PROJEKTLEITUNG);
            case "admin" -> new TimeCraftBenutzer(3, benutzername, hash, Rolle.ADMIN);
            default -> throw new UsernameNotFoundException("Unbekannter Benutzer");
        };
    }
}
