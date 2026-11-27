package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Demo-Daten: sieben Personen aus dem Mockup, die Team-Benutzernamen mit
 * Passwort {@code timecraft1}, vollständige Startwerte und kein doppeltes Anlegen.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DemoDatenTest {

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

    private DemoDaten demoDaten;

    @BeforeEach
    void vorbereiten() {
        demoDaten = new DemoDaten(benutzerRepository, mitarbeiterRepository, arbeitszeitmodellRepository,
                zeitkontoRepository, urlaubskontoRepository, passwortService, TestDaten.FESTE_UHR, "timecraft1");
    }

    @Test
    void legtSiebenPersonenMitStartwertenTeamBenutzernUndProjektleitungenAlsVorgesetzteAn() {
        demoDaten.anlegenFallsLeer();

        assertThat(benutzerRepository.count()).isEqualTo(7);
        assertThat(mitarbeiterRepository.count()).isEqualTo(7);
        assertThat(arbeitszeitmodellRepository.count()).isEqualTo(7);
        assertThat(zeitkontoRepository.count()).isEqualTo(7);
        assertThat(urlaubskontoRepository.count()).isEqualTo(7);
        assertThat(mitarbeiterRepository.findByAktivTrueOrderByPersonalnummerAsc()).hasSize(6);

        pruefe("admin", Rolle.ADMIN, "Paul Hartmann");
        pruefe("projektleitung", Rolle.PROJEKTLEITUNG, "Lena Schröder");
        pruefe("mitarbeiter", Rolle.MITARBEITER, "Jonas Becker");

        List<Mitarbeiter> alle = mitarbeiterRepository.findAllByOrderByPersonalnummerAsc();
        for (Mitarbeiter mitarbeiter : alle) {
            Mitarbeiter vorgesetzter = mitarbeiter.getVorgesetzter();
            if (vorgesetzter != null) {
                assertThat(vorgesetzter.getBenutzer().getRolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
                assertThat(vorgesetzter.getMitarbeiterId()).isNotEqualTo(mitarbeiter.getMitarbeiterId());
            }
        }
    }

    @Test
    void legtNichtsAnWennSchonBenutzerExistieren() {
        demoDaten.anlegenFallsLeer();
        demoDaten.anlegenFallsLeer();

        assertThat(benutzerRepository.count()).isEqualTo(7);
    }

    private void pruefe(String benutzername, Rolle rolle, String name) {
        Benutzer benutzer = benutzerRepository.findByBenutzername(benutzername).orElseThrow();
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findByBenutzer(benutzer).orElseThrow();

        assertThat(benutzer.getRolle()).isEqualTo(rolle);
        assertThat(mitarbeiter.getName()).isEqualTo(name);
        assertThat(passwortService.passt("timecraft1", benutzer.getPasswortHash())).isTrue();
        assertThat(benutzer.isPasswortAenderungNoetig()).isFalse();
    }
}
