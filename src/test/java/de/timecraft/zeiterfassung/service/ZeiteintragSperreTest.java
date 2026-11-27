package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/**
 * Zweites Browserfenster (FA-M2-02, QA-M2-01) mit echter Datenbank: Waehrend Fenster 1 einstempelt und
 * die Sperre haelt, wartet Fenster 2 und legt danach keinen zweiten laufenden Eintrag an.
 * Ohne Testtransaktion, damit beide Fenster eigene Transaktionen haben.
 *
 * @author Noah Cipa
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ ZeiteintragService.class, PausenRegelService.class, ZugriffsPruefung.class,
        AbwesenheitService.class, FeiertagService.class,
        ZeiteintragSperreTest.Konfiguration.class })
class ZeiteintragSperreTest {

    private static final Instant JETZT = Instant.parse("2026-10-22T07:30:00Z");

    @TestConfiguration
    static class Konfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(JETZT, ZoneId.of("Europe/Berlin"));
        }

        @Bean
        MitarbeiterAuskunft mitarbeiterAuskunft() {
            return new FakeMitarbeiterAuskunft(Rolle.MITARBEITER);
        }

        @Bean
        ProjektAuskunft projektAuskunft() {
            return new FakeProjektAuskunft();
        }
    }

    @Autowired
    private ZeiteintragService service;

    @Autowired
    private ZeiteintragRepository repository;

    @Autowired
    private TransactionTemplate transaktion;

    private final ExecutorService fenster1 = Executors.newSingleThreadExecutor();
    private final ExecutorService fenster2 = Executors.newSingleThreadExecutor();

    @AfterEach
    void aufraeumen() {
        fenster1.shutdownNow();
        fenster2.shutdownNow();
        repository.deleteAll();
    }

    @Test
    void zweitesFensterWartetAufDieSperreUndStempeltNichtDoppelt() throws Exception {
        // Gestern: abgeschlossener Eintrag, dessen Zeile gesperrt wird
        Zeiteintrag gestern = new Zeiteintrag(JONAS_BECKER, JETZT.minusSeconds(24 * 3600), Standort.BUERO, null);
        gestern.ausstempeln(JETZT.minusSeconds(20 * 3600));
        repository.save(gestern);

        CountDownLatch gesperrt = new CountDownLatch(1);
        CountDownLatch weiter = new CountDownLatch(1);
        Future<?> erstesFenster = fenster1.submit(() -> transaktion.executeWithoutResult(status -> {
            service.sperreMitarbeiter(JONAS_BECKER);
            gesperrt.countDown();
            warte(weiter);
            repository.save(new Zeiteintrag(JONAS_BECKER, JETZT, Standort.HOMEOFFICE, null));
        }));
        assertThat(gesperrt.await(5, TimeUnit.SECONDS)).isTrue();

        Future<RuntimeException> zweitesFenster = fenster2.submit(() -> {
            try {
                service.einstempeln(JONAS_BECKER, Rolle.MITARBEITER, Standort.BUERO, null);
                return null;
            } catch (RuntimeException fehler) {
                return fehler;
            }
        });
        // Fenster 2 haengt an der Sperre, solange Fenster 1 nicht gespeichert hat
        Thread.sleep(300);
        assertThat(zweitesFenster.isDone()).isFalse();

        weiter.countDown();
        erstesFenster.get(5, TimeUnit.SECONDS);

        assertThat(zweitesFenster.get(5, TimeUnit.SECONDS))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Sie sind bereits eingestempelt.");
        assertThat(repository.findAll()).filteredOn(eintrag -> eintrag.getEnde() == null)
                .extracting(Zeiteintrag::getStandort).containsExactly(Standort.HOMEOFFICE);
    }

    private static void warte(CountDownLatch signal) {
        try {
            signal.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
