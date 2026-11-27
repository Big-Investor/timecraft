package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.springframework.transaction.support.TransactionTemplate;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.repository.FeiertagRepository;

/**
 * AbwesenheitsUebernahme mit echter Datenbank. M5 ruft sie innerhalb seiner eigenen Transaktion auf: Scheitert
 * die Genehmigung, darf nichts uebrig bleiben. Ohne Testtransaktion, damit Commit und Rollback stattfinden.
 *
 * @author Noah Cipa
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ AbwesenheitService.class, FeiertagService.class, ZugriffsPruefung.class,
        IntegrationsTestKonfiguration.class })
class AbwesenheitsUebernahmeIntegrationTest {

    @Autowired
    private AbwesenheitsUebernahme uebernahme;

    @Autowired
    private FeiertagRepository feiertagRepository;

    @Autowired
    private TransactionTemplate transaktion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void leeren() {
        IntegrationsTestKonfiguration.leereTabellen(jdbc);
    }

    private static LocalDate dezember(int tag) {
        return LocalDate.of(2026, 12, tag);
    }

    private long urlaub(long antragId, int von, int bis) {
        return uebernahme.uebernehmen(antragId, JONAS_BECKER, AbwesenheitsArt.URLAUB, dezember(von), dezember(bis));
    }

    private int anzahlAbwesenheiten() {
        return jdbc.queryForObject("select count(*) from abwesenheit", Integer.class);
    }

    @Test
    void uebernehmenSpeichertDieAbwesenheitZumAntrag() {
        long id = urlaub(77, 21, 23);

        assertThat(uebernahme.abwesenheitZuAntrag(77)).contains(id);
        assertThat(uebernahme.abwesenheitZuAntrag(78)).isEmpty();
        assertThat(jdbc.queryForMap("select * from abwesenheit where abwesenheit_id = ?", id))
                .containsEntry("mitarbeiter_id", JONAS_BECKER)
                .containsEntry("antrag_id", 77L)
                .containsEntry("art", "URLAUB")
                .containsEntry("von", Date.valueOf(dezember(21)))
                .containsEntry("bis", Date.valueOf(dezember(23)));
    }

    @Test
    void ueberschneidungZaehltRandtageAberNichtAngrenzendeTageUndAndereMitarbeiter() {
        urlaub(77, 21, 23);

        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(18), dezember(21))).isTrue();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(23), dezember(28))).isTrue();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(22), dezember(22))).isTrue();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(1), dezember(31))).isTrue();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(18), dezember(20))).isFalse();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(24), dezember(28))).isFalse();
        assertThat(uebernahme.ueberschneidetAbwesenheit(LENA_SCHROEDER, dezember(21), dezember(23))).isFalse();
    }

    @Test
    void ueberschneidendeAbwesenheitWirdNichtGespeichert() {
        urlaub(77, 21, 23);

        assertThatThrownBy(() -> urlaub(78, 23, 28))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("In diesem Zeitraum gibt es schon eine Abwesenheit.");

        assertThat(anzahlAbwesenheiten()).isEqualTo(1);
        assertThat(uebernahme.abwesenheitZuAntrag(78)).isEmpty();
    }

    @Test
    void scheitertDieGenehmigungInM5BleibtKeineAbwesenheit() {
        assertThatThrownBy(() -> transaktion.executeWithoutResult(status -> {
            urlaub(77, 21, 23);
            throw new IllegalStateException("Fehler beim Speichern des Antrags");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(anzahlAbwesenheiten()).isZero();
    }

    @Test
    void fachfehlerRolltDieGanzeTransaktionDesAufrufersZurueck() {
        assertThatThrownBy(() -> transaktion.executeWithoutResult(status -> {
            urlaub(77, 21, 23);
            urlaub(78, 22, 22);
        })).isInstanceOf(FachlicheException.class);

        assertThat(anzahlAbwesenheiten()).isZero();
    }

    @Test
    void aufhebenLoeschtDieAbwesenheitUndGibtDenZeitraumFrei() {
        long id = urlaub(77, 21, 23);

        uebernahme.aufheben(id);

        assertThat(anzahlAbwesenheiten()).isZero();
        assertThat(uebernahme.abwesenheitZuAntrag(77)).isEmpty();
        assertThat(uebernahme.ueberschneidetAbwesenheit(JONAS_BECKER, dezember(21), dezember(23))).isFalse();
    }

    @Test
    void scheitertDerAufruferBleibtDieAufgehobeneAbwesenheitBestehen() {
        long id = urlaub(77, 21, 23);

        assertThatThrownBy(() -> transaktion.executeWithoutResult(status -> {
            uebernahme.aufheben(id);
            throw new IllegalStateException("Fehler beim Stornieren des Antrags");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(uebernahme.abwesenheitZuAntrag(77)).contains(id);
    }

    @Test
    void unbekannteAbwesenheitKannNichtAufgehobenWerden() {
        assertThatThrownBy(() -> uebernahme.aufheben(999_999L))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Die Abwesenheit wurde nicht gefunden.");
    }

    @Test
    void urlaubstageOhneGespeicherteFeiertageUndArbeitsfreieTage() {
        feiertagRepository.save(new Feiertag(dezember(25), "1. Weihnachtstag"));

        // Mo 21.12. bis So 27.12.: Montag bis Donnerstag zaehlen, Freitag ist Feiertag
        assertThat(uebernahme.urlaubstage(JONAS_BECKER, dezember(21), dezember(27))).isEqualTo(4);
        // Teilzeit Montag bis Mittwoch
        assertThat(uebernahme.urlaubstage(LENA_SCHROEDER, dezember(21), dezember(27))).isEqualTo(3);
    }

    /** Zwei Genehmigungen in M5 gleichzeitig (QA-M2-08): Die zweite wartet und sieht dann die erste. */
    @Test
    void zweiteGenehmigungWartetUndLegtKeineUeberschneidungAn() throws Exception {
        urlaub(76, 1, 2);
        ExecutorService fenster1 = Executors.newSingleThreadExecutor();
        ExecutorService fenster2 = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch gespeichert = new CountDownLatch(1);
            CountDownLatch weiter = new CountDownLatch(1);
            Future<?> erste = fenster1.submit(() -> transaktion.executeWithoutResult(status -> {
                urlaub(77, 21, 23);
                gespeichert.countDown();
                warte(weiter);
            }));
            assertThat(gespeichert.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> zweite = fenster2.submit(() -> urlaub(78, 22, 24));
            Thread.sleep(300);
            assertThat(zweite.isDone()).isFalse();

            weiter.countDown();
            erste.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> zweite.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(FachlicheException.class);
            assertThat(anzahlAbwesenheiten()).isEqualTo(2);
        } finally {
            fenster1.shutdownNow();
            fenster2.shutdownNow();
        }
    }

    private static void warte(CountDownLatch signal) {
        try {
            signal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
