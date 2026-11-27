package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.fake.TestMailService;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Kontofunktionen des Admins im {@link MitarbeiterService}: sperren und entsperren
 * (FA-M1-05, US-M1-06) und Passwort zurücksetzen mit Einmalpasswort (FA-M1-08, UC-M1-10,
 * QA-M1-11, QA-M1-12), mit Negativtests für Projektleitung, Mitarbeiter und ohne Anmeldung.
 * Die Wirkung auf die Anmeldung wird mit dem echten {@link AnmeldeService} geprüft.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class KontoVerwaltungTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private ArbeitszeitmodellRepository arbeitszeitmodellRepository;

    @Autowired
    private ZeitkontoRepository zeitkontoRepository;

    @Autowired
    private UrlaubskontoRepository urlaubskontoRepository;

    private final PasswortService passwortService = TestDaten.passwortService();
    private final TestMailService mailService = new TestMailService();

    private MitarbeiterService mitarbeiterService;
    private AnmeldeService anmeldeService;
    private Mitarbeiter paul;
    private Mitarbeiter jonas;

    @BeforeEach
    void vorbereiten() {
        EinmalpasswortVersand versand = new EinmalpasswortVersand(passwortService, mailService, benutzerRepository);
        mitarbeiterService = new MitarbeiterService(mitarbeiterRepository, benutzerRepository,
                arbeitszeitmodellRepository, zeitkontoRepository, urlaubskontoRepository,
                new MitarbeiterPruefung(benutzerRepository, mitarbeiterRepository), versand, TestDaten.FESTE_UHR);
        anmeldeService = new AnmeldeService(benutzerRepository, mitarbeiterRepository, passwortService);

        paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "p.hartmann", Rolle.ADMIN, "1001");
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker",
                Rolle.MITARBEITER, "1012");
    }

    @Test
    void sperrenEntsperrenUndZuruecksetzenImNormalfall() {
        // Gesperrt: keine Anmeldung; entsperrt: wieder möglich, alle Daten bleiben (US-M1-06)
        mitarbeiterService.sperren(jonas.getMitarbeiterId(), paul.getMitarbeiterId(), Rolle.ADMIN);
        assertThat(konto().isGesperrt()).isTrue();
        assertThatThrownBy(() -> anmeldeService.anmelden("j.becker", TestDaten.PASSWORT))
                .isInstanceOf(LockedException.class);
        mitarbeiterService.entsperren(jonas.getMitarbeiterId(), Rolle.ADMIN);
        assertThat(anmeldeService.anmelden("j.becker", TestDaten.PASSWORT).mitarbeiterId())
                .isEqualTo(jonas.getMitarbeiterId());

        // Zurücksetzen (UC-M1-10): Mail an die hinterlegte Adresse, Hash 24 Stunden gültig, altes Passwort ungültig
        String rueckmeldung = mitarbeiterService.einmalpasswortSenden(jonas.getMitarbeiterId(), Rolle.ADMIN);

        assertThat(rueckmeldung).isEqualTo(TestMailService.RUECKMELDUNG);
        assertThat(mailService.gesendeteMails().get(0).empfaenger()).isEqualTo("j.becker@test.example");
        String einmalpasswort = mailService.letztesEinmalpasswort();
        Benutzer benutzer = konto();
        assertThat(benutzer.getEinmalpasswortHash()).isNotEqualTo(einmalpasswort);
        assertThat(passwortService.passt(einmalpasswort, benutzer.getEinmalpasswortHash())).isTrue();
        assertThat(benutzer.getEinmalpasswortGueltigBis()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
        assertThat(benutzer.isPasswortAenderungNoetig()).isTrue();
        assertThat(passwortService.passt(TestDaten.PASSWORT, benutzer.getPasswortHash())).isFalse();
    }

    @Test
    void versandfehlerUndRechteJeRolle() {
        long jonasId = jonas.getMitarbeiterId();
        long paulId = paul.getMitarbeiterId();
        String hashVorher = konto().getPasswortHash();

        // Versandfehler (QA-M1-11): altes Passwort bleibt gültig, der Admin bekommt eine deutsche Meldung
        mailService.versandSchlaegtFehl();
        assertThatThrownBy(() -> mitarbeiterService.einmalpasswortSenden(jonasId, Rolle.ADMIN))
                .isInstanceOf(FachlicheException.class).hasMessage(TestMailService.FEHLERTEXT);
        assertThat(konto().getPasswortHash()).isEqualTo(hashVorher);
        assertThat(konto().getEinmalpasswortHash()).isNull();

        // Nur der Admin, und der Admin sperrt sich nicht selbst
        assertThatThrownBy(() -> mitarbeiterService.sperren(jonasId, paulId, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.entsperren(jonasId, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.einmalpasswortSenden(jonasId, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.einmalpasswortSenden(jonasId, null))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> mitarbeiterService.sperren(paulId, paulId, Rolle.ADMIN))
                .hasMessage("Sie können Ihr eigenes Konto nicht sperren.");
        assertThatThrownBy(() -> mitarbeiterService.sperren(999_999, paulId, Rolle.ADMIN))
                .isInstanceOf(NichtGefundenException.class);
        assertThat(konto().isGesperrt()).isFalse();
        assertThat(benutzerRepository.findByBenutzername("p.hartmann").orElseThrow().isGesperrt()).isFalse();
    }

    private Benutzer konto() {
        return benutzerRepository.findByBenutzername("j.becker").orElseThrow();
    }
}
