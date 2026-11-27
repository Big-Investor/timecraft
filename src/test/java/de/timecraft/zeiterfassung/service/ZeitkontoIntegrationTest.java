package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitkontoAuskunft;
import de.timecraft.zeiterfassung.api.ZeitkontoInfo;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.repository.FeiertagRepository;

/**
 * Zeitkonto ueber die api mit echter Datenbank und echter Pausenregel je Tag: Nachgetragene Buchungen,
 * uebernommene Abwesenheit und Feiertag wirken auf Soll, Ist und Saldo (Handrechnung, FA-M2-16, FA-M2-21).
 *
 * @author Noah Cipa
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ ZeitkontoService.class, BuchungsAenderungService.class, ZeiteintragService.class,
        PausenRegelService.class, ZugriffsPruefung.class, AbwesenheitService.class, FeiertagService.class,
        IntegrationsTestKonfiguration.class })
class ZeitkontoIntegrationTest {

    @Autowired
    private ZeitkontoAuskunft zeitkonto;

    @Autowired
    private BuchungsAenderung aenderung;

    @Autowired
    private AbwesenheitsUebernahme uebernahme;

    @Autowired
    private FeiertagRepository feiertagRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void leeren() {
        IntegrationsTestKonfiguration.leereTabellen(jdbc);
    }

    /** Uhrzeit am Montag, 02.11.2026. */
    private static Instant montag(int stunde, int minute) {
        return LocalDateTime.of(2026, 11, 2, stunde, minute).atZone(ZoneId.of("Europe/Berlin")).toInstant();
    }

    private void nachtragen(Instant beginn, Instant ende) {
        aenderung.nachtragen(JONAS_BECKER, beginn, ende, Standort.BUERO, null, JONAS_BECKER, "Test");
    }

    @Test
    void monatStimmtMitDerHandrechnungUeberein() {
        // 240 + 270 = 510 min Arbeit, die Luecke von 10 min ist keine Pause: 30 min Abzug, Ist 480
        nachtragen(montag(8, 0), montag(12, 0));
        nachtragen(montag(12, 10), montag(16, 40));
        feiertagRepository.save(new Feiertag(LocalDate.of(2026, 11, 4), "Testfeiertag"));
        uebernahme.uebernehmen(77, JONAS_BECKER, AbwesenheitsArt.KRANKHEIT, LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 5));

        ZeitkontoInfo november = zeitkonto.monat(JONAS_BECKER, YearMonth.of(2026, 11)).orElseThrow();

        // Soll bis heute (10.11.): 02., 03., 06., 09. und 10.11. zu je 480 min
        assertThat(november.sollMinuten()).isEqualTo(5 * 480);
        assertThat(november.istMinuten()).isEqualTo(480);
        assertThat(november.differenzMinuten()).isEqualTo(480 - 5 * 480);
        // Januar bis Oktober 2026 haben 217 Tage Montag bis Freitag (nur der Testfeiertag ist gespeichert)
        assertThat(november.saldoMinuten()).isEqualTo(480 - (217 + 5) * 480);
    }
}
