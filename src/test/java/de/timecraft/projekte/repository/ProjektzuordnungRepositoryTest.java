package de.timecraft.projekte.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.Projektzuordnung;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Tabelle "projektzuordnung" gegen eine echte (H2-)Datenbank:
 * Primärschlüssel (Mitarbeiter + Projekt), Fremdschlüssel auf "projekt" und Pflichtfelder.
 * Die Mitarbeiter-IDs sind frei gewählte Zahlen, denn die Mitarbeiter-Tabelle gehört zu Modul M1.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProjektzuordnungRepositoryTest {

    private static final LocalDateTime ZEIT = LocalDateTime.of(2026, 11, 16, 9, 30);
    private static final long MITARBEITER_1 = 11L;

    @Autowired
    private ProjektzuordnungRepository repository;

    @Autowired
    private ProjektRepository projektRepository;

    @Autowired
    private TestEntityManager entityManager;

    private long projektA;

    /** Vor jedem Test existiert ein Projekt, auf das die Zuordnungen zeigen können. */
    @BeforeEach
    void legeProjekteAn() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate ziel = LocalDate.of(2026, 12, 31);
        projektA = projektRepository.save(new Projekt(2L, "Projekt A", null, start, ziel)).getProjektId();
    }

    @Test
    void speichertZuordnungUndLiestSieAusDerDatenbankWieder() {
        repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        entityManager.clear();

        Projektzuordnung gelesen = repository
                .findById(new Projektzuordnung.Schluessel(MITARBEITER_1, projektA)).orElseThrow();

        assertThat(gelesen.getMitarbeiterId()).isEqualTo(MITARBEITER_1);
        assertThat(gelesen.getProjektId()).isEqualTo(projektA);
        assertThat(gelesen.getZugeordnetAm()).isEqualTo(ZEIT);
    }

    @Test
    void primaerschluesselVerhindertDoppelteZuordnungInDerDatenbank() {
        entityManager.persistAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        entityManager.clear();

        assertThatThrownBy(() -> entityManager.persistAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void zuordnungAufNichtVorhandenesProjektWirdAbgelehnt() {
        long nichtVorhanden = projektA + 1000;

        assertThatThrownBy(() -> repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, nichtVorhanden, ZEIT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zuordnungOhneZeitpunktWirdAbgelehnt() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
