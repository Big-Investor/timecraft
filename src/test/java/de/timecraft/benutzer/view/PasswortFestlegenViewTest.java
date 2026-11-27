package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.spring.security.AuthenticationContext;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.service.BenutzerService;
import de.timecraft.benutzer.service.PasswortService;
import de.timecraft.benutzer.service.TestDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Seite „Eigenes Passwort festlegen“ nach einer Anmeldung mit Einmalpasswort
 * (FA-M1-09, US-M1-02) mit echtem Service und Testdatenbank.
 *
 * @author Leon-Selim Kannenberg
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswortFestlegenViewTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private BenutzerService benutzerService;

    @Autowired
    private PasswortService passwortService;

    private Mitarbeiter jonas;
    private PasswortFestlegenView seite;

    @BeforeEach
    void vorbereiten() {
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker", Rolle.MITARBEITER, "1012");
        Benutzer benutzer = konto();
        passwortService.einmalpasswortSetzen(benutzer, "Xy7kLmNp2qRs");
        benutzerRepository.save(benutzer);

        ViewTestHilfe.anmelden(jonas);
        ViewTestHilfe.fensterOeffnen();
        seite = new PasswortFestlegenView(benutzerService, new AktuelleSitzung(), new AuthenticationContext());
    }

    @AfterEach
    void aufraeumen() {
        ViewTestHilfe.aufraeumen();
    }

    @Test
    void siehtAusWieDieAnmeldeseiteUndGueltigesPasswortHebtDieSperreAuf() {
        String text = seite.getElement().getTextRecursively();
        assertThat(seite.hasClassName("login-view")).isTrue();
        assertThat(text).contains("Zeiterfassung für die Tischlerei.", "Eigenes Passwort festlegen",
                "Sie haben sich mit einem Einmalpasswort angemeldet.", "Passwort festlegen", "Abmelden");
        assertThat(seite.neuesPasswort.getHelperText()).isEqualTo("Mindestens 8 Zeichen und mindestens eine Ziffer.");

        seite.neuesPasswort.setValue("eigenesPasswort1");
        seite.wiederholung.setValue("eigenesPasswort1");
        seite.festlegen();

        assertThat(seite.neuesPasswort.isInvalid()).isFalse();
        assertThat(benutzerService.mussPasswortAendern(jonas.getMitarbeiterId(), Rolle.MITARBEITER)).isFalse();
        assertThat(passwortService.passt("eigenesPasswort1", konto().getPasswortHash())).isTrue();
    }

    @Test
    void fehlerStehenAmFeldUndDieSperreBleibt() {
        seite.neuesPasswort.setValue("kurz1");
        seite.wiederholung.setValue("kurz1");
        seite.festlegen();
        assertThat(seite.neuesPasswort.isInvalid()).isTrue();
        assertThat(seite.neuesPasswort.getErrorMessage()).isEqualTo("Das Passwort muss mindestens 8 Zeichen lang sein.");
        assertThat(seite.wiederholung.isInvalid()).isFalse();

        seite.neuesPasswort.setValue("eigenesPasswort1");
        seite.wiederholung.setValue("eigenesPasswort2");
        seite.festlegen();
        assertThat(seite.neuesPasswort.isInvalid()).isFalse();
        assertThat(seite.wiederholung.getErrorMessage()).isEqualTo("Die beiden neuen Passwörter stimmen nicht überein.");
        assertThat(benutzerService.mussPasswortAendern(jonas.getMitarbeiterId(), Rolle.MITARBEITER)).isTrue();
    }

    private Benutzer konto() {
        return benutzerRepository.findByBenutzername("j.becker").orElseThrow();
    }
}
