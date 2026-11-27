package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft „Passwort ändern“ (FA-M1-06, US-M1-04) und „eigenes Passwort festlegen“ nach einem
 * Einmalpasswort (FA-M1-09, US-M1-02) mit echter Testdatenbank (H2), für alle drei Rollen.
 * Fehler stehen am betroffenen Feld, und es wird nichts gespeichert (QA-M1-07).
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BenutzerServiceTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    private final PasswortService passwortService = TestDaten.passwortService();

    private BenutzerService benutzerService;
    private Mitarbeiter jonas;

    @BeforeEach
    void vorbereiten() {
        benutzerService = new BenutzerService(benutzerRepository, mitarbeiterRepository, passwortService);
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker", Rolle.MITARBEITER, "1012");
    }

    @Test
    void jedeRolleAendertIhrPasswortUndLegtNachEinmalpasswortEinEigenesFest() {
        Mitarbeiter lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository,
                "l.schroeder", Rolle.PROJEKTLEITUNG, "1004");
        Mitarbeiter paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository,
                "p.hartmann", Rolle.ADMIN, "1001");

        benutzerService.passwortAendern(jonas.getMitarbeiterId(), Rolle.MITARBEITER,
                TestDaten.PASSWORT, "neuesPasswort7", "neuesPasswort7");
        benutzerService.passwortAendern(lena.getMitarbeiterId(), Rolle.PROJEKTLEITUNG,
                TestDaten.PASSWORT, "leitung2026", "leitung2026");
        benutzerService.passwortAendern(paul.getMitarbeiterId(), Rolle.ADMIN,
                TestDaten.PASSWORT, "verwaltung1", "verwaltung1");

        // Gespeichert wird nur der BCrypt-Hash; das alte Passwort gilt nicht mehr (QA-M1-01)
        assertThat(konto().getPasswortHash()).isNotEqualTo("neuesPasswort7");
        assertThat(passwortService.passt("neuesPasswort7", konto().getPasswortHash())).isTrue();
        assertThat(passwortService.passt(TestDaten.PASSWORT, konto().getPasswortHash())).isFalse();
        assertThat(passwortService.passt("leitung2026", lena.getBenutzer().getPasswortHash())).isTrue();
        assertThat(passwortService.passt("verwaltung1", paul.getBenutzer().getPasswortHash())).isTrue();

        // Nach einem Einmalpasswort ist ein eigenes Passwort nötig; danach nicht mehr
        Benutzer benutzer = konto();
        passwortService.einmalpasswortSetzen(benutzer, "Xy7kLmNp2qRs");
        benutzerRepository.save(benutzer);
        assertThat(benutzerService.mussPasswortAendern(jonas.getMitarbeiterId(), Rolle.MITARBEITER)).isTrue();
        benutzerService.erstesPasswortFestlegen(jonas.getMitarbeiterId(), Rolle.MITARBEITER,
                "eigenesPasswort1", "eigenesPasswort1");
        assertThat(benutzerService.mussPasswortAendern(jonas.getMitarbeiterId(), Rolle.MITARBEITER)).isFalse();
        assertThat(passwortService.passt("eigenesPasswort1", konto().getPasswortHash())).isTrue();
        assertThat(konto().getEinmalpasswortHash()).isNull();
    }

    @Test
    void falscheEingabenStehenAmFeldUndAendernNichts() {
        long id = jonas.getMitarbeiterId();
        String hashVorher = konto().getPasswortHash();

        EingabeFehlerException altFalsch = feldfehler(() -> benutzerService.passwortAendern(
                id, Rolle.MITARBEITER, "falsch123", "neuesPasswort7", "neuesPasswort7"));
        EingabeFehlerException zuKurz = feldfehler(() -> benutzerService.passwortAendern(
                id, Rolle.MITARBEITER, TestDaten.PASSWORT, "kurz1", "kurz1"));
        EingabeFehlerException ohneZiffer = feldfehler(() -> benutzerService.passwortAendern(
                id, Rolle.MITARBEITER, TestDaten.PASSWORT, "ohneziffer", "ohneziffer"));
        EingabeFehlerException ungleich = feldfehler(() -> benutzerService.passwortAendern(
                id, Rolle.MITARBEITER, TestDaten.PASSWORT, "neuesPasswort7", "neuesPasswort8"));

        assertThat(altFalsch.getFehlerJeFeld()).containsOnlyKeys(BenutzerService.FELD_ALTES_PASSWORT)
                .containsValue("Das bisherige Passwort ist nicht richtig.");
        assertThat(zuKurz.meldungZu(BenutzerService.FELD_NEUES_PASSWORT))
                .isEqualTo("Das Passwort muss mindestens 8 Zeichen lang sein.");
        assertThat(ohneZiffer.meldungZu(BenutzerService.FELD_NEUES_PASSWORT))
                .isEqualTo("Das Passwort muss mindestens eine Ziffer enthalten.");
        assertThat(ungleich.meldungZu(BenutzerService.FELD_WIEDERHOLUNG))
                .isEqualTo("Die beiden neuen Passwörter stimmen nicht überein.");
        assertThat(konto().getPasswortHash()).isEqualTo(hashVorher);

        // Eigenes Passwort festlegen geht nur nach einem Einmalpasswort
        assertThatThrownBy(() -> benutzerService.erstesPasswortFestlegen(id, Rolle.MITARBEITER,
                "neuesPasswort7", "neuesPasswort7"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Ihr Passwort ist bereits festgelegt. Bitte nutzen Sie „Passwort ändern“.");

        // Ohne Anmeldung nichts; unbekanntes Konto wird gemeldet
        assertThatThrownBy(() -> benutzerService.passwortAendern(id, null, TestDaten.PASSWORT, "neuesPasswort7",
                "neuesPasswort7")).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> benutzerService.mussPasswortAendern(id, null))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> benutzerService.mussPasswortAendern(999_999, Rolle.MITARBEITER))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Ihr Benutzerkonto wurde nicht gefunden.");
    }

    private EingabeFehlerException feldfehler(ThrowingCallable aufruf) {
        return catchThrowableOfType(EingabeFehlerException.class, aufruf);
    }

    private Benutzer konto() {
        return benutzerRepository.findByBenutzername("j.becker").orElseThrow();
    }
}
