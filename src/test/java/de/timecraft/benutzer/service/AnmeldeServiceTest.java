package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Anmeldung (UC-M1-01) mit echter Testdatenbank (H2): richtige und falsche Daten,
 * Sperre (FA-M1-05), Einmalpasswort (FA-M1-09, QA-M1-01, QA-M1-12), alle drei Rollen und den
 * Text, den die Anmeldeseite zu einer Ablehnung zeigt (US-M1-01).
 *
 * <p>Die Tests laufen bewusst ohne umschließende Transaktion ({@code NOT_SUPPORTED}): So wird
 * geprüft, dass ein gezählter Fehlversuch auch dann gespeichert bleibt, wenn die Anmeldung
 * mit einer Ausnahme endet.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AnmeldeService.class, PasswortService.class, AnmeldeServiceTest.Konfiguration.class})
class AnmeldeServiceTest {

    @TestConfiguration
    static class Konfiguration {
        @Bean
        Clock clock() {
            return TestDaten.FESTE_UHR;
        }

        @Bean
        PasswordEncoder passwortVerschluesselung() {
            return TestDaten.VERSCHLUESSELUNG;
        }
    }

    @Autowired
    private AnmeldeService anmeldeService;

    @Autowired
    private PasswortService passwortService;

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    private Mitarbeiter jonas;

    @BeforeEach
    void datenAnlegen() {
        TestDaten.allesLoeschen(benutzerRepository, mitarbeiterRepository);
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker", Rolle.MITARBEITER, "1012");
    }

    @AfterEach
    void datenLoeschen() {
        TestDaten.allesLoeschen(benutzerRepository, mitarbeiterRepository);
    }

