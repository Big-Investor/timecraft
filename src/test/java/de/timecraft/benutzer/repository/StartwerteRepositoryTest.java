package de.timecraft.benutzer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft Arbeitszeitmodell (mit Arbeitstagen), Zeitkonto und Urlaubskonto eines Mitarbeiters.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StartwerteRepositoryTest {

    @Autowired
    private TestEntityManager datenbank;

    @Autowired
    private ArbeitszeitmodellRepository arbeitszeitmodellRepository;

    @Autowired
    private ZeitkontoRepository zeitkontoRepository;

    @Autowired
    private UrlaubskontoRepository urlaubskontoRepository;

    private Mitarbeiter lukas;

    @BeforeEach
    void mitarbeiterAnlegen() {
        Benutzer benutzer = datenbank.persist(new Benutzer("l.meyer", Rolle.MITARBEITER));
        lukas = new Mitarbeiter(benutzer, "1024", "Lukas", "Meyer");
        lukas.setEmail("l.meyer@tischlerei-hartmann.de");
        lukas.setGeburtsdatum(LocalDate.of(2009, 3, 14));
        lukas.setEintrittsdatum(LocalDate.of(2026, 8, 1));
        lukas.setQualifikation(Qualifikation.AZUBI);
        lukas = datenbank.persist(lukas);
    }

    @Test
    void speichertArbeitszeitmodellZeitkontoUndUrlaubskonto() {
        Set<DayOfWeek> montagBisFreitag = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        Arbeitszeitmodell modell = arbeitszeitmodellRepository.saveAndFlush(
                new Arbeitszeitmodell(lukas, new BigDecimal("40.00"), montagBisFreitag));
        datenbank.clear();
        Arbeitszeitmodell gelesen = arbeitszeitmodellRepository.findByMitarbeiter(lukas).orElseThrow();
        assertThat(gelesen.getWochenstunden()).isEqualByComparingTo("40");
        assertThat(gelesen.getArbeitstage()).containsExactlyInAnyOrderElementsOf(montagBisFreitag);

        // Arbeitstage lassen sich ändern (Tabelle arbeitstag)
        gelesen.setArbeitstage(Set.of(DayOfWeek.SATURDAY));
        arbeitszeitmodellRepository.saveAndFlush(gelesen);
        datenbank.clear();
        assertThat(arbeitszeitmodellRepository.findById(modell.getArbeitszeitmodellId()).orElseThrow().getArbeitstage())
                .containsExactly(DayOfWeek.SATURDAY);

        // Zeitkonto mit negativem Startsaldo, Urlaubskonto je Jahr
        zeitkontoRepository.saveAndFlush(new Zeitkonto(lukas, LocalDate.of(2026, 8, 1), -150));
        Zeitkonto zeitkonto = zeitkontoRepository.findByMitarbeiter(lukas).orElseThrow();
        assertThat(zeitkonto.getStartdatum()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(zeitkonto.getAnfangssaldoMin()).isEqualTo(-150);

        urlaubskontoRepository.save(new Urlaubskonto(lukas, 2026, 30, 0));
        urlaubskontoRepository.saveAndFlush(new Urlaubskonto(lukas, 2027, 30, 3));
        Urlaubskonto konto2027 = urlaubskontoRepository.findByMitarbeiterAndJahr(lukas, (short) 2027).orElseThrow();
        assertThat(konto2027.getAnspruchTage()).isEqualTo(30);
        assertThat(konto2027.getUebertragTage()).isEqualTo(3);
        assertThat(urlaubskontoRepository.findByMitarbeiterAndJahr(lukas, (short) 2025)).isEmpty();
    }

    @Test
    void jeMitarbeiterHoechstensEinModellEinZeitkontoUndEinUrlaubskontoJeJahr() {
        arbeitszeitmodellRepository.saveAndFlush(
                new Arbeitszeitmodell(lukas, new BigDecimal("40.00"), Set.of(DayOfWeek.MONDAY)));
        assertThatThrownBy(() -> arbeitszeitmodellRepository.saveAndFlush(
                new Arbeitszeitmodell(lukas, new BigDecimal("30.00"), Set.of(DayOfWeek.FRIDAY))))
                .isInstanceOf(DataIntegrityViolationException.class);
        datenbank.clear();

        zeitkontoRepository.saveAndFlush(new Zeitkonto(lukas, LocalDate.of(2026, 8, 1), 0));
        assertThatThrownBy(() -> zeitkontoRepository.saveAndFlush(new Zeitkonto(lukas, LocalDate.of(2026, 9, 1), 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
        datenbank.clear();

        urlaubskontoRepository.saveAndFlush(new Urlaubskonto(lukas, 2026, 30, 0));
        assertThatThrownBy(() -> urlaubskontoRepository.saveAndFlush(new Urlaubskonto(lukas, 2026, 25, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
