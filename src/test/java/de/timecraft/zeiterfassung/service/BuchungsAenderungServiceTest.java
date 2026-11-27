package de.timecraft.zeiterfassung.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Aenderungsprotokoll;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.AenderungsprotokollRepository;
import de.timecraft.zeiterfassung.repository.PauseRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/** @author Noah Cipa */
class BuchungsAenderungServiceTest {

    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-11-15T08:30:00Z"), ZoneId.of("Europe/Berlin"));
    private static final Standort BUERO = Standort.BUERO;

    private final ZeiteintragRepository zeiteintragRepository = mock(ZeiteintragRepository.class);
    private final PauseRepository pauseRepository = mock(PauseRepository.class);
    private final AenderungsprotokollRepository protokollRepository = mock(AenderungsprotokollRepository.class);
    private final ZeiteintragService zeiteintragService = new ZeiteintragService(
            zeiteintragRepository, pauseRepository, new PausenRegelService(),
            new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), new FakeProjektAuskunft(), mock(AbwesenheitService.class),
            mock(FeiertagService.class), new ZugriffsPruefung(new FakeProjektAuskunft()), FESTE_UHR);
    private final BuchungsAenderungService service = new BuchungsAenderungService(
            zeiteintragRepository, protokollRepository, zeiteintragService, FESTE_UHR);

    /** Zeitpunkt zu einer Ortszeit in Europe/Berlin. */
    private static Instant berlin(int jahr, int monat, int tag, int stunde, int minute) {
        return LocalDateTime.of(jahr, monat, tag, stunde, minute).atZone(ZoneId.of("Europe/Berlin")).toInstant();
    }

    private Zeiteintrag eintragMitId(long id, Instant beginn, Instant ende) {
        Zeiteintrag eintrag = spy(new Zeiteintrag(1, beginn, Standort.BUERO, 1024L));
        doReturn(id).when(eintrag).getId();
        when(zeiteintragRepository.findeMitarbeiterId(id)).thenReturn(Optional.of(1L));
        if (ende != null) {
            eintrag.ausstempeln(ende);
        }
        return eintrag;
    }

    private void repositoryLiefert(Zeiteintrag... eintraege) {
        when(zeiteintragRepository.findeUeberschneidende(anyLong(), any(), any())).thenAnswer(aufruf -> {
            Instant beginn = aufruf.getArgument(1);
            Instant ende = aufruf.getArgument(2);
            return Stream.of(eintraege)
                    .filter(e -> e.getBeginn().isBefore(ende) && (e.getEnde() == null || e.getEnde().isAfter(beginn)))
                    .toList();
        });
    }

    @Test
    void aendernSchreibtNurGeaenderteFelderInsProtokoll() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 11, 10, 8, 0), berlin(2026, 11, 10, 16, 0));
        when(zeiteintragRepository.findById(5L)).thenReturn(Optional.of(eintrag));
        repositoryLiefert(eintrag);

        service.aendern(5, Instant.parse("2026-11-10T07:00:00Z"), Instant.parse("2026-11-10T16:00:00Z"), BUERO, 1024L,
                AenderungsArt.KORREKTUR, 2, "Ausstempeln vergessen");

        ArgumentCaptor<Aenderungsprotokoll> protokoll = ArgumentCaptor.forClass(Aenderungsprotokoll.class);
        verify(protokollRepository).save(protokoll.capture());
        assertThat(protokoll.getValue().getFeld()).isEqualTo("ende");
        assertThat(protokoll.getValue().getAlterWert()).isEqualTo("10.11.2026 16:00");
        assertThat(protokoll.getValue().getNeuerWert()).isEqualTo("10.11.2026 17:00");
        assertThat(protokoll.getValue().getArt()).isEqualTo(AenderungsArt.KORREKTUR);
        assertThat(protokoll.getValue().getGeaendertVonId()).isEqualTo(2);
        assertThat(eintrag.getEnde()).isEqualTo(berlin(2026, 11, 10, 17, 0));
        assertThat(eintrag.isGeaendert()).isTrue();
    }

    @Test
    void laufendeBuchungKannNichtGeaendertWerden() {
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 11, 15, 8, 0), null);
        when(zeiteintragRepository.findById(5L)).thenReturn(Optional.of(laufend));

        assertThatThrownBy(() -> service.aendern(5, Instant.parse("2026-11-15T06:00:00Z"),
                Instant.parse("2026-11-15T08:00:00Z"), BUERO, null, AenderungsArt.KORREKTUR, 2, "Test"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Eine laufende Buchung kann nicht geändert werden.");
        verify(protokollRepository, never()).save(any());
    }

    @Test
    void aenderungMitUeberschneidungWirdNichtGespeichert() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 11, 10, 8, 0), berlin(2026, 11, 10, 16, 0));
        Zeiteintrag spaeter = eintragMitId(6, berlin(2026, 11, 10, 17, 0), berlin(2026, 11, 10, 18, 0));
        when(zeiteintragRepository.findById(5L)).thenReturn(Optional.of(eintrag));
        repositoryLiefert(eintrag, spaeter);

        assertThatThrownBy(() -> service.aendern(5, Instant.parse("2026-11-10T07:00:00Z"),
                Instant.parse("2026-11-10T16:30:00Z"), BUERO, 1024L, AenderungsArt.KORREKTUR, 2, "Test"))
                .isInstanceOf(FachlicheException.class);
        verify(protokollRepository, never()).save(any());
        assertThat(eintrag.getEnde()).isEqualTo(berlin(2026, 11, 10, 16, 0));
    }

    @Test
    void unbekannteBuchungKannNichtGeaendertWerden() {
        when(zeiteintragRepository.findeMitarbeiterId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(99, Instant.parse("2026-11-10T07:00:00Z"),
                Instant.parse("2026-11-10T15:00:00Z"), BUERO, null, AenderungsArt.AENDERUNG, 2, "Test"))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void nachtragenLegtEintragUndProtokollAn() {
        Zeiteintrag gespeichert = eintragMitId(9, berlin(2026, 11, 11, 8, 0), berlin(2026, 11, 11, 12, 0));
        when(zeiteintragRepository.save(any(Zeiteintrag.class))).thenReturn(gespeichert);

        long id = service.nachtragen(1, Instant.parse("2026-11-11T07:00:00Z"), Instant.parse("2026-11-11T11:00:00Z"),
                BUERO, null, 2, "Kalender-Eintrag");

        assertThat(id).isEqualTo(9);
        ArgumentCaptor<Zeiteintrag> neuerEintrag = ArgumentCaptor.forClass(Zeiteintrag.class);
        verify(zeiteintragRepository).save(neuerEintrag.capture());
        assertThat(neuerEintrag.getValue().isGeaendert()).isTrue();
        ArgumentCaptor<Aenderungsprotokoll> protokoll = ArgumentCaptor.forClass(Aenderungsprotokoll.class);
        verify(protokollRepository).save(protokoll.capture());
        assertThat(protokoll.getValue().getArt()).isEqualTo(AenderungsArt.NACHTRAG);
        assertThat(protokoll.getValue().getZeiteintragId()).isEqualTo(9);
    }

    @Test
    void nachtragenInDerZukunftWirdNichtGespeichert() {
        assertThatThrownBy(() -> service.nachtragen(1, Instant.parse("2026-11-15T08:00:00Z"),
                Instant.parse("2026-11-15T10:00:00Z"), BUERO, null, 2, "Test"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Ende darf nicht in der Zukunft liegen.");
        verify(zeiteintragRepository, never()).save(any());
        verify(protokollRepository, never()).save(any());
    }

    @Test
    void aenderungUndNachtragWerdenMinutengenauGespeichert() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 11, 10, 8, 0), berlin(2026, 11, 10, 16, 0));
        when(zeiteintragRepository.findById(5L)).thenReturn(Optional.of(eintrag));
        repositoryLiefert(eintrag);

        service.aendern(5, berlin(2026, 11, 10, 8, 0).plusSeconds(40), berlin(2026, 11, 10, 17, 0).plusSeconds(59),
                BUERO, 1024L, AenderungsArt.KORREKTUR, 2, "Test");

        assertThat(eintrag.getBeginn()).isEqualTo(berlin(2026, 11, 10, 8, 0));
        assertThat(eintrag.getEnde()).isEqualTo(berlin(2026, 11, 10, 17, 0));
        // Ab der Minute des Endes ist ein Nachtrag keine Ueberschneidung
        when(zeiteintragRepository.save(any(Zeiteintrag.class))).thenReturn(eintrag);
        ArgumentCaptor<Zeiteintrag> neu = ArgumentCaptor.forClass(Zeiteintrag.class);

        service.nachtragen(1, berlin(2026, 11, 10, 17, 0).plusSeconds(10), berlin(2026, 11, 10, 18, 0).plusSeconds(30),
                BUERO, null, 2, "Test");

        verify(zeiteintragRepository).save(neu.capture());
        assertThat(neu.getValue().getBeginn()).isEqualTo(berlin(2026, 11, 10, 17, 0));
        assertThat(neu.getValue().getEnde()).isEqualTo(berlin(2026, 11, 10, 18, 0));
    }

    @Test
    void nachtragenUndAendernNurMitBekanntenMitarbeitern() {
        Instant beginn = berlin(2026, 11, 10, 8, 0);
        Instant ende = berlin(2026, 11, 10, 12, 0);
        List<Runnable> aufrufe = List.of(
                () -> service.nachtragen(999, beginn, ende, BUERO, null, 1, "Test"),
                () -> service.nachtragen(1, beginn, ende, BUERO, null, 999, "Test"),
                () -> service.aendern(5, beginn, ende, BUERO, null, AenderungsArt.KORREKTUR, 999, "Test"));

        for (Runnable aufruf : aufrufe) {
            assertThatThrownBy(aufruf::run)
                    .isInstanceOf(NichtGefundenException.class)
                    .hasMessage("Der Mitarbeiter wurde nicht gefunden.");
        }
        verify(zeiteintragRepository, never()).save(any());
        verify(protokollRepository, never()).save(any());
    }
}
