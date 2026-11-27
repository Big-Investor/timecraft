package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.service.TestDaten;
import de.timecraft.geruest.layout.StartView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Sperre aller anderen Seiten, solange nach einem Einmalpasswort noch kein eigenes
 * Passwort festgelegt ist (US-M1-02), für alle drei Rollen.
 *
 * @author Leon-Selim Kannenberg
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswortPflichtTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private PasswortPflicht passwortPflicht;

    @AfterEach
    void aufraeumen() {
        ViewTestHilfe.aufraeumen();
    }

    @Test
    void nachEinmalpasswortWirdJedeAndereSeiteUmgeleitetFuerAlleRollen() {
        Mitarbeiter jonas = person("j.becker", Rolle.MITARBEITER, "1012", true);
        Mitarbeiter lena = person("l.schroeder", Rolle.PROJEKTLEITUNG, "1004", true);
        Mitarbeiter paul = person("p.hartmann", Rolle.ADMIN, "1001", true);

        ViewTestHilfe.anmelden(jonas);
        assertThat(passwortPflicht.mussUmleiten(StartView.class)).isTrue();
        assertThat(passwortPflicht.mussUmleiten(PasswortAendernView.class)).isTrue();
        // Die Seiten „Passwort festlegen“ und „Anmelden“ bleiben erreichbar
        assertThat(passwortPflicht.mussUmleiten(PasswortFestlegenView.class)).isFalse();
        assertThat(passwortPflicht.mussUmleiten(AnmeldeView.class)).isFalse();

        ViewTestHilfe.anmelden(lena);
        assertThat(passwortPflicht.mussUmleiten(StartView.class)).isTrue();
        ViewTestHilfe.anmelden(paul);
        assertThat(passwortPflicht.mussUmleiten(StartView.class)).isTrue();
    }

    @Test
    void mitEigenemPasswortOderOhneAnmeldungWirdNichtUmgeleitet() {
        Mitarbeiter paul = person("p.hartmann", Rolle.ADMIN, "1001", false);

        // Ohne Anmeldung entscheidet Spring Security, nicht die Passwortpflicht
        assertThat(passwortPflicht.mussUmleiten(StartView.class)).isFalse();

        ViewTestHilfe.anmelden(paul);
        assertThat(passwortPflicht.mussUmleiten(StartView.class)).isFalse();
    }

    private Mitarbeiter person(String benutzername, Rolle rolle, String personalnummer, boolean aenderungNoetig) {
        Mitarbeiter mitarbeiter = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository,
                benutzername, rolle, personalnummer);
        Benutzer benutzer = mitarbeiter.getBenutzer();
        benutzer.setPasswortAenderungNoetig(aenderungNoetig);
        benutzerRepository.save(benutzer);
        return mitarbeiter;
    }
}
