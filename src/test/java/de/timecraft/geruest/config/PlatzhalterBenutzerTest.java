package de.timecraft.geruest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatzhalterBenutzerTest {

    private final PasswordEncoder verschluesselung = new BCryptPasswordEncoder();
    private final UserDetailsService dienst =
            new PlatzhalterBenutzer().platzhalterBenutzerDienst(verschluesselung, "geheim");

    @Test
    void jederBenutzerHatFesteMitarbeiterIdUndRolle() {
        pruefe("mitarbeiter", 1, Rolle.MITARBEITER);
        pruefe("projektleitung", 2, Rolle.PROJEKTLEITUNG);
        pruefe("admin", 3, Rolle.ADMIN);
    }

    @Test
    void passwortIstAlsBcryptHashGespeichert() {
        String gespeichert = dienst.loadUserByUsername("admin").getPassword();

        assertThat(gespeichert).isNotEqualTo("geheim");
        assertThat(verschluesselung.matches("geheim", gespeichert)).isTrue();
    }

    @Test
    void unbekannterBenutzerWirdAbgelehnt() {
        assertThatThrownBy(() -> dienst.loadUserByUsername("niemand"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void jederAufrufLiefertEinNeuesObjekt() {
        TimeCraftBenutzer erster = (TimeCraftBenutzer) dienst.loadUserByUsername("admin");
        erster.eraseCredentials();

        assertThat(dienst.loadUserByUsername("admin").getPassword()).isNotNull();
    }

    private void pruefe(String benutzername, long mitarbeiterId, Rolle rolle) {
        TimeCraftBenutzer benutzer = (TimeCraftBenutzer) dienst.loadUserByUsername(benutzername);

        assertThat(benutzer.mitarbeiterId()).isEqualTo(mitarbeiterId);
        assertThat(benutzer.rolle()).isEqualTo(rolle);
    }
}
