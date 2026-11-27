package de.timecraft.projekte.repository;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Filterabfrage der Projektliste mit einem kleinen festen Datensatz.
 * <p>
 * Die vier Beispielprojekte (Mitarbeiter 11 und 12 sind frei gewählte Zahlen):
 * <pre>
 * Name                     Start       Ziel        Status       zugeordnet
 * Website Relaunch         01.01.2026  30.06.2026  AKTIV        11, 12
 * Umbau Lager              01.03.2026  31.12.2026  AKTIV        11
 * Altprojekt WEBSITE 2020  01.01.2020  31.12.2020  ARCHIVIERT   12
 * 50% Rabatt-Aktion        01.07.2026  30.09.2026  AKTIV        niemand
 * </pre>
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProjektRepositoryFilterTest {

    private static final long MITARBEITER_11 = 11L;
    private static final long MITARBEITER_12 = 12L;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    private long websiteId;
    private long umbauId;
    private long altprojektId;
    private long rabattId;

    @BeforeEach
    void legeBeispieldatenAn() {
        websiteId = speichere("Website Relaunch", "2026-01-01", "2026-06-30", ProjektStatus.AKTIV);
        umbauId = speichere("Umbau Lager", "2026-03-01", "2026-12-31", ProjektStatus.AKTIV);
        altprojektId = speichere("Altprojekt WEBSITE 2020", "2020-01-01", "2020-12-31", ProjektStatus.ARCHIVIERT);
        rabattId = speichere("50% Rabatt-Aktion", "2026-07-01", "2026-09-30", ProjektStatus.AKTIV);

        ordneZu(MITARBEITER_11, websiteId);
        ordneZu(MITARBEITER_12, websiteId);
        ordneZu(MITARBEITER_11, umbauId);
        ordneZu(MITARBEITER_12, altprojektId);
        repository.flush();
    }

    @Test
    void ohneFilterKommenAlleProjekteSortiertNachProjektId() {
        List<Projekt> ergebnis = repository.filtern(new ProjektFilter());

        assertThat(ids(ergebnis)).containsExactly(websiteId, umbauId, altprojektId, rabattId);
    }

    @Test
    void filterNachMitarbeiterZeigtNurSeineProjekte() {
        assertThat(ids(mitarbeiter(MITARBEITER_11))).containsExactly(websiteId, umbauId);
        assertThat(ids(mitarbeiter(MITARBEITER_12))).containsExactly(websiteId, altprojektId);
    }

    @Test
    void filterNachUnbekanntemMitarbeiterLiefertNichts() {
        assertThat(mitarbeiter(99L)).isEmpty();
    }

    @Test
    void filterNachStatus() {
        ProjektFilter filter = new ProjektFilter();

        filter.setStatus(ProjektStatus.ARCHIVIERT);
        assertThat(ids(repository.filtern(filter))).containsExactly(altprojektId);

        filter.setStatus(ProjektStatus.AKTIV);
        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId, umbauId, rabattId);
    }

    @Test
    void nameEnthaeltTextOhneBeachtungVonGrossUndKleinschreibung() {
        assertThat(ids(name("website"))).containsExactly(websiteId, altprojektId);
        assertThat(ids(name("WEBSITE"))).containsExactly(websiteId, altprojektId);
        assertThat(ids(name("relaunch"))).containsExactly(websiteId);
        assertThat(ids(name("lager"))).containsExactly(umbauId);
    }

    @Test
    void nameOhneTrefferLiefertNichts() {
        assertThat(name("gibtEsNicht")).isEmpty();
    }

    @Test
    void sonderzeichenImNamenWerdenWoertlichGesucht() {
        // "%" und "_" sind keine Platzhalter: Nur "50% Rabatt-Aktion" enthält wirklich ein Prozentzeichen.
        assertThat(ids(name("%"))).containsExactly(rabattId);
        assertThat(ids(name("50%"))).containsExactly(rabattId);
        assertThat(name("_")).isEmpty();
    }

    @Test
    void startdatumVonUndBisNehmenDieGrenztageMit() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStartVon(LocalDate.of(2026, 1, 1));
        filter.setStartBis(LocalDate.of(2026, 3, 1));

        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId, umbauId);
    }

    @Test
    void zieldatumVonUndBisNehmenDieGrenztageMit() {
        ProjektFilter filter = new ProjektFilter();
        filter.setZielVon(LocalDate.of(2026, 6, 30));
        filter.setZielBis(LocalDate.of(2026, 9, 30));

        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId, rabattId);
    }

    @Test
    void startUndZielZeitraumWirkenGemeinsam() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStartVon(LocalDate.of(2026, 1, 1));
        filter.setZielBis(LocalDate.of(2026, 6, 30));

        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId);
    }

    @Test
    void mehrereFilterWirkenGemeinsam() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_11);
        filter.setStatus(ProjektStatus.AKTIV);
        filter.setName("umbau");
        assertThat(ids(repository.filtern(filter))).containsExactly(umbauId);

        filter.setMitarbeiterId(MITARBEITER_12);
        filter.setName(null);
        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId);
    }

    @Test
    void kombinationOhneUebereinstimmungLiefertNichts() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_12);
        filter.setStatus(ProjektStatus.AKTIV);
        filter.setName("alt");

        assertThat(repository.filtern(filter)).isEmpty();
    }

    @Test
    void alleFilterGleichzeitig() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_11);
        filter.setStatus(ProjektStatus.AKTIV);
        filter.setStartVon(LocalDate.of(2026, 1, 1));
        filter.setStartBis(LocalDate.of(2026, 1, 1));
        filter.setZielVon(LocalDate.of(2026, 6, 1));
        filter.setZielBis(LocalDate.of(2026, 6, 30));
        filter.setName("RELAUNCH");

        assertThat(ids(repository.filtern(filter))).containsExactly(websiteId);
    }

    private long speichere(String name, String start, String ziel, ProjektStatus status) {
        Projekt projekt = new Projekt(2L, name, null, LocalDate.parse(start), LocalDate.parse(ziel));
        projekt.setStatus(status);
        return repository.save(projekt).getProjektId();
    }

    private void ordneZu(long mitarbeiterId, long projektId) {
        zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));
    }

    private List<Projekt> mitarbeiter(long mitarbeiterId) {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(mitarbeiterId);
        return repository.filtern(filter);
    }

    private List<Projekt> name(String text) {
        ProjektFilter filter = new ProjektFilter();
        filter.setName(text);
        return repository.filtern(filter);
    }

    private List<Long> ids(List<Projekt> projekte) {
        return projekte.stream().map(Projekt::getProjektId).toList();
    }
}
