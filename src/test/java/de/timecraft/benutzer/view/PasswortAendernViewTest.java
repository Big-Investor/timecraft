package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.router.Route;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.service.BenutzerService;
import de.timecraft.benutzer.service.PasswortService;
import de.timecraft.benutzer.service.TestDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Maske „Passwort ändern“ (UC-M1-03, FA-M1-06, US-M1-04) mit echtem Service und
 * Testdatenbank: Fehler erscheinen am betroffenen Feld und nichts wird gespeichert (QA-M1-07);
 * bei Erfolg werden die Felder geleert. Alle drei Rollen erreichen die Maske über das Menü.
 *
 * @author Leon-Selim Kannenberg
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswortAendernViewTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private BenutzerService benutzerService;

    @Autowired
    private PasswortService passwortService;

    private Mitarbeiter jonas;
    private PasswortAendernView maske;

    @BeforeEach
    void vorbereiten() {
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker", Rolle.MITARBEITER, "1012");
        ViewTestHilfe.anmelden(jonas);
        ViewTestHilfe.fensterOeffnen();
        maske = new PasswortAendernView(benutzerService, new AktuelleSitzung());
    }

    @AfterEach
    void aufraeumen() {
        ViewTestHilfe.aufraeumen();
    }

    @Test
    void maskeIstFuerJedeRolleImMenueUndAendertDasPasswort() {
        PasswortAendernMenuEintrag eintrag = new PasswortAendernMenuEintrag();
        assertThat(eintrag.erlaubteRollen()).containsExactlyInAnyOrder(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG, Rolle.ADMIN);
        assertThat(eintrag.bezeichnung()).isEqualTo("Passwort ändern");
        assertThat(eintrag.route()).isEqualTo(PasswortAendernView.class.getAnnotation(Route.class).value());
        assertThat(eintrag.reihenfolge()).isBetween(300, 399);
        assertThat(PasswortAendernView.class.getAnnotation(Route.class).layout()).isEqualTo(HauptLayout.class);
        assertThat(PasswortAendernView.class.isAnnotationPresent(PermitAll.class)).isTrue();

        assertThat(maske.getElement().getTextRecursively()).contains("Eigenes Passwort ändern", "Passwort ändern");
        assertThat(maske.altesPasswort.getLabel()).isEqualTo("Bisheriges Passwort");
        assertThat(maske.neuesPasswort.getHelperText()).isEqualTo("Mindestens 8 Zeichen und mindestens eine Ziffer.");
        assertThat(maske.wiederholung.getLabel()).isEqualTo("Neues Passwort wiederholen");

        eingeben(TestDaten.PASSWORT, "neuesPasswort7", "neuesPasswort7");
        maske.aendern();

        assertThat(maske.altesPasswort.getValue()).isEmpty();
        assertThat(maske.neuesPasswort.getValue()).isEmpty();
        assertThat(maske.wiederholung.getValue()).isEmpty();
        assertThat(passwortService.passt("neuesPasswort7", konto().getPasswortHash())).isTrue();
    }

    @Test
    void fehlerStehenAmBetroffenenFeldUndVerschwindenNachDerKorrektur() {
        String hashVorher = konto().getPasswortHash();

        eingeben("falsch123", "neuesPasswort7", "neuesPasswort7");
        maske.aendern();
        assertThat(maske.altesPasswort.isInvalid()).isTrue();
        assertThat(maske.altesPasswort.getErrorMessage()).isEqualTo("Das bisherige Passwort ist nicht richtig.");
        assertThat(maske.neuesPasswort.isInvalid()).isFalse();
        assertThat(konto().getPasswortHash()).isEqualTo(hashVorher);

        eingeben(TestDaten.PASSWORT, "ohneziffer", "ohneziffer");
        maske.aendern();
        assertThat(maske.altesPasswort.isInvalid()).isFalse();
        assertThat(maske.neuesPasswort.getErrorMessage()).isEqualTo("Das Passwort muss mindestens eine Ziffer enthalten.");

        eingeben(TestDaten.PASSWORT, "neuesPasswort7", "neuesPasswort8");
        maske.aendern();
        assertThat(maske.wiederholung.isInvalid()).isTrue();
        assertThat(maske.wiederholung.getErrorMessage()).isEqualTo("Die beiden neuen Passwörter stimmen nicht überein.");

        eingeben(TestDaten.PASSWORT, "neuesPasswort7", "neuesPasswort7");
        maske.aendern();
        assertThat(maske.neuesPasswort.isInvalid()).isFalse();
        assertThat(maske.wiederholung.isInvalid()).isFalse();
    }

    private void eingeben(String alt, String neu, String wiederholung) {
        maske.altesPasswort.setValue(alt);
        maske.neuesPasswort.setValue(neu);
        maske.wiederholung.setValue(wiederholung);
    }

    private Benutzer konto() {
        return benutzerRepository.findByBenutzername("j.becker").orElseThrow();
    }
}
