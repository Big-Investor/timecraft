package de.timecraft.geruest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SicherheitsKonfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServerProperties serverProperties;

    @Value("${timecraft.platzhalter.passwort}")
    private String passwort;

    @Test
    void ohneAnmeldungLeitetEineSeiteAufDieAnmeldungUm() throws Exception {
        mockMvc.perform(get("/irgendeine-seite"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void jeRolleMeldetSichAnUndBekommtIhreMitarbeiterIdUndRolle() throws Exception {
        pruefeAnmeldung("mitarbeiter", 1, Rolle.MITARBEITER);
        pruefeAnmeldung("projektleitung", 2, Rolle.PROJEKTLEITUNG);
        pruefeAnmeldung("admin", 3, Rolle.ADMIN);
    }

    @Test
    void falschesPasswortFuehrtZurueckZurAnmeldungMitFehler() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("falsch"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void sitzungEndetNachDreissigMinuten() {
        assertThat(serverProperties.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofMinutes(30));
    }

    private void pruefeAnmeldung(String benutzername, long mitarbeiterId, Rolle rolle) throws Exception {
        mockMvc.perform(formLogin("/login").user(benutzername).password(passwort))
                .andExpect(authenticated().withAuthentication(anmeldung -> {
                    TimeCraftBenutzer benutzer = (TimeCraftBenutzer) anmeldung.getPrincipal();
                    assertThat(benutzer.getUsername()).isEqualTo(benutzername);
                    assertThat(benutzer.mitarbeiterId()).isEqualTo(mitarbeiterId);
                    assertThat(benutzer.rolle()).isEqualTo(rolle);
                }));
    }
}
