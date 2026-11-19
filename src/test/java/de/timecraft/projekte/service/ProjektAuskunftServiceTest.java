package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die lesende Schnittstelle ProjektAuskunft (FA-M3-12) mit einer echten Datenbank
 * (H2 im PostgreSQL-Modus). Die api prüft keine Rechte, deshalb gibt es hier keine Rollentests.
 * <p>
 * Die vier Beispielprojekte (die Zahlen 11, 12, 13 sind frei gewählte Mitarbeiter-IDs,
 * 98 und 99 sind Projektleitungen):
 * <pre>
 * Name                Projektleitung  Status       zugeordnet  Angebotspreis
 * Website Relaunch    99              AKTIV        11, 12      1500.00
 * Umbau Lager         98              AKTIV        11          (leer)
 * Altprojekt 2020     99              ARCHIVIERT   11, 12      (leer)
 * Rabatt-Aktion       99              AKTIV        niemand     (leer)
 * </pre>
 * Mitarbeiter 13 ist keinem Projekt zugeordnet.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ProjektAuskunftService.class)
class ProjektAuskunftServiceTest {

    private static final long MITARBEITER_11 = 11L;
    private static final long MITARBEITER_12 = 12L;
    private static final long MITARBEITER_13 = 13L;
    private static final long PROJEKTLEITUNG_98 = 98L;
    private static final long PROJEKTLEITUNG_99 = 99L;
    private static final long UNBEKANNTE_ID = 123456L;

    @Autowired
    private ProjektAuskunftService auskunft;

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
        websiteId = speichere(PROJEKTLEITUNG_99, "Website Relaunch", ProjektStatus.AKTIV);
        umbauId = speichere(PROJEKTLEITUNG_98, "Umbau Lager", ProjektStatus.AKTIV);
        altprojektId = speichere(PROJEKTLEITUNG_99, "Altprojekt 2020", ProjektStatus.ARCHIVIERT);
        rabattId = speichere(PROJEKTLEITUNG_99, "Rabatt-Aktion", ProjektStatus.AKTIV);

        Projekt website = repository.findById(websiteId).orElseThrow();
        website.setAngebotspreis(new BigDecimal("1500.00"));
        repository.save(website);

