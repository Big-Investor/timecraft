package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prüft die Projektliste (ProjektService.projekteSehen) je Rolle mit einer echten Datenbank
 * (H2 im PostgreSQL-Modus). Damit ist QA-M3-01 belegt: Jede Rolle sieht genau die erlaubten Projekte.
 * <p>
 * Die vier Beispielprojekte (die Zahlen 11, 12, 13 sind frei gewählte Mitarbeiter-IDs):
 * <pre>
 * Name                     Start       Ziel        Status       zugeordnet
 * Website Relaunch         01.01.2026  30.06.2026  AKTIV        11, 12
 * Umbau Lager              01.03.2026  31.12.2026  AKTIV        11
 * Altprojekt WEBSITE 2020  01.01.2020  31.12.2020  ARCHIVIERT   12
 * Rabatt-Aktion            01.07.2026  30.09.2026  AKTIV        niemand
 * </pre>
 * Admin hat die ID 3, die Projektleitung die ID 2 (sie ist hier nur Besucherin: Die Projekte
 * gehören einer anderen Projektleitung, die Liste zeigt ja alle aktiven Projekte).
 * Mitarbeiter 13 ist keinem Projekt zugeordnet.
 *
 * @author Tim Stukenbröker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ProjektService.class, ProjektBerechtigung.class, FesteUhr.class})
class ProjektServiceListeTest {

    private static final long ADMIN_ID = 3L;
    private static final long PROJEKTLEITUNG_ID = 2L;
    private static final long MITARBEITER_11 = 11L;
    private static final long MITARBEITER_12 = 12L;
    private static final long MITARBEITER_13 = 13L;
    private static final long EIGENTLICHE_PROJEKTLEITUNG_ID = 99L;

    @Autowired
    private ProjektService service;

    @Autowired
    private ProjektRepository repository;

    @Autowired
    private ProjektzuordnungRepository zuordnungRepository;

    @MockitoBean
    private MitarbeiterAuskunft mitarbeiterAuskunft;

    private long websiteId;
    private long umbauId;
    private long altprojektId;
    private long rabattId;

    @BeforeEach
    void legeBeispieldatenAn() {
        websiteId = speichere("Website Relaunch", "2026-01-01", "2026-06-30", ProjektStatus.AKTIV);
        umbauId = speichere("Umbau Lager", "2026-03-01", "2026-12-31", ProjektStatus.AKTIV);
        altprojektId = speichere("Altprojekt WEBSITE 2020", "2020-01-01", "2020-12-31", ProjektStatus.ARCHIVIERT);
        rabattId = speichere("Rabatt-Aktion", "2026-07-01", "2026-09-30", ProjektStatus.AKTIV);

        ordneZu(MITARBEITER_11, websiteId);
        ordneZu(MITARBEITER_12, websiteId);
        ordneZu(MITARBEITER_11, umbauId);
        ordneZu(MITARBEITER_12, altprojektId);
    }

    // ---------- Sichtbarkeit je Rolle (QA-M3-01) ----------

    @Test
    void adminSiehtAlleProjekteAuchArchivierte() {
        assertThat(ids(admin(new ProjektFilter())))
                .containsExactly(websiteId, umbauId, altprojektId, rabattId);
    }

    @Test
    void projektleitungSiehtAlleAktivenProjekteAberKeinArchiviertes() {
        assertThat(ids(projektleitung(new ProjektFilter())))
                .containsExactly(websiteId, umbauId, rabattId);
    }

    @Test
    void projektleitungSiehtAuchProjekteDieSieNichtVerantwortet() {
        List<Projekt> liste = service.projekteSehen(EIGENTLICHE_PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, new ProjektFilter());

        assertThat(ids(liste)).containsExactly(websiteId, umbauId, rabattId);
    }

    @Test
    void mitarbeiterSiehtNurSeineZugeordnetenProjekte() {
        assertThat(ids(mitarbeiter(MITARBEITER_11, new ProjektFilter()))).containsExactly(websiteId, umbauId);
    }

    @Test
    void mitarbeiterSiehtAuchEinArchiviertesProjektDemErZugeordnetIst() {
        assertThat(ids(mitarbeiter(MITARBEITER_12, new ProjektFilter()))).containsExactly(websiteId, altprojektId);
    }

    @Test
    void mitarbeiterOhneZuordnungSiehtKeinProjekt() {
        assertThat(mitarbeiter(MITARBEITER_13, new ProjektFilter())).isEmpty();
    }

    @Test
    void nichtAngemeldeteBekommenKeineListe() {
        assertThatThrownBy(() -> service.projekteSehen(0L, null, new ProjektFilter()))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    // ---------- Filter einzeln (innerhalb der erlaubten Projekte) ----------

    @Test
    void namensfilterIgnoriertGrossUndKleinschreibung() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("wEbSiTe");

        assertThat(ids(admin(filter))).containsExactly(websiteId, altprojektId);
    }

    @Test
    void namensfilterBleibtInnerhalbDerErlaubtenProjekte() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("website");

