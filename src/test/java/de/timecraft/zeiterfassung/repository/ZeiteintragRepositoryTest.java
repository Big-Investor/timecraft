package de.timecraft.zeiterfassung.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.Abwesenheit;
import de.timecraft.zeiterfassung.model.Aenderungsprotokoll;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.model.Pause;
import de.timecraft.zeiterfassung.model.Zeiteintrag;

/** @author Noah Cipa */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ZeiteintragRepositoryTest {

    @Autowired
    private ZeiteintragRepository zeiteintragRepository;

    @Autowired
    private PauseRepository pauseRepository;

    @Autowired
    private AenderungsprotokollRepository protokollRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Zeiteintrag abgeschlossen(long mitarbeiterId, Instant beginn, Instant ende) {
        Zeiteintrag eintrag = new Zeiteintrag(mitarbeiterId, beginn, Standort.UNTERWEGS, null);
        eintrag.ausstempeln(ende);
        return zeiteintragRepository.saveAndFlush(eintrag);
    }

    private void loeschenScheitertAmFremdschluessel(long zeiteintragId) {
        // Persistenzkontext leeren, damit die Datenbank selbst den Fremdschluessel prueft
        entityManager.flush();
        entityManager.clear();
        assertThatThrownBy(() -> {
            zeiteintragRepository.deleteById(zeiteintragId);
            zeiteintragRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void spaltenVorhanden(String tabelle, String spalten) {
        // Schlaegt fehl, wenn eine Spalte des Relationenmodells fehlt oder anders heisst.
        entityManager.getEntityManager().createNativeQuery("select " + spalten + " from " + tabelle).getResultList();
    }

    @Test
    void ueberschneidungFindetAuchTageAlteBeendeteUndLaufendeEintraege() {
        // Ausstempeln vergessen und zwei Tage spaeter nachgeholt
        Zeiteintrag lang = abgeschlossen(1, Instant.parse("2026-10-19T06:00:00Z"), Instant.parse("2026-10-21T08:00:00Z"));
        Zeiteintrag laufend = zeiteintragRepository.saveAndFlush(
                new Zeiteintrag(1, Instant.parse("2026-10-22T06:00:00Z"), Standort.BUERO, null));
        abgeschlossen(2, Instant.parse("2026-10-21T06:00:00Z"), Instant.parse("2026-10-21T07:00:00Z"));

        assertThat(zeiteintragRepository.findeUeberschneidende(1, Instant.parse("2026-10-21T06:00:00Z"),
                Instant.parse("2026-10-21T07:00:00Z"))).containsExactly(lang);
        assertThat(zeiteintragRepository.findeUeberschneidende(1, Instant.parse("2026-10-22T07:00:00Z"),
                Instant.parse("2026-10-22T08:00:00Z"))).containsExactly(laufend);
    }

    @Test
    void angrenzendeEintraegeUeberschneidenSichNicht() {
        abgeschlossen(1, Instant.parse("2026-10-21T06:00:00Z"), Instant.parse("2026-10-21T08:00:00Z"));

        assertThat(zeiteintragRepository.findeUeberschneidende(1, Instant.parse("2026-10-21T08:00:00Z"),
                Instant.parse("2026-10-21T09:00:00Z"))).isEmpty();
        assertThat(zeiteintragRepository.findeUeberschneidende(1, Instant.parse("2026-10-21T05:00:00Z"),
                Instant.parse("2026-10-21T06:00:00Z"))).isEmpty();
    }

    @Test
    void tabellenUndSpaltenHeissenWieImRelationenmodell() {
        spaltenVorhanden("zeiteintrag", "zeiteintrag_id, mitarbeiter_id, projekt_id, beginn, ende, standort, notiz, geaendert");
        spaltenVorhanden("pause", "pause_id, zeiteintrag_id, beginn, ende");
        spaltenVorhanden("abwesenheit", "abwesenheit_id, mitarbeiter_id, antrag_id, art, von, bis");
        spaltenVorhanden("feiertag", "feiertag_id, datum, name");
        spaltenVorhanden("aenderungsprotokoll",
                "protokoll_id, zeiteintrag_id, geaendert_von_id, zeitpunkt, art, feld, alter_wert, neuer_wert, begruendung");
    }

    @Test
    void standortUndProjektWerdenGespeichertUndGelesen() {
        Zeiteintrag eintrag = zeiteintragRepository.saveAndFlush(
                new Zeiteintrag(1, Instant.parse("2026-11-02T07:00:00Z"), Standort.HOMEOFFICE, 1024L));
        entityManager.clear();

        Zeiteintrag gelesen = zeiteintragRepository.findById(eintrag.getId()).orElseThrow();
        assertThat(gelesen.getStandort()).isEqualTo(Standort.HOMEOFFICE);
        assertThat(gelesen.getProjektId()).isEqualTo(1024L);
        assertThat(gelesen.getEnde()).isNull();
    }

    @Test
    void doppelteStundeBeiWinterzeitBleibtUnterscheidbar() {
        // 25.10.2026: 02:30 Uhr gibt es zweimal (00:30 UTC in Sommerzeit, 01:30 UTC in Winterzeit)
        Instant ersteHalbeDrei = Instant.parse("2026-10-25T00:30:00Z");
        Instant zweiteHalbeDrei = Instant.parse("2026-10-25T01:30:00Z");
        Zeiteintrag eintrag = abgeschlossen(1, ersteHalbeDrei, zweiteHalbeDrei);
        entityManager.clear();

        Zeiteintrag gelesen = zeiteintragRepository.findById(eintrag.getId()).orElseThrow();
        assertThat(gelesen.getBeginn()).isEqualTo(ersteHalbeDrei);
        assertThat(gelesen.getEnde()).isEqualTo(zweiteHalbeDrei);
        assertThat(Duration.between(gelesen.getBeginn(), gelesen.getEnde())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void zeitpunkteRundUmDieSommerzeitBleibenErhalten() {
        // 29.03.2026: 01:30 Uhr Winterzeit bis 03:30 Uhr Sommerzeit ist eine Stunde
        Instant beginn = Instant.parse("2026-03-29T00:30:00Z");
        Instant ende = Instant.parse("2026-03-29T01:30:00Z");
        Zeiteintrag eintrag = abgeschlossen(1, beginn, ende);
        entityManager.clear();

        Zeiteintrag gelesen = zeiteintragRepository.findById(eintrag.getId()).orElseThrow();
        assertThat(gelesen.getBeginn()).isEqualTo(beginn);
        assertThat(gelesen.getEnde()).isEqualTo(ende);
    }

    @Test
    void zeitraumsucheSchliesstDenBeginnEinUndDasEndeAus() {
        Instant mitternacht = Instant.parse("2026-11-01T23:00:00Z");
        Zeiteintrag genauAmBeginn = abgeschlossen(1, mitternacht, mitternacht.plus(Duration.ofHours(1)));
        Zeiteintrag genauAmEnde = abgeschlossen(1, mitternacht.plus(Duration.ofDays(1)),
                mitternacht.plus(Duration.ofHours(25)));
        abgeschlossen(2, mitternacht.plus(Duration.ofHours(2)), mitternacht.plus(Duration.ofHours(3)));

        assertThat(zeiteintragRepository.findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                1, mitternacht, mitternacht.plus(Duration.ofDays(1))))
                .extracting(Zeiteintrag::getId)
                .containsExactly(genauAmBeginn.getId())
                .doesNotContain(genauAmEnde.getId());
    }

    @Test
    void findetDenLaufendenEintragUndDieLaufendePause() {
        abgeschlossen(1, Instant.parse("2026-11-02T07:00:00Z"), Instant.parse("2026-11-02T11:00:00Z"));
        Zeiteintrag laufend = zeiteintragRepository.saveAndFlush(
                new Zeiteintrag(1, Instant.parse("2026-11-03T07:00:00Z"), Standort.BUERO, null));
        Pause beendet = new Pause(laufend, Instant.parse("2026-11-03T09:00:00Z"));
        beendet.pauseBeenden(Instant.parse("2026-11-03T09:15:00Z"));
        pauseRepository.saveAndFlush(beendet);
        Pause offen = pauseRepository.saveAndFlush(new Pause(laufend, Instant.parse("2026-11-03T10:00:00Z")));

        assertThat(zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(1)).map(Zeiteintrag::getId)
                .contains(laufend.getId());
        assertThat(pauseRepository.findByZeiteintrag_IdAndEndeIsNull(laufend.getId())).map(Pause::getId)
                .contains(offen.getId());
        assertThat(pauseRepository.findByZeiteintrag_Id(laufend.getId())).hasSize(2);
    }

    @Test
    void zeiteintragMitPauseLaesstSichNichtLoeschen() {
        Zeiteintrag eintrag = abgeschlossen(1, Instant.parse("2026-11-02T07:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"));
        Pause pause = new Pause(eintrag, Instant.parse("2026-11-02T11:00:00Z"));
        pause.pauseBeenden(Instant.parse("2026-11-02T11:30:00Z"));
        pauseRepository.saveAndFlush(pause);

        loeschenScheitertAmFremdschluessel(eintrag.getId());
    }

    @Test
    void pauseZuUnbekanntemZeiteintragWirdAbgelehnt() {
        Zeiteintrag unbekannt = entityManager.getEntityManager().getReference(Zeiteintrag.class, 999_999L);

        assertThatThrownBy(() -> pauseRepository.saveAndFlush(new Pause(unbekannt, Instant.parse("2026-11-02T11:00:00Z"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void protokollVerweistAufDenZeiteintragUndHaeltDenZeitpunkt() {
        Zeiteintrag eintrag = abgeschlossen(1, Instant.parse("2026-11-02T07:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"));
        Instant zeitpunkt = Instant.parse("2026-11-03T08:15:00Z");
        protokollRepository.save(new Aenderungsprotokoll(eintrag, 2, zeitpunkt, AenderungsArt.KORREKTUR,
                "ende", "02.11.2026 16:00", "02.11.2026 17:00", "Ausstempeln vergessen"));
        entityManager.flush();
        entityManager.clear();

        Aenderungsprotokoll gelesen = protokollRepository.findByZeiteintrag_IdOrderByZeitpunkt(eintrag.getId()).get(0);
        assertThat(gelesen.getZeiteintragId()).isEqualTo(eintrag.getId());
        assertThat(gelesen.getZeitpunkt()).isEqualTo(zeitpunkt);
        assertThat(gelesen.getGeaendertVonId()).isEqualTo(2);
    }

    @Test
    void zeiteintragMitProtokollLaesstSichNichtLoeschen() {
        Zeiteintrag eintrag = abgeschlossen(1, Instant.parse("2026-11-02T07:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"));
        protokollRepository.save(new Aenderungsprotokoll(eintrag, 2, Instant.parse("2026-11-03T08:15:00Z"),
                AenderungsArt.NACHTRAG, "eintrag", null, "02.11.2026 08:00 bis 02.11.2026 16:00", null));
        entityManager.flush();

        loeschenScheitertAmFremdschluessel(eintrag.getId());
    }

    @Test
    void abwesenheitUndFeiertagHabenEigeneSchluessel() {
        Abwesenheit abwesenheit = entityManager.persistFlushFind(new Abwesenheit(1, 77L, AbwesenheitsArt.URLAUB,
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 23)));
        Feiertag feiertag = entityManager.persistFlushFind(new Feiertag(LocalDate.of(2026, 6, 15), "Testfeiertag"));

        assertThat(abwesenheit.getId()).isNotNull();
        assertThat(abwesenheit.getAntragId()).isEqualTo(77L);
        assertThat(feiertag.getId()).isNotNull();
    }

    @Test
    void ueberschneidungJeProjektFindetAlleMitarbeiterDiesesProjekts() {
        Zeiteintrag frueher = new Zeiteintrag(1, Instant.parse("2026-10-31T20:00:00Z"), Standort.BUERO, 1024L);
        frueher.ausstempeln(Instant.parse("2026-11-01T02:00:00Z"));
        zeiteintragRepository.saveAndFlush(frueher);
        Zeiteintrag laufend = zeiteintragRepository.saveAndFlush(
                new Zeiteintrag(2, Instant.parse("2026-11-03T07:00:00Z"), Standort.BUERO, 1024L));
        zeiteintragRepository.saveAndFlush(
                new Zeiteintrag(3, Instant.parse("2026-11-03T07:00:00Z"), Standort.BUERO, 1031L));

        assertThat(zeiteintragRepository.findeUeberschneidendeAufProjekt(1024, Instant.parse("2026-10-31T23:00:00Z"),
                Instant.parse("2026-11-30T23:00:00Z")))
                .extracting(Zeiteintrag::getId).containsExactly(frueher.getId(), laufend.getId());
    }

    @Test
    void ueberschneidungJeProjektLaesstAngrenzendeUndAndereEintraegeWeg() {
        Instant von = Instant.parse("2026-10-31T23:00:00Z");
        Instant bis = Instant.parse("2026-11-30T23:00:00Z");
        Instant imZeitraum = Instant.parse("2026-11-03T07:00:00Z");
        projektEintrag(1, 1024L, von.minus(Duration.ofHours(2)), von);
        projektEintrag(2, 1024L, bis, bis.plus(Duration.ofHours(2)));
        projektEintrag(3, null, imZeitraum, imZeitraum.plus(Duration.ofHours(1)));
        projektEintrag(4, 1031L, imZeitraum, imZeitraum.plus(Duration.ofHours(1)));
        // Laufender Eintrag, der erst am Ende des Zeitraums beginnt
        zeiteintragRepository.saveAndFlush(new Zeiteintrag(5, bis, Standort.BUERO, 1024L));

        assertThat(zeiteintragRepository.findeUeberschneidendeAufProjekt(1024, von, bis)).isEmpty();
    }

    private void projektEintrag(long mitarbeiterId, Long projektId, Instant beginn, Instant ende) {
        Zeiteintrag eintrag = new Zeiteintrag(mitarbeiterId, beginn, Standort.BUERO, projektId);
        eintrag.ausstempeln(ende);
        zeiteintragRepository.saveAndFlush(eintrag);
    }
}
