package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.WebAttributes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prüft die Anmeldung über die echte Spring-Security-Konfiguration des Gerüsts:
 * Die M1-Benutzerverwaltung ersetzt die frühere Platzhalter-Anmeldung (UC-M1-01, UC-M1-02).
 *
 * @author Leon-Selim Kannenberg
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnmeldungIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private PasswortService passwortService;

    @BeforeEach
    void datenAnlegen() {
        TestDaten.allesLoeschen(benutzerRepository, mitarbeiterRepository);
    }

    @AfterEach
    void datenLoeschen() {
        TestDaten.allesLoeschen(benutzerRepository, mitarbeiterRepository);
    }

    @Test
    void jedeRolleMeldetSichAnUndNachDemAbmeldenIstDieSitzungBeendet() throws Exception {
        Mitarbeiter jonas = person("mitarbeiter", Rolle.MITARBEITER, "1012");
        Mitarbeiter lena = person("projektleitung", Rolle.PROJEKTLEITUNG, "1004");
        Mitarbeiter paul = person("admin", Rolle.ADMIN, "1001");

        pruefeAnmeldung("projektleitung", lena.getMitarbeiterId(), Rolle.PROJEKTLEITUNG);
        pruefeAnmeldung("admin", paul.getMitarbeiterId(), Rolle.ADMIN);
        MvcResult anmeldung = pruefeAnmeldung("mitarbeiter", jonas.getMitarbeiterId(), Rolle.MITARBEITER);
        MockHttpSession sitzung = (MockHttpSession) anmeldung.getRequest().getSession();

        mockMvc.perform(post("/logout").with(csrf()).session(sitzung))
                .andExpect(unauthenticated());

        // Die Sitzung ist beendet: Mit ihr ist keine geschützte Seite mehr erreichbar (US-M1-03).
        assertThat(sitzung.isInvalid()).isTrue();

        // Mit Einmalpasswort gelingt die Anmeldung genau einmal
        Benutzer benutzer = benutzerRepository.findByBenutzername("mitarbeiter").orElseThrow();
        passwortService.einmalpasswortSetzen(benutzer, "Xy7kLmNp2qRs");
        benutzerRepository.save(benutzer);
        mockMvc.perform(formLogin("/login").user("mitarbeiter").password("Xy7kLmNp2qRs"))
                .andExpect(authenticated());
        mockMvc.perform(formLogin("/login").user("mitarbeiter").password("Xy7kLmNp2qRs"))
                .andExpect(unauthenticated());
    }

    @Test
    void falschesPasswortUndGesperrtesKontoFuehrenZurueckZurAnmeldung() throws Exception {
        person("admin", Rolle.ADMIN, "1001");
        person("mitarbeiter", Rolle.MITARBEITER, "1012");

        mockMvc.perform(formLogin("/login").user("admin").password("falsch123"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        assertThat(benutzerRepository.findByBenutzername("admin").orElseThrow().getFehlversuche()).isEqualTo(1);

        Benutzer benutzer = benutzerRepository.findByBenutzername("mitarbeiter").orElseThrow();
        benutzer.setGesperrt(true);
        benutzerRepository.save(benutzer);
        MvcResult ergebnis = mockMvc.perform(formLogin("/login").user("mitarbeiter").password(TestDaten.PASSWORT))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();

        // Aus dieser Sitzungsangabe liest die Anmeldeseite den Text für die Person.
        Object ausnahme = ergebnis.getRequest().getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        assertThat(AnmeldeService.meldungFuer(ausnahme)).isEqualTo(AnmeldeService.KONTO_GESPERRT);
    }

    private Mitarbeiter person(String benutzername, Rolle rolle, String personalnummer) {
        return TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, benutzername, rolle, personalnummer);
    }

    private MvcResult pruefeAnmeldung(String benutzername, long mitarbeiterId, Rolle rolle) throws Exception {
        return mockMvc.perform(formLogin("/login").user(benutzername).password(TestDaten.PASSWORT))
                .andExpect(authenticated().withAuthentication(anmeldung -> {
                    TimeCraftBenutzer benutzer = (TimeCraftBenutzer) anmeldung.getPrincipal();
                    assertThat(benutzer.getUsername()).isEqualTo(benutzername);
                    assertThat(benutzer.mitarbeiterId()).isEqualTo(mitarbeiterId);
                    assertThat(benutzer.rolle()).isEqualTo(rolle);
                }))
                .andReturn();
    }
}
