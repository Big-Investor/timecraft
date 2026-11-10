package de.timecraft.geruest.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.spring.security.AuthenticationContext;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class HauptLayoutTest {

    @AfterEach
    void sitzungLeeren() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void kopfzeileZeigtBenutzernameUndRolleDerAngemeldetenPerson() {
        anmelden(new TimeCraftBenutzer(2, "projektleitung", "hash", Rolle.PROJEKTLEITUNG));

        HauptLayout layout = new HauptLayout(new AktuelleSitzung(), new AuthenticationContext(), List.of());
        String text = layout.getElement().getTextRecursively();

        assertThat(text).contains("projektleitung", "Projektleitung", "PR");
    }

    @Test
    void seitenleisteZeigtLogoUndAbmelden() {
        anmelden(new TimeCraftBenutzer(1, "mitarbeiter", "hash", Rolle.MITARBEITER));

        HauptLayout layout = new HauptLayout(new AktuelleSitzung(), new AuthenticationContext(), List.of());
        String text = layout.getElement().getTextRecursively();

        assertThat(text).contains("TimeCraft", "Abmelden");
    }

    private void anmelden(TimeCraftBenutzer benutzer) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }
}