    @Test
    void jedeRolleMeldetSichAnUndEinmalpasswortGiltGenauEinmal() {
        TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "l.schroeder", Rolle.PROJEKTLEITUNG, "1004");
        TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "p.hartmann", Rolle.ADMIN, "1001");

        TimeCraftBenutzer angemeldet = anmeldeService.anmelden("j.becker", TestDaten.PASSWORT);
        assertThat(angemeldet.mitarbeiterId()).isEqualTo(jonas.getMitarbeiterId());
        assertThat(angemeldet.name()).isEqualTo("Test Person 1012");
        assertThat(angemeldet.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MITARBEITER");
        assertThat(anmeldeService.anmelden("j.becker", TestDaten.PASSWORT)).isNotSameAs(angemeldet);
        assertThat(anmeldeService.anmelden("l.schroeder", TestDaten.PASSWORT).rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
        assertThat(anmeldeService.anmelden("p.hartmann", TestDaten.PASSWORT).rolle()).isEqualTo(Rolle.ADMIN);

        // Ein Fehlversuch wird gezählt, eine erfolgreiche Anmeldung setzt die Zählung zurück
        assertThatThrownBy(() -> anmeldeService.anmelden("j.becker", "falsch123"))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(konto("j.becker").getFehlversuche()).isEqualTo(1);
        anmeldeService.anmelden("j.becker", TestDaten.PASSWORT);
        assertThat(konto("j.becker").getFehlversuche()).isZero();

        // Eine geänderte Rolle gilt ab der nächsten Anmeldung
        Benutzer benutzer = konto("j.becker");
        benutzer.setRolle(Rolle.PROJEKTLEITUNG);
        benutzerRepository.save(benutzer);
        assertThat(anmeldeService.anmelden("j.becker", TestDaten.PASSWORT).rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);

        // Einmalpasswort: nur einmal gültig, danach muss ein eigenes Passwort her; das alte gilt nicht mehr
        einmalpasswortVergeben("Xy7kLmNp2qRs");
        assertThat(anmeldeService.anmelden("j.becker", "Xy7kLmNp2qRs").getUsername()).isEqualTo("j.becker");
        assertThat(konto("j.becker").isPasswortAenderungNoetig()).isTrue();
        assertThat(konto("j.becker").getEinmalpasswortHash()).isNull();
        assertThatThrownBy(() -> anmeldeService.anmelden("j.becker", "Xy7kLmNp2qRs"))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> anmeldeService.anmelden("j.becker", TestDaten.PASSWORT))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void abgelehnteAnmeldungenUndIhreMeldungen() {
        // Falsches Passwort und unbekannter Benutzer: dieselbe neutrale Meldung
        Throwable falsch = catchThrowable(() -> anmeldeService.anmelden("j.becker", "falsch123"));
        Throwable unbekannt = catchThrowable(() -> anmeldeService.anmelden("gibt.es.nicht", TestDaten.PASSWORT));
        assertThat(falsch).isInstanceOf(BadCredentialsException.class).hasMessage("Benutzername oder Passwort ist falsch.");
        assertThat(AnmeldeService.meldungFuer(unbekannt)).isEqualTo(AnmeldeService.FALSCHE_DATEN);

        // Automatische Sperre nach 5 Fehlversuchen ist zurückgestellt (FA-M1-K2): Es wird nur gezählt
        for (int versuch = 0; versuch < 5; versuch++) {
            catchThrowable(() -> anmeldeService.anmelden("j.becker", "falsch123"));
        }
        assertThat(konto("j.becker").getFehlversuche()).isEqualTo(6);
        assertThat(konto("j.becker").isGesperrt()).isFalse();

        // Gesperrtes Konto: Grund nur bei richtigem Passwort, Einmalpasswort wird nicht verbraucht
        einmalpasswortVergeben("Xy7kLmNp2qRs");
        Benutzer benutzer = konto("j.becker");
        benutzer.setGesperrt(true);
        benutzerRepository.save(benutzer);
        Throwable gesperrtFalsch = catchThrowable(() -> anmeldeService.anmelden("j.becker", "falsch123"));
        Throwable gesperrtRichtig = catchThrowable(() -> anmeldeService.anmelden("j.becker", "Xy7kLmNp2qRs"));
        assertThat(AnmeldeService.meldungFuer(gesperrtFalsch)).isEqualTo(AnmeldeService.FALSCHE_DATEN);
        assertThat(gesperrtRichtig).isInstanceOf(LockedException.class);
        assertThat(AnmeldeService.meldungFuer(gesperrtRichtig))
                .isEqualTo("Ihr Konto ist gesperrt. Bitte wenden Sie sich an die Verwaltung.");
        assertThat(konto("j.becker").getEinmalpasswortHash()).isNotNull();

        // Abgelaufenes Einmalpasswort (QA-M1-12): Feste Uhr 14.11.2026 10:00, gültig nur bis 9:59
        benutzer = konto("j.becker");
        benutzer.setGesperrt(false);
        benutzer.setEinmalpasswort(passwortService.hashen("Ab3cDeFgHjKm"), LocalDateTime.of(2026, 11, 14, 9, 59));
        benutzerRepository.save(benutzer);
        Throwable abgelaufen = catchThrowable(() -> anmeldeService.anmelden("j.becker", "Ab3cDeFgHjKm"));
        assertThat(abgelaufen).isInstanceOf(CredentialsExpiredException.class);
        assertThat(AnmeldeService.meldungFuer(abgelaufen))
                .isEqualTo("Das Einmalpasswort ist abgelaufen. Bitte lassen Sie sich von der Verwaltung ein neues senden.");

        // Deaktivierter Mitarbeiter (das Einmalpasswort hat das alte Passwort ersetzt)
        Mitarbeiter mitarbeiter = mitarbeiterRepository.findById(jonas.getMitarbeiterId()).orElseThrow();
        mitarbeiter.setAktiv(false);
        mitarbeiterRepository.save(mitarbeiter);
        Throwable inaktiv = catchThrowable(() -> anmeldeService.anmelden("j.becker", "Ab3cDeFgHjKm"));
        assertThat(inaktiv).isInstanceOf(DisabledException.class);
        assertThat(AnmeldeService.meldungFuer(inaktiv))
                .isEqualTo("Ihr Konto ist nicht mehr aktiv. Bitte wenden Sie sich an die Verwaltung.");
        assertThat(AnmeldeService.meldungFuer(null)).isEqualTo(AnmeldeService.FALSCHE_DATEN);
    }

    private void einmalpasswortVergeben(String einmalpasswort) {
        Benutzer benutzer = konto("j.becker");
        passwortService.einmalpasswortSetzen(benutzer, einmalpasswort);
        benutzerRepository.save(benutzer);
    }

    private Benutzer konto(String benutzername) {
        return benutzerRepository.findByBenutzername(benutzername).orElseThrow();
    }
}
