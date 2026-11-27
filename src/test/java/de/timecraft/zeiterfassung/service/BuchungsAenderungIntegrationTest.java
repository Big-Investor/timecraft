package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/**
 * BuchungsAenderung mit echter Datenbank: Buchung und Protokoll werden zusammen gespeichert oder gar nicht.
 * Ohne Testtransaktion, damit Commit und Rollback wirklich stattfinden. Gelesen wird ueber die api.
 *
 * @author Noah Cipa
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ BuchungsAenderungService.class, BuchungsAuskunftService.class, ZeiteintragService.class,
        PausenRegelService.class, ZugriffsPruefung.class, AbwesenheitService.class, FeiertagService.class,
        IntegrationsTestKonfiguration.class })
class BuchungsAenderungIntegrationTest {

    @Autowired
    private BuchungsAenderung aenderung;

    @Autowired
    private BuchungsAuskunft auskunft;

    @Autowired
    private ZeiteintragService zeiteintragService;

    @Autowired
    private ZeiteintragRepository repository;

    @Autowired
    private TransactionTemplate transaktion;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void leeren() {
        IntegrationsTestKonfiguration.leereTabellen(jdbc);
    }

    @AfterEach
    void aufraeumen() {
        jdbc.execute("alter table aenderungsprotokoll drop constraint if exists testfehler_protokoll");
        jdbc.execute("alter table zeiteintrag drop constraint if exists testfehler_buchung");
        IntegrationsTestKonfiguration.leereTabellen(jdbc);
    }

    private static Instant berlin(int tag, int stunde) {
        return LocalDateTime.of(2026, 11, tag, stunde, 0).atZone(ZoneId.of("Europe/Berlin")).toInstant();
    }

    private long buchung(int tag, int vonStunde, int bisStunde) {
        Zeiteintrag eintrag = new Zeiteintrag(JONAS_BECKER, berlin(tag, vonStunde), Standort.BUERO, null);
        eintrag.ausstempeln(berlin(tag, bisStunde));
        return repository.save(eintrag).getId();
    }

    private BuchungInfo gelesen(long id) {
        return auskunft.buchung(id).orElseThrow();
    }

    private int anzahl(String tabelle) {
        return jdbc.queryForObject("select count(*) from " + tabelle, Integer.class);
    }

    /** Laesst die Datenbank selbst beim Speichern scheitern, ohne Attrappe. */
    private void datenbankLehntAb(String tabelle, String name, String bedingung) {
        jdbc.execute("alter table " + tabelle + " add constraint " + name + " check (" + bedingung + ")");
    }

    private void unveraendert(long id) {
        BuchungInfo buchung = gelesen(id);
        assertThat(buchung.beginn()).isEqualTo(berlin(9, 8));
        assertThat(buchung.ende()).isEqualTo(berlin(9, 12));
        assertThat(buchung.standort()).isEqualTo(Standort.BUERO);
        assertThat(buchung.projektId()).isNull();
        assertThat(buchung.geaendert()).isFalse();
        assertThat(auskunft.historie(id)).isEmpty();
    }

    /** Aendert Beginn, Ende, Standort und Projekt der Buchung vom 09.11., 8 bis 12 Uhr. */
    private void aendereAlles(long id) {
        aenderung.aendern(id, berlin(9, 9), berlin(9, 14), Standort.UNTERWEGS, 1024L,
                AenderungsArt.KORREKTUR, PAUL_HARTMANN, "Beim Kunden gewesen");
    }

    @Test
    void aendernSpeichertBuchungUndProtokoll() {
        long id = buchung(9, 8, 12);

        aendereAlles(id);

        BuchungInfo buchung = gelesen(id);
        assertThat(buchung.beginn()).isEqualTo(berlin(9, 9));
        assertThat(buchung.ende()).isEqualTo(berlin(9, 14));
        assertThat(buchung.standort()).isEqualTo(Standort.UNTERWEGS);
        assertThat(buchung.projektId()).isEqualTo(1024L);
        assertThat(buchung.geaendert()).isTrue();
        assertThat(auskunft.historie(id))
                .extracting(AenderungInfo::feld, AenderungInfo::alterWert, AenderungInfo::neuerWert)
                .containsExactlyInAnyOrder(
                        tuple("beginn", "09.11.2026 08:00", "09.11.2026 09:00"),
                        tuple("ende", "09.11.2026 12:00", "09.11.2026 14:00"),
                        tuple("standort", "BUERO", "UNTERWEGS"),
                        tuple("projekt", null, "1024"));
        assertThat(auskunft.historie(id)).allSatisfy(eintrag -> {
            assertThat(eintrag.zeitpunkt()).isEqualTo(IntegrationsTestKonfiguration.JETZT);
            assertThat(eintrag.geaendertVonMitarbeiterId()).isEqualTo(PAUL_HARTMANN);
            assertThat(eintrag.art()).isEqualTo(AenderungsArt.KORREKTUR);
            assertThat(eintrag.begruendung()).isEqualTo("Beim Kunden gewesen");
        });
    }

    @Test
    void scheitertEinProtokolleintragBleibtNichtsGespeichert() {
        long id = buchung(9, 8, 12);
        // beginn und ende werden vorher protokolliert und muessen wieder verschwinden
        datenbankLehntAb("aenderungsprotokoll", "testfehler_protokoll", "feld <> 'standort'");

        assertThatThrownBy(() -> aendereAlles(id)).isInstanceOf(DataIntegrityViolationException.class);

        unveraendert(id);
    }

    @Test
    void scheitertDieBuchungVerschwindetAuchDasProtokoll() {
        long id = buchung(9, 8, 12);
        datenbankLehntAb("zeiteintrag", "testfehler_buchung", "standort <> 'UNTERWEGS'");

        assertThatThrownBy(() -> aendereAlles(id)).isInstanceOf(DataIntegrityViolationException.class);

        unveraendert(id);
    }

    @Test
    void nachtragenSpeichertBuchungUndProtokoll() {
        long id = aenderung.nachtragen(JONAS_BECKER, berlin(9, 8), berlin(9, 12), Standort.HOMEOFFICE, 1024L,
                PAUL_HARTMANN, "Vergessen");

        BuchungInfo buchung = gelesen(id);
        assertThat(buchung.mitarbeiterId()).isEqualTo(JONAS_BECKER);
        assertThat(buchung.nettoMinuten()).isEqualTo(240);
        assertThat(buchung.geaendert()).isTrue();
        assertThat(auskunft.historie(id))
                .extracting(AenderungInfo::art, AenderungInfo::feld, AenderungInfo::neuerWert)
                .containsExactly(tuple(AenderungsArt.NACHTRAG, "eintrag", "09.11.2026 08:00 bis 09.11.2026 12:00"));
    }

    @Test
    void scheitertDasProtokollWirdKeineBuchungNachgetragen() {
        datenbankLehntAb("aenderungsprotokoll", "testfehler_protokoll", "feld <> 'eintrag'");

        assertThatThrownBy(() -> aenderung.nachtragen(JONAS_BECKER, berlin(9, 8), berlin(9, 12),
                Standort.BUERO, null, PAUL_HARTMANN, "Vergessen"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(anzahl("zeiteintrag")).isZero();
        assertThat(anzahl("aenderungsprotokoll")).isZero();
    }

    @Test
    void scheitertDerAufruferWerdenAenderungUndNachtragZurueckgerollt() {
        long id = buchung(9, 8, 12);

        assertThatThrownBy(() -> transaktion.executeWithoutResult(status -> {
            aendereAlles(id);
            aenderung.nachtragen(JONAS_BECKER, berlin(6, 8), berlin(6, 12), Standort.BUERO, null,
                    PAUL_HARTMANN, "Vergessen");
            throw new IllegalStateException("Fehler im aufrufenden Modul");
        })).isInstanceOf(IllegalStateException.class);

        unveraendert(id);
        assertThat(anzahl("zeiteintrag")).isEqualTo(1);
        assertThat(anzahl("aenderungsprotokoll")).isZero();
    }

    @Test
    void ueberschneidungWirdBeimAendernAbgelehntUndNichtsGespeichert() {
        long id = buchung(9, 8, 12);
        buchung(9, 13, 15);

        assertThatThrownBy(() -> aendereAlles(id))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Eintrag überschneidet sich mit Ihrem Eintrag vom 09.11.2026 13:00 Uhr.");

        unveraendert(id);
    }

    @Test
    void ueberschneidungWirdBeimNachtragenAbgelehntUndNichtsGespeichert() {
        buchung(9, 8, 12);

        assertThatThrownBy(() -> aenderung.nachtragen(JONAS_BECKER, berlin(9, 11), berlin(9, 14),
                Standort.BUERO, null, PAUL_HARTMANN, "Doppelt"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Eintrag überschneidet sich mit Ihrem Eintrag vom 09.11.2026 08:00 Uhr.");

        assertThat(anzahl("zeiteintrag")).isEqualTo(1);
        assertThat(anzahl("aenderungsprotokoll")).isZero();
    }

    @Test
    void laufendeBuchungWirdNichtGeaendert() {
        long id = repository.save(new Zeiteintrag(JONAS_BECKER, berlin(10, 8), Standort.BUERO, null)).getId();

        assertThatThrownBy(() -> aenderung.aendern(id, berlin(10, 8), berlin(10, 11), Standort.BUERO, null,
                AenderungsArt.AENDERUNG, PAUL_HARTMANN, "Ende"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Eine laufende Buchung kann nicht geändert werden.");

        assertThat(gelesen(id).laeuft()).isTrue();
        assertThat(auskunft.historie(id)).isEmpty();
    }

    @Test
    void unbekannteBuchungWirdGemeldet() {
        assertThatThrownBy(() -> aendereAlles(999_999L))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Die Buchung wurde nicht gefunden.");
    }

    /**
     * Ein zweites Fenster aendert dieselbe Buchung, waehrend das erste noch speichert. Es wartet auf die Sperre
     * und liest die Buchung erst danach, damit im Protokoll der wirklich letzte Stand als alter Wert steht.
     */
    @Test
    void gleichzeitigeAenderungProtokolliertDenStandNachDerSperre() throws Exception {
        long id = buchung(9, 8, 12);
        ExecutorService fenster1 = Executors.newSingleThreadExecutor();
        ExecutorService fenster2 = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch gesperrt = new CountDownLatch(1);
            CountDownLatch weiter = new CountDownLatch(1);
            Future<?> erstes = fenster1.submit(() -> transaktion.executeWithoutResult(status -> {
                zeiteintragService.sperreMitarbeiter(JONAS_BECKER);
                gesperrt.countDown();
                warte(weiter);
                aenderung.aendern(id, berlin(9, 8), berlin(9, 13), Standort.BUERO, null,
                        AenderungsArt.AENDERUNG, PAUL_HARTMANN, "Erste");
            }));
            assertThat(gesperrt.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> zweites = fenster2.submit(() -> aenderung.aendern(id, berlin(9, 8), berlin(9, 14),
                    Standort.BUERO, null, AenderungsArt.AENDERUNG, PAUL_HARTMANN, "Zweite"));
            Thread.sleep(300);
            assertThat(zweites.isDone()).isFalse();

            weiter.countDown();
            erstes.get(5, TimeUnit.SECONDS);
            zweites.get(5, TimeUnit.SECONDS);
        } finally {
            fenster1.shutdownNow();
            fenster2.shutdownNow();
        }

        assertThat(auskunft.historie(id))
                .extracting(AenderungInfo::begruendung, AenderungInfo::alterWert, AenderungInfo::neuerWert)
                .containsExactlyInAnyOrder(
                        tuple("Erste", "09.11.2026 12:00", "09.11.2026 13:00"),
                        tuple("Zweite", "09.11.2026 13:00", "09.11.2026 14:00"));
        assertThat(gelesen(id).ende()).isEqualTo(berlin(9, 14));
    }

    private static void warte(CountDownLatch signal) {
        try {
            signal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
