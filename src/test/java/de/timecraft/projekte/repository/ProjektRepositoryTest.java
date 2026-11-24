package de.timecraft.projekte.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Tabelle "projekt" gegen eine echte (H2-)Datenbank.
 * Nach dem Speichern wird der Zwischenspeicher von JPA geleert (flush + clear),
 * damit das Projekt wirklich aus der Datenbank gelesen wird und nicht aus dem Speicher.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProjektRepositoryTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate ZIEL = LocalDate.of(2026, 12, 31);

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void speichertProjektMitVomSystemVergebenerIdUndLiestAlleFelderWiederEin() {
        Projekt neu = new Projekt(2L, "Website", "Neue Firmenwebsite", START, ZIEL);

        Projekt gespeichert = repository.saveAndFlush(neu);
        entityManager.clear();
        Projekt gelesen = repository.findById(gespeichert.getProjektId()).orElseThrow();

        assertThat(gelesen.getProjektId()).isNotNull();
        assertThat(gelesen.getProjektleitungId()).isEqualTo(2L);
        assertThat(gelesen.getName()).isEqualTo("Website");
        assertThat(gelesen.getBeschreibung()).isEqualTo("Neue Firmenwebsite");
        assertThat(gelesen.getStartdatum()).isEqualTo(START);
        assertThat(gelesen.getZieldatum()).isEqualTo(ZIEL);
    }

    @Test
    void neuesProjektIstAktivUndHatNochKeinenAngebotspreis() {
        Projekt gespeichert = repository.saveAndFlush(new Projekt(2L, "Website", null, START, ZIEL));
        entityManager.clear();
        Projekt gelesen = repository.findById(gespeichert.getProjektId()).orElseThrow();

        assertThat(gelesen.getStatus()).isEqualTo(ProjektStatus.AKTIV);
        assertThat(gelesen.getAngebotspreis()).isNull();
        assertThat(gelesen.getBeschreibung()).isNull();
    }

    @Test
    void gleicheNamenSindErlaubtUndBekommenVerschiedeneIds() {
        Projekt erstes = repository.saveAndFlush(new Projekt(2L, "Umbau", null, START, ZIEL));
        Projekt zweites = repository.saveAndFlush(new Projekt(2L, "Umbau", null, START, ZIEL));

        assertThat(erstes.getProjektId()).isNotEqualTo(zweites.getProjektId());
    }

    @Test
    void speichertStatusArchiviertAlsText() {
        Projekt projekt = new Projekt(2L, "Altprojekt", null, START, ZIEL);
        projekt.setStatus(ProjektStatus.ARCHIVIERT);

        Projekt gespeichert = repository.saveAndFlush(projekt);
        entityManager.clear();

        assertThat(repository.findById(gespeichert.getProjektId()).orElseThrow().getStatus())
                .isEqualTo(ProjektStatus.ARCHIVIERT);
    }

    @Test
    void speichertAngebotspreisMitZweiNachkommastellen() {
        Projekt projekt = new Projekt(2L, "Kalkulation", null, START, ZIEL);
        projekt.setAngebotspreis(new BigDecimal("1234.5"));

        Projekt gespeichert = repository.saveAndFlush(projekt);
        entityManager.clear();
        Projekt gelesen = repository.findById(gespeichert.getProjektId()).orElseThrow();

        assertThat(gelesen.getAngebotspreis()).isEqualByComparingTo("1234.50");
    }

    @Test
    void nameDarfHoechstens100ZeichenHaben() {
        Projekt genau100 = new Projekt(2L, "a".repeat(100), null, START, ZIEL);
        Projekt zuLang = new Projekt(2L, "a".repeat(101), null, START, ZIEL);

        assertThat(repository.saveAndFlush(genau100).getProjektId()).isNotNull();
        assertThatThrownBy(() -> repository.saveAndFlush(zuLang))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pflichtfelderDuerfenNichtLeerSein() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Projekt(2L, null, null, START, ZIEL)))
                .as("Name fehlt")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(new Projekt(2L, "Ohne Start", null, null, ZIEL)))
                .as("Startdatum fehlt")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> repository.saveAndFlush(new Projekt(2L, "Ohne Ziel", null, START, null)))
                .as("Zieldatum fehlt")
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
