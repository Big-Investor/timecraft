package de.timecraft.benutzer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Tabellen benutzer und mitarbeiter mit einer echten Testdatenbank (H2):
 * Speichern, Lesen, Eindeutigkeit (QA-M1-05) und die 1:1-Verknüpfung (FA-M1-13).
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BenutzerUndMitarbeiterRepositoryTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    /** Nach einem abgelehnten Speichern wird der Zwischenspeicher von JPA geleert. */
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void speichertUndFindetBenutzerUndMitarbeiter() {
        Benutzer benutzer = new Benutzer("l.meyer", Rolle.MITARBEITER);
        // Ein BCrypt-Hash ist immer genau 60 Zeichen lang, so wie die Spalte im Relationenmodell.
        String beispielHash = "$2a$10$" + "a".repeat(53);
        benutzer.setPasswortHash(beispielHash);
        benutzer.setEinmalpasswort(beispielHash, LocalDateTime.of(2026, 11, 15, 10, 0));
        benutzer.setPasswortAenderungNoetig(true);
        benutzer.fehlversuchHochzaehlen();
        benutzerRepository.saveAndFlush(benutzer);

        Benutzer gelesen = benutzerRepository.findByBenutzername("l.meyer").orElseThrow();
        assertThat(gelesen.getBenutzerId()).isNotNull();
        assertThat(gelesen.getRolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(gelesen.isGesperrt()).isFalse();
        assertThat(gelesen.getFehlversuche()).isEqualTo(1);
        assertThat(gelesen.isPasswortAenderungNoetig()).isTrue();
        assertThat(gelesen.getEinmalpasswortGueltigBis()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
        assertThat(benutzerRepository.findByBenutzername("gibt.es.nicht")).isEmpty();

        // Mitarbeiter über sein Konto finden; Listen sortiert nach Personalnummer, aktive ohne Ehemalige
        Mitarbeiter lena = mitarbeiterRepository.save(neuerMitarbeiter("projektleitung", "1004"));
        Mitarbeiter jonas = neuerMitarbeiter("mitarbeiter", "1012");
        jonas.setVorgesetzter(lena);
        mitarbeiterRepository.save(jonas);
        Mitarbeiter sophie = neuerMitarbeiter("s.kuehn", "1009");
        sophie.setVorgesetzter(lena);
        sophie.setAktiv(false);
        sophie.setAustrittsdatum(LocalDate.of(2026, 6, 30));
        mitarbeiterRepository.saveAndFlush(sophie);

        Mitarbeiter gefunden = mitarbeiterRepository.findByBenutzer(jonas.getBenutzer()).orElseThrow();
        assertThat(gefunden.getName()).isEqualTo("Vorname Nachname");
        assertThat(gefunden.getQualifikation()).isEqualTo(Qualifikation.GESELLE);
        assertThat(gefunden.getStundensatz()).isEqualByComparingTo("28.00");
        assertThat(mitarbeiterRepository.findAllByOrderByPersonalnummerAsc())
                .extracting(Mitarbeiter::getPersonalnummer).containsExactly("1004", "1009", "1012");
        assertThat(mitarbeiterRepository.findByAktivTrueOrderByPersonalnummerAsc())
                .extracting(Mitarbeiter::getPersonalnummer).containsExactly("1004", "1012");
        assertThat(mitarbeiterRepository.findByVorgesetzterAndAktivTrue(lena))
                .extracting(Mitarbeiter::getPersonalnummer).containsExactly("1012");
    }

    @Test
    void benutzernamePersonalnummerUndKontoSindEindeutig() {
        benutzerRepository.saveAndFlush(new Benutzer("j.becker", Rolle.MITARBEITER));
        assertThat(benutzerRepository.existsByBenutzername("j.becker")).isTrue();
        assertThatThrownBy(() -> benutzerRepository.saveAndFlush(new Benutzer("j.becker", Rolle.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();

        mitarbeiterRepository.saveAndFlush(neuerMitarbeiter("a.vogt", "1018"));
        assertThat(mitarbeiterRepository.existsByPersonalnummer("1018")).isTrue();
        assertThatThrownBy(() -> mitarbeiterRepository.saveAndFlush(neuerMitarbeiter("a.vogt2", "1018")))
                .isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();

        // 1:1 – ein Benutzerkonto gehört zu höchstens einem Mitarbeiter
        Mitarbeiter erster = mitarbeiterRepository.saveAndFlush(neuerMitarbeiter("f.brandt", "1021"));
        Mitarbeiter zweiter = new Mitarbeiter(erster.getBenutzer(), "1022", "Felix", "Doppelt");
        stammdatenFuellen(zweiter);
        assertThatThrownBy(() -> mitarbeiterRepository.saveAndFlush(zweiter))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Legt einen Mitarbeiter mit neuem Benutzerkonto und gültigen Stammdaten an (noch nicht gespeichert). */
    private Mitarbeiter neuerMitarbeiter(String benutzername, String personalnummer) {
        Benutzer benutzer = benutzerRepository.save(new Benutzer(benutzername, Rolle.MITARBEITER));
        Mitarbeiter mitarbeiter = new Mitarbeiter(benutzer, personalnummer, "Vorname", "Nachname");
        stammdatenFuellen(mitarbeiter);
        return mitarbeiter;
    }

    private void stammdatenFuellen(Mitarbeiter mitarbeiter) {
        mitarbeiter.setEmail("test@tischlerei-hartmann.de");
        mitarbeiter.setGeburtsdatum(LocalDate.of(1995, 5, 1));
        mitarbeiter.setEintrittsdatum(LocalDate.of(2020, 8, 1));
        mitarbeiter.setQualifikation(Qualifikation.GESELLE);
        mitarbeiter.setStundensatz(new BigDecimal("28.00"));
    }
}