        ordneZu(MITARBEITER_11, websiteId);
        ordneZu(MITARBEITER_12, websiteId);
        ordneZu(MITARBEITER_11, umbauId);
        ordneZu(MITARBEITER_11, altprojektId);
        ordneZu(MITARBEITER_12, altprojektId);
    }

    // ---------- finde ----------

    @Test
    void findeLiefertAlleFelderEinesAktivenProjekts() {
        Optional<ProjektInfo> info = auskunft.finde(websiteId);

        assertThat(info).isPresent();
        assertThat(info.get().projektId()).isEqualTo(websiteId);
        assertThat(info.get().name()).isEqualTo("Website Relaunch");
        assertThat(info.get().startdatum()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(info.get().zieldatum()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(info.get().projektleitungId()).isEqualTo(PROJEKTLEITUNG_99);
        assertThat(info.get().aktiv()).isTrue();
        assertThat(info.get().angebotspreis()).isEqualByComparingTo("1500.00");
    }

    @Test
    void findeMeldetEinArchiviertesProjektAlsNichtAktiv() {
        assertThat(auskunft.finde(altprojektId).orElseThrow().aktiv()).isFalse();
    }

    @Test
    void findeLiefertOhneAngebotspreisNull() {
        assertThat(auskunft.finde(umbauId).orElseThrow().angebotspreis()).isNull();
    }

    @Test
    void findeLiefertBeiUnbekannterIdEinLeeresOptional() {
        assertThat(auskunft.finde(UNBEKANNTE_ID)).isEmpty();
    }

    // ---------- aktiveProjekteVon (Auswahl beim Einstempeln, QA-M3-01) ----------

    @Test
    void aktiveProjekteVonLiefertNurAktiveZugeordneteProjekte() {
        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_11))).containsExactly(websiteId, umbauId);
        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_12))).containsExactly(websiteId);
    }

    @Test
    void aktiveProjekteVonZeigtKeinArchiviertesProjektAuchWennZugeordnet() {
        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_12))).doesNotContain(altprojektId);
    }

    @Test
    void aktiveProjekteVonIstLeerOhneZuordnungUndBeiUnbekanntemMitarbeiter() {
        assertThat(auskunft.aktiveProjekteVon(MITARBEITER_13)).isEmpty();
        assertThat(auskunft.aktiveProjekteVon(UNBEKANNTE_ID)).isEmpty();
    }

    @Test
    void einProjektDasArchiviertWirdFaelltAusDerAuswahlZumEinstempeln() {
        Projekt website = repository.findById(websiteId).orElseThrow();
        website.setStatus(ProjektStatus.ARCHIVIERT);
        repository.save(website);

        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_11))).containsExactly(umbauId);
    }

    @Test
    void einEntfernterMitarbeiterBekommtDasProjektNichtMehrZurAuswahl() {
        zuordnungRepository.deleteById(new Projektzuordnung.Schluessel(MITARBEITER_11, umbauId));

        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_11))).containsExactly(websiteId);
    }

    @Test
    void aktiveProjekteVonSindNachProjektIdSortiert() {
        assertThat(projektIds(auskunft.aktiveProjekteVon(MITARBEITER_11))).isSorted();
    }

    // ---------- verantworteteProjekte ----------

    @Test
    void verantworteteProjekteLiefernAktiveUndArchivierte() {
        assertThat(projektIds(auskunft.verantworteteProjekte(PROJEKTLEITUNG_99)))
                .containsExactly(websiteId, altprojektId, rabattId);
        assertThat(projektIds(auskunft.verantworteteProjekte(PROJEKTLEITUNG_98))).containsExactly(umbauId);
    }

    @Test
    void verantworteteProjekteIstLeerFuerPersonOhneProjekte() {
        assertThat(auskunft.verantworteteProjekte(UNBEKANNTE_ID)).isEmpty();
        assertThat(auskunft.verantworteteProjekte(MITARBEITER_11)).isEmpty();
    }

    // ---------- alle ----------

    @Test
    void alleLiefertAlleProjekteAuchArchivierteNachProjektIdSortiert() {
        List<ProjektInfo> alle = auskunft.alle();

        assertThat(projektIds(alle)).containsExactly(websiteId, umbauId, altprojektId, rabattId);
        assertThat(alle).extracting(ProjektInfo::aktiv).containsExactly(true, true, false, true);
    }

    // ---------- zugeordneteMitarbeiter und istZugeordnet ----------

    @Test
    void zugeordneteMitarbeiterLiefertAlleIdsSortiert() {
        assertThat(auskunft.zugeordneteMitarbeiter(websiteId)).containsExactly(MITARBEITER_11, MITARBEITER_12);
    }

    @Test
    void zugeordneteMitarbeiterIstLeerBeiLeeremTeamUndUnbekanntemProjekt() {
        assertThat(auskunft.zugeordneteMitarbeiter(rabattId)).isEmpty();
        assertThat(auskunft.zugeordneteMitarbeiter(UNBEKANNTE_ID)).isEmpty();
    }

    @Test
    void zugeordneteMitarbeiterGibtesAuchBeiArchiviertemProjekt() {
        assertThat(auskunft.zugeordneteMitarbeiter(altprojektId)).containsExactly(MITARBEITER_11, MITARBEITER_12);
    }

    @Test
    void istZugeordnetSagtJaNurFuerZugeordnete() {
        assertThat(auskunft.istZugeordnet(MITARBEITER_11, umbauId)).isTrue();
        assertThat(auskunft.istZugeordnet(MITARBEITER_12, umbauId)).isFalse();
        assertThat(auskunft.istZugeordnet(MITARBEITER_13, websiteId)).isFalse();
    }

    @Test
    void istZugeordnetBeiUnbekanntemProjektIstFalse() {
        assertThat(auskunft.istZugeordnet(MITARBEITER_11, UNBEKANNTE_ID)).isFalse();
    }

    // ---------- Hilfsmethoden ----------

    private List<Long> projektIds(List<ProjektInfo> projekte) {
        return projekte.stream().map(ProjektInfo::projektId).toList();
    }

    private long speichere(long projektleitungId, String name, ProjektStatus status) {
        Projekt projekt = new Projekt(projektleitungId, name, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        projekt.setStatus(status);
        return repository.save(projekt).getProjektId();
    }

    private void ordneZu(long mitarbeiterId, long projektId) {
        zuordnungRepository.save(new Projektzuordnung(mitarbeiterId, projektId, LocalDateTime.of(2026, 1, 1, 8, 0)));
    }
}