        assertThat(ids(projektleitung(filter))).containsExactly(websiteId);
        assertThat(ids(mitarbeiter(MITARBEITER_11, filter))).containsExactly(websiteId);
        assertThat(ids(mitarbeiter(MITARBEITER_12, filter))).containsExactly(websiteId, altprojektId);
    }

    @Test
    void statusfilterAktiv() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStatus(ProjektStatus.AKTIV);

        assertThat(ids(admin(filter))).containsExactly(websiteId, umbauId, rabattId);
    }

    @Test
    void statusfilterArchiviertZeigtDemAdminDasArchivierteProjekt() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStatus(ProjektStatus.ARCHIVIERT);

        assertThat(ids(admin(filter))).containsExactly(altprojektId);
    }

    @Test
    void statusfilterArchiviertZeigtDerProjektleitungNichts() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStatus(ProjektStatus.ARCHIVIERT);

        assertThat(projektleitung(filter)).isEmpty();
    }

    @Test
    void statusfilterArchiviertZeigtDemZugeordnetenMitarbeiterNurSeinArchiviertesProjekt() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStatus(ProjektStatus.ARCHIVIERT);

        assertThat(ids(mitarbeiter(MITARBEITER_12, filter))).containsExactly(altprojektId);
        assertThat(mitarbeiter(MITARBEITER_11, filter)).isEmpty();
    }

    @Test
    void startdatumVonUndBisNehmenDenGrenztagMit() {
        ProjektFilter filter = new ProjektFilter();
        filter.setStartVon(LocalDate.of(2026, 3, 1));
        filter.setStartBis(LocalDate.of(2026, 7, 1));

        assertThat(ids(admin(filter))).containsExactly(umbauId, rabattId);
    }

    @Test
    void zieldatumVonUndBisNehmenDenGrenztagMit() {
        ProjektFilter filter = new ProjektFilter();
        filter.setZielVon(LocalDate.of(2026, 6, 30));
        filter.setZielBis(LocalDate.of(2026, 9, 30));

        assertThat(ids(admin(filter))).containsExactly(websiteId, rabattId);
    }

    @Test
    void mitarbeiterfilterZeigtDemAdminDieProjekteDieserPerson() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_12);

        assertThat(ids(admin(filter))).containsExactly(websiteId, altprojektId);
    }

    @Test
    void mitarbeiterfilterZeigtDerProjektleitungNurAktiveProjekteDieserPerson() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_12);

        assertThat(ids(projektleitung(filter))).containsExactly(websiteId);
    }

    // ---------- Mitarbeiterfilter bei der Rolle Mitarbeiter: wirkt zusätzlich zur Sichtbarkeit ----------

    @Test
    void mitarbeiterfilterMitEigenerIdAendertFuerEinenMitarbeiterNichts() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_11);

        assertThat(ids(mitarbeiter(MITARBEITER_11, filter))).containsExactly(websiteId, umbauId);
    }

    @Test
    void mitarbeiterfilterMitFremdemMitarbeiterZeigtNurGemeinsameProjekte() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_12);

        assertThat(ids(mitarbeiter(MITARBEITER_11, filter))).containsExactly(websiteId);
    }

    @Test
    void mitarbeiterSiehtMitDemMitarbeiterfilterNieFremdeProjekte() {
        ProjektFilter filter = new ProjektFilter();
        filter.setMitarbeiterId(MITARBEITER_11);

        assertThat(mitarbeiter(MITARBEITER_13, filter)).isEmpty();
    }

    // ---------- Filter kombiniert, Zurücksetzen, ohne Treffer ----------

    @Test
    void mehrereFilterWirkenGemeinsam() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("website");
        filter.setStatus(ProjektStatus.AKTIV);
        filter.setStartVon(LocalDate.of(2026, 1, 1));
        filter.setMitarbeiterId(MITARBEITER_12);

        assertThat(ids(admin(filter))).containsExactly(websiteId);
    }

    @Test
    void kombinierteFilterOhneGemeinsamesProjektErgebenEineLeereListe() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("website");
        filter.setStatus(ProjektStatus.AKTIV);
        filter.setZielVon(LocalDate.of(2027, 1, 1));

        assertThat(admin(filter)).isEmpty();
    }

    @Test
    void einNeuerFilterSetztAllesZurueckUndZeigtWiederAlleErlaubtenProjekte() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("website");
        filter.setStatus(ProjektStatus.ARCHIVIERT);
        assertThat(ids(admin(filter))).containsExactly(altprojektId);

        assertThat(ids(admin(new ProjektFilter()))).hasSize(4);
    }

    @Test
    void keinFilterNullFuehrtZurVollenListe() {
        assertThat(ids(service.projekteSehen(ADMIN_ID, Rolle.ADMIN, null))).hasSize(4);
    }

    @Test
    void einLeererNamensfilterFiltertNichts() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("   ");

        assertThat(ids(admin(filter))).hasSize(4);
    }

    @Test
    void dieProjektlisteIstNachProjektIdSortiert() {
        List<Long> ids = ids(admin(new ProjektFilter()));

        assertThat(ids).isSorted();
    }

    // ---------- Hilfsmethoden ----------

    private List<Projekt> admin(ProjektFilter filter) {
        return service.projekteSehen(ADMIN_ID, Rolle.ADMIN, filter);
    }

    private List<Projekt> projektleitung(ProjektFilter filter) {
        return service.projekteSehen(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, filter);
    }

    private List<Projekt> mitarbeiter(long mitarbeiterId, ProjektFilter filter) {
        return service.projekteSehen(mitarbeiterId, Rolle.MITARBEITER, filter);
    }

    private List<Long> ids(List<Projekt> projekte) {
        return projekte.stream().map(Projekt::getProjektId).toList();
    }

    private long speichere(String name, String start, String ziel, ProjektStatus status) {
        Projekt projekt = new Projekt(EIGENTLICHE_PROJEKTLEITUNG_ID, name, null, LocalDate.parse(start), LocalDate.parse(ziel));
        projekt.setStatus(status);
        return repository.save(projekt).getProjektId();
    }

    private void ordneZu(long mitarbeiterId, long projektId) {
        zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));
    }
}
