package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.repository.ProjektRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die schreibende Schnittstelle AngebotspreisPflege mit einer echten Datenbank
 * (H2 im PostgreSQL-Modus). Die Spalte angebotspreis ist NUMERIC(10,2): höchstens 8 Vorkommastellen
 * und 2 Nachkommastellen, also höchstens 99999999.99.
 * <p>
 * Die api prüft keine Rechte (Vertrag), deshalb gibt es hier keine Rollentests. Nach jedem Aufruf wird der Wert
 * aus der Datenbank neu gelesen ({@link #preisInDerDatenbank}), damit der Test wirklich das Gespeicherte prüft.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AngebotspreisService.class)
class AngebotspreisServiceTest {

    private static final long UNBEKANNTE_ID = 123456L;

    @Autowired
    private AngebotspreisService service;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private long projektId;

    @BeforeEach
    void legeProjektAn() {
        Projekt projekt = new Projekt(99L, "Website Relaunch", "Neue Seite", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        projektId = repository.save(projekt).getProjektId();
    }

    // ---------- Normalfall ----------

    @Test
    void setztDenAngebotspreis() {
        service.setzeAngebotspreis(projektId, new BigDecimal("1500.50"));

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("1500.50");
    }

    @Test
    void einNeuerPreisUeberschreibtDenAlten() {
        service.setzeAngebotspreis(projektId, new BigDecimal("1500.50"));
        service.setzeAngebotspreis(projektId, new BigDecimal("2000.00"));

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("2000.00");
    }

    @Test
    void nullLoeschtDenAngebotspreis() {
        service.setzeAngebotspreis(projektId, new BigDecimal("1500.50"));

        service.setzeAngebotspreis(projektId, null);

        assertThat(preisInDerDatenbank()).isNull();
    }

    @Test
    void dieAnderenFelderDesProjektsBleibenUnveraendert() {
        service.setzeAngebotspreis(projektId, new BigDecimal("1500.50"));

        Projekt projekt = projektAusDerDatenbank();
        assertThat(projekt.getName()).isEqualTo("Website Relaunch");
        assertThat(projekt.getBeschreibung()).isEqualTo("Neue Seite");
        assertThat(projekt.getProjektleitungId()).isEqualTo(99L);
        assertThat(projekt.getStatus()).isEqualTo(ProjektStatus.AKTIV);
    }

    @Test
    void auchEinArchiviertesProjektDarfEinenPreisBekommen() {
        Projekt projekt = repository.findById(projektId).orElseThrow();
        projekt.setStatus(ProjektStatus.ARCHIVIERT);
        repository.save(projekt);

        service.setzeAngebotspreis(projektId, new BigDecimal("100.00"));

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("100.00");
    }

    // ---------- Grenzfälle ----------

    @Test
    void nullEuroIstErlaubt() {
        service.setzeAngebotspreis(projektId, BigDecimal.ZERO);

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("0.00");
    }

    @Test
    void derHoechstePassendePreisIstErlaubt() {
        service.setzeAngebotspreis(projektId, new BigDecimal("99999999.99"));

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("99999999.99");
    }

    @Test
    void mehrAlsZweiNachkommastellenWerdenKaufmaennischAufZweiGerundet() {
        service.setzeAngebotspreis(projektId, new BigDecimal("10.005"));
        assertThat(preisInDerDatenbank()).isEqualByComparingTo("10.01");

        service.setzeAngebotspreis(projektId, new BigDecimal("10.004"));
        assertThat(preisInDerDatenbank()).isEqualByComparingTo("10.00");
    }

    // ---------- Fehlerfälle: verständliche Meldung, nichts wird geändert ----------

    @Test
    void einNegativerPreisWirdAbgelehnt() {
        service.setzeAngebotspreis(projektId, new BigDecimal("500.00"));

        assertThatThrownBy(() -> service.setzeAngebotspreis(projektId, new BigDecimal("-0.01")))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Angebotspreis darf nicht negativ sein.");

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("500.00");
    }

    @Test
    void einZuGrosserPreisWirdAbgelehnt() {
        service.setzeAngebotspreis(projektId, new BigDecimal("500.00"));

        assertThatThrownBy(() -> service.setzeAngebotspreis(projektId, new BigDecimal("100000000.00")))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Angebotspreis ist zu groß (höchstens 99.999.999,99 Euro).");

        assertThat(preisInDerDatenbank()).isEqualByComparingTo("500.00");
    }

    @Test
    void einErstNachDemRundenZuGrosserPreisWirdAbgelehnt() {
        assertThatThrownBy(() -> service.setzeAngebotspreis(projektId, new BigDecimal("99999999.995")))
                .isInstanceOf(FachlicheException.class);

        assertThat(preisInDerDatenbank()).isNull();
    }

    @Test
    void eineUnbekannteProjektIdWirdAbgelehnt() {
        assertThatThrownBy(() -> service.setzeAngebotspreis(UNBEKANNTE_ID, new BigDecimal("100.00")))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Das Projekt wurde nicht gefunden.");
    }

    @Test
    void beiUnbekannterProjektIdWirdNichtsAngelegt() {
        long vorher = repository.count();

        assertThatThrownBy(() -> service.setzeAngebotspreis(UNBEKANNTE_ID, new BigDecimal("100.00")))
                .isInstanceOf(NichtGefundenException.class);

        assertThat(repository.count()).isEqualTo(vorher);
    }

    // ---------- Hilfsmethoden ----------

    /** Schreibt alles in die Datenbank, leert den Zwischenspeicher und liest das Projekt wirklich neu. */
    private Projekt projektAusDerDatenbank() {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(projektId).orElseThrow();
    }

    private BigDecimal preisInDerDatenbank() {
        return projektAusDerDatenbank().getAngebotspreis();
    }
}
