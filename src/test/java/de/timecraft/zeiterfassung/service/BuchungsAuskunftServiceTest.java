package de.timecraft.zeiterfassung.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Pause;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.AenderungsprotokollRepository;
import de.timecraft.zeiterfassung.repository.PauseRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/** @author Noah Cipa */
class BuchungsAuskunftServiceTest {

    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-11-15T08:30:00Z"), ZoneId.of("Europe/Berlin"));
    private static final YearMonth OKTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private final ZeiteintragRepository zeiteintragRepository = mock(ZeiteintragRepository.class);
    private final PauseRepository pauseRepository = mock(PauseRepository.class);
    private final ZeiteintragService zeiteintragService = new ZeiteintragService(
            zeiteintragRepository, pauseRepository, new PausenRegelService(),
            new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), new FakeProjektAuskunft(), mock(AbwesenheitService.class),
            mock(FeiertagService.class), new ZugriffsPruefung(new FakeProjektAuskunft()), FESTE_UHR);
    private final AenderungsprotokollRepository aenderungsprotokollRepository = mock(AenderungsprotokollRepository.class);
    private final BuchungsAuskunftService service = new BuchungsAuskunftService(
            zeiteintragRepository, pauseRepository, aenderungsprotokollRepository, zeiteintragService, FESTE_UHR);

    /** Zeitpunkt zu einer Ortszeit in Europe/Berlin. */
    private static Instant berlin(int jahr, int monat, int tag, int stunde, int minute) {
        return LocalDateTime.of(jahr, monat, tag, stunde, minute).atZone(ZoneId.of("Europe/Berlin")).toInstant();
    }

    private Zeiteintrag eintragMitId(long id, Instant beginn, Instant ende) {
        Zeiteintrag eintrag = spy(new Zeiteintrag(1, beginn, Standort.BUERO, 1024L));
        doReturn(id).when(eintrag).getId();
        if (ende != null) {
            eintrag.ausstempeln(ende);
        }
        return eintrag;
    }

    /** Die Abfragen filtern wie die Datenbank. */
    private void repositoryLiefert(Zeiteintrag... eintraege) {
        when(zeiteintragRepository.findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                anyLong(), any(), any())).thenAnswer(aufruf -> {
                    Instant von = aufruf.getArgument(1);
                    Instant bis = aufruf.getArgument(2);
                    return Stream.of(eintraege)
                            .filter(e -> !e.getBeginn().isBefore(von) && e.getBeginn().isBefore(bis))
                            .toList();
                });
        when(zeiteintragRepository.findeUeberschneidende(anyLong(), any(), any()))
                .thenAnswer(aufruf -> ueberschneidende(aufruf.getArgument(1), aufruf.getArgument(2), eintraege));
    }

    private List<Zeiteintrag> ueberschneidende(Instant von, Instant bis, Zeiteintrag... eintraege) {
        return Stream.of(eintraege)
                .filter(e -> e.getBeginn().isBefore(bis) && (e.getEnde() == null || e.getEnde().isAfter(von)))
                .toList();
    }

    @Test
    void nachtschichtUeberDieMonatsgrenzeWirdAufgeteilt() {
        Zeiteintrag nachtschicht = eintragMitId(5, berlin(2026, 10, 31, 22, 0), berlin(2026, 11, 1, 6, 0));
        repositoryLiefert(nachtschicht);

        List<ZeitSegment> oktober = service.segmente(1, OKTOBER);
        List<ZeitSegment> november = service.segmente(1, NOVEMBER);

        assertThat(oktober).hasSize(1);
        assertThat(oktober.get(0).tag()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(oktober.get(0).anrechenbareMinuten()).isEqualTo(120);
        assertThat(november).hasSize(1);
        assertThat(november.get(0).tag()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(november.get(0).anrechenbareMinuten()).isEqualTo(330);
        assertThat(120 + 330).isEqualTo((int) zeiteintragService.nettoArbeitszeit(nachtschicht).toMinutes());
    }

    @Test
    void pauseUeberMitternachtWirdAufBeideTageVerteilt() {
        Zeiteintrag nachtschicht = eintragMitId(5, berlin(2026, 10, 31, 20, 0), berlin(2026, 11, 1, 4, 0));
        Pause pause = new Pause(nachtschicht, berlin(2026, 10, 31, 23, 30));
        pause.pauseBeenden(berlin(2026, 11, 1, 0, 30));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(pause));
        repositoryLiefert(nachtschicht);

        assertThat(service.segmente(1, OKTOBER).get(0).anrechenbareMinuten()).isEqualTo(210);
        assertThat(service.segmente(1, NOVEMBER).get(0).anrechenbareMinuten()).isEqualTo(210);
    }

    @Test
    void laufendeBuchungenErscheinenNichtInDenSegmenten() {
        repositoryLiefert(eintragMitId(5, berlin(2026, 11, 15, 8, 0), null));

        assertThat(service.segmente(1, NOVEMBER)).isEmpty();
    }

    @Test
    void laufendeBuchungWirdAlsBuchungInfoGeliefert() {
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 11, 15, 8, 0), null);
        when(zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(1)).thenReturn(Optional.of(laufend));

        BuchungInfo info = service.laufendeBuchung(1).orElseThrow();

        assertThat(info.laeuft()).isTrue();
        assertThat(info.nettoMinuten()).isEqualTo(0);
        assertThat(info.projektId()).isEqualTo(1024L);
        assertThat(info.standort()).isEqualTo(Standort.BUERO);
        assertThat(info.beginn()).isEqualTo(Instant.parse("2026-11-15T07:00:00Z"));
    }

    @Test
    void unbekannteBuchungGibtNichts() {
        when(zeiteintragRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.buchung(99)).isEmpty();
    }

    @Test
    void buchungenLiefertNurEintraegeImZeitraum() {
        Zeiteintrag vormittag = eintragMitId(5, berlin(2026, 11, 10, 8, 0), berlin(2026, 11, 10, 12, 0));
        Zeiteintrag nachmittag = eintragMitId(6, berlin(2026, 11, 10, 13, 0), berlin(2026, 11, 10, 17, 0));
        repositoryLiefert(vormittag, nachmittag);

        List<BuchungInfo> buchungen = service.buchungen(1,
                Instant.parse("2026-11-10T11:30:00Z"), Instant.parse("2026-11-10T17:00:00Z"));

        assertThat(buchungen).extracting(BuchungInfo::zeiteintragId).containsExactly(6L);
    }

    @Test
    void segmenteAmTagDerWinterzeitumstellung() {
        // 24.10. 23:00 bis 25.10. 04:00 Uhr Ortszeit: 1 h am 24.10., 5 h am 25.10. (doppelte Stunde)
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 24, 23, 0), berlin(2026, 10, 25, 4, 0));
        repositoryLiefert(eintrag);

        List<ZeitSegment> segmente = service.segmente(1, OKTOBER);

        assertThat(segmente).extracting(ZeitSegment::tag)
                .containsExactly(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25));
        assertThat(segmente).extracting(ZeitSegment::anrechenbareMinuten).containsExactly(60, 300);
        assertThat(segmente.get(1).beginn()).isEqualTo(Instant.parse("2026-10-24T22:00:00Z"));
        assertThat(60 + 300).isEqualTo((int) zeiteintragService.nettoArbeitszeit(eintrag).toMinutes());
    }

    @Test
    void segmenteAmTagDerSommerzeitumstellung() {
        // 28.03. 23:00 bis 29.03. 05:00 Uhr Ortszeit: 1 h am 28.03., 4 h am 29.03. (fehlende Stunde)
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 3, 28, 23, 0), berlin(2026, 3, 29, 5, 0));
        repositoryLiefert(eintrag);

        List<ZeitSegment> segmente = service.segmente(1, YearMonth.of(2026, 3));

        assertThat(segmente).extracting(ZeitSegment::anrechenbareMinuten).containsExactly(60, 240);
        assertThat(segmente.get(1).beginn()).isEqualTo(Instant.parse("2026-03-28T23:00:00Z"));
    }

    @Test
    void fehlendePauseMachtKeinTeilstueckNegativ() {
        // 16:00 bis 00:15 ohne Pause: 495 Minuten, 30 fehlen, der Folgetag hat nur 15
        Zeiteintrag spaet = eintragMitId(5, berlin(2026, 11, 10, 16, 0), berlin(2026, 11, 11, 0, 15));
        repositoryLiefert(spaet);

        assertThat(service.segmente(1, NOVEMBER)).extracting(ZeitSegment::anrechenbareMinuten)
                .containsExactly(465, 0);
        assertThat(zeiteintragService.nettoArbeitszeit(spaet).toMinutes()).isEqualTo(465);
    }

    @Test
    void pflichtpauseDesTagesWirktImSegmentDesSpaeterenEintrags() {
        repositoryLiefert(eintragMitId(5, berlin(2026, 11, 10, 8, 0), berlin(2026, 11, 10, 12, 0)),
                eintragMitId(6, berlin(2026, 11, 10, 12, 0), berlin(2026, 11, 10, 16, 0)));

        assertThat(service.segmente(1, NOVEMBER)).extracting(ZeitSegment::anrechenbareMinuten)
                .containsExactly(240, 210);
    }

    @Test
    void tagelangerEintragErscheintImFolgemonat() {
        Zeiteintrag vergessen = eintragMitId(5, berlin(2026, 10, 29, 8, 0), berlin(2026, 11, 2, 8, 0));
        repositoryLiefert(vergessen);

        assertThat(service.segmente(1, NOVEMBER)).extracting(ZeitSegment::tag)
                .containsExactly(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2));
    }

    @Test
    void projektSegmenteNutzenDieUeberschneidungImMonat() {
        Zeiteintrag nachtschicht = eintragMitId(5, berlin(2026, 10, 31, 22, 0), berlin(2026, 11, 1, 2, 0));
        repositoryLiefert(nachtschicht);
        when(zeiteintragRepository.findeUeberschneidendeAufProjekt(1024, berlin(2026, 11, 1, 0, 0),
                berlin(2026, 12, 1, 0, 0))).thenReturn(List.of(nachtschicht));

        assertThat(service.segmenteProjekt(1024, NOVEMBER)).extracting(ZeitSegment::anrechenbareMinuten)
                .containsExactly(120);
    }

    @Test
    void buchungenFindetAuchTageVorherBegonneneLaufendeBuchung() {
        repositoryLiefert(eintragMitId(5, berlin(2026, 11, 10, 8, 0), null));

        assertThat(service.buchungen(1, berlin(2026, 11, 14, 0, 0), berlin(2026, 11, 15, 0, 0)))
                .extracting(BuchungInfo::zeiteintragId).containsExactly(5L);
    }

    @Test
    void laufendeBuchungBeruehrtKeinenZeitraumInDerZukunft() {
        repositoryLiefert(eintragMitId(5, berlin(2026, 11, 10, 8, 0), null));

        assertThat(service.buchungen(1, berlin(2026, 11, 16, 0, 0), berlin(2026, 11, 17, 0, 0))).isEmpty();
    }

    @Test
    void segmenteUndNettoStimmenAuchBeiPauseAusserhalbDerBuchung() {
        // Buchung nach Korrektur 14:00 bis 22:00, die alte Pause 12:00 bis 13:00 liegt davor
        Zeiteintrag verschoben = eintragMitId(5, berlin(2026, 11, 10, 14, 0), berlin(2026, 11, 10, 22, 0));
        Pause alt = new Pause(verschoben, berlin(2026, 11, 10, 12, 0));
        alt.pauseBeenden(berlin(2026, 11, 10, 13, 0));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(alt));
        repositoryLiefert(verschoben);

        assertThat(service.segmente(1, NOVEMBER)).extracting(ZeitSegment::anrechenbareMinuten).containsExactly(450);
        assertThat(zeiteintragService.nettoArbeitszeit(verschoben).toMinutes()).isEqualTo(450);
    }
}
