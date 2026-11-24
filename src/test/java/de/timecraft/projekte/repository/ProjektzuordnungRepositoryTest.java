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
 * Primärschlüssel (Mitarbeiter + Projekt), Fremdschlüssel auf "projekt" und die Abfragen.
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
    private static final long MITARBEITER_2 = 12L;

    @Autowired
    private ProjektzuordnungRepository repository;

    @Autowired
    private ProjektRepository projektRepository;

    @Autowired
    private TestEntityManager entityManager;

    private long projektA;
    private long projektB;

    /** Vor jedem Test existieren zwei Projekte, auf die die Zuordnungen zeigen können. */
    @BeforeEach
    void legeProjekteAn() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate ziel = LocalDate.of(2026, 12, 31);
        projektA = projektRepository.save(new Projekt(2L, "Projekt A", null, start, ziel)).getProjektId();
        projektB = projektRepository.save(new Projekt(2L, "Projekt B", null, start, ziel)).getProjektId();
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
    void einMitarbeiterKannInMehrerenProjektenSein() {
        repository.save(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        repository.save(new Projektzuordnung(MITARBEITER_1, projektB, ZEIT));
        repository.flush();

        assertThat(repository.findByMitarbeiterId(MITARBEITER_1))
                .extracting(Projektzuordnung::getProjektId)
                .containsExactlyInAnyOrder(projektA, projektB);
    }

    @Test
    void mitarbeiterIstProProjektNurEinmalZugeordnet() {
        repository.save(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        repository.save(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT.plusDays(1)));
        repository.flush();

        assertThat(repository.findByProjektId(projektA)).hasSize(1);
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
        long nichtVorhanden = projektB + 1000;

        assertThatThrownBy(() -> repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, nichtVorhanden, ZEIT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void zuordnungOhneZeitpunktWirdAbgelehnt() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void liefertDasTeamEinesProjekts() {
        repository.save(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        repository.save(new Projektzuordnung(MITARBEITER_2, projektA, ZEIT));
        repository.save(new Projektzuordnung(MITARBEITER_1, projektB, ZEIT));
        repository.flush();

        assertThat(repository.findByProjektId(projektA))
                .extracting(Projektzuordnung::getMitarbeiterId)
                .containsExactlyInAnyOrder(MITARBEITER_1, MITARBEITER_2);
        assertThat(repository.findByProjektId(projektB))
                .extracting(Projektzuordnung::getMitarbeiterId)
                .containsExactly(MITARBEITER_1);
    }

    @Test
    void erkenntObMitarbeiterZugeordnetIst() {
        repository.saveAndFlush(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));

        assertThat(repository.existsByMitarbeiterIdAndProjektId(MITARBEITER_1, projektA)).isTrue();
        assertThat(repository.existsByMitarbeiterIdAndProjektId(MITARBEITER_1, projektB)).isFalse();
        assertThat(repository.existsByMitarbeiterIdAndProjektId(MITARBEITER_2, projektA)).isFalse();
    }

    @Test
    void entfernenLoeschtNurDieZuordnungUndNichtDasProjekt() {
        repository.save(new Projektzuordnung(MITARBEITER_1, projektA, ZEIT));
        repository.save(new Projektzuordnung(MITARBEITER_2, projektA, ZEIT));
        repository.flush();

        repository.deleteById(new Projektzuordnung.Schluessel(MITARBEITER_1, projektA));
        repository.flush();

        assertThat(repository.findByProjektId(projektA))
                .extracting(Projektzuordnung::getMitarbeiterId)
                .containsExactly(MITARBEITER_2);
        assertThat(projektRepository.findById(projektA)).isPresent();
    }
}
