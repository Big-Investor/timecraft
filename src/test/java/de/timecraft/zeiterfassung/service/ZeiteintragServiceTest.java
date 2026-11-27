package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.FELIX_BRANDT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.MEHMET_YILMAZ;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.BUEROUMBAU_SCHMITT;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.EINBAUKUECHE_WAGNER;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.LADENBAU_KRAUSE;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.TREPPE_LINDENSTRASSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.AbwesenheitsTag;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.model.Pause;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.model.UebersichtEintrag;
import de.timecraft.zeiterfassung.model.UebersichtTag;
import de.timecraft.zeiterfassung.model.UebersichtWoche;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.model.Zeitenuebersicht;
import de.timecraft.zeiterfassung.repository.PauseRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;
import de.timecraft.zeiterfassung.service.EingabeFehler.Feld;

/** @author Noah Cipa */
class ZeiteintragServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-10-22T07:30:00Z"), BERLIN);
    private static final Instant JETZT = Instant.parse("2026-10-22T07:30:00Z");

    private final ZeiteintragRepository zeiteintragRepository = mock(ZeiteintragRepository.class);
    private final PauseRepository pauseRepository = mock(PauseRepository.class);
    private final AbwesenheitService abwesenheitService = mock(AbwesenheitService.class);
    private final FeiertagService feiertagService = mock(FeiertagService.class);
    private final ZeiteintragService service = new ZeiteintragService(
            zeiteintragRepository, pauseRepository, new PausenRegelService(),
            new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), new FakeProjektAuskunft(), abwesenheitService,
            feiertagService, new ZugriffsPruefung(new FakeProjektAuskunft()), FESTE_UHR);

    private final List<Zeiteintrag> angelegte = new ArrayList<>();

    @BeforeEach
    void speichernLiefertDasGespeicherte() {
        when(zeiteintragRepository.save(any(Zeiteintrag.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
        when(pauseRepository.save(any(Pause.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
    }

    /** Wie die Datenbank liefert die Tagesabfrage alle im Test angelegten Eintraege der Person im Zeitraum. */
    @BeforeEach
    void tagesabfrageLiefertAngelegteEintraege() {
        when(zeiteintragRepository.findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                anyLong(), any(), any())).thenAnswer(aufruf -> imZeitraum(angelegte.stream()
                        .filter(e -> e.getMitarbeiterId() == aufruf.<Long>getArgument(0)).toList(),
                        aufruf.getArgument(1), aufruf.getArgument(2)));
    }

    /** Nur Eintraege mit Beginn im halboffenen Zeitraum [von, bis), sortiert nach Beginn. */
    private static List<Zeiteintrag> imZeitraum(List<Zeiteintrag> alle, Instant von, Instant bis) {
        if (von == null || bis == null) {
            return List.of();
        }
        return alle.stream()
                .filter(e -> !e.getBeginn().isBefore(von) && e.getBeginn().isBefore(bis))
                .sorted(Comparator.comparing(Zeiteintrag::getBeginn))
                .toList();
    }

    /** Zeitpunkt zu einer Ortszeit in Europe/Berlin. */
    private static Instant berlin(int jahr, int monat, int tag, int stunde, int minute) {
        return LocalDateTime.of(jahr, monat, tag, stunde, minute).atZone(ZoneId.of("Europe/Berlin")).toInstant();
    }

    private Zeiteintrag eintragMitId(long id, Instant beginn) {
        return eintragVon(JONAS_BECKER, id, beginn);
    }

    private Zeiteintrag eintragVon(long mitarbeiterId, long id, Instant beginn) {
        Zeiteintrag eintrag = spy(new Zeiteintrag(mitarbeiterId, beginn, Standort.BUERO, null));
        doReturn(id).when(eintrag).getId();
        angelegte.add(eintrag);
        return eintrag;
    }

    private Pause pause(Zeiteintrag eintrag, Instant beginn, Instant ende) {
        Pause pause = new Pause(eintrag, beginn);
        pause.pauseBeenden(ende);
        return pause;
    }

    @Test
    void einstempelnSpeichertEintragMitServerzeit() {
        Zeiteintrag eintrag = service.einstempeln(1, Rolle.MITARBEITER, Standort.HOMEOFFICE, null);

        assertThat(eintrag.getBeginn()).isEqualTo(JETZT);
        assertThat(eintrag.getEnde()).isNull();
        assertThat(eintrag.getStandort()).isEqualTo(Standort.HOMEOFFICE);
    }

    @Test
    void einstempelnOhneStandortSpeichertNichts() {
        assertAbgelehnt(() -> service.einstempeln(1, Rolle.MITARBEITER, null, null),
                "Bitte wählen Sie einen Standort aus.");
    }

    @Test
    void einstempelnWennSchonEingestempeltSpeichertNichts() {
        Zeiteintrag laufenderEintrag = eintragMitId(5, JETZT.minus(Duration.ofHours(1)));
        alsLaufend(laufenderEintrag);

        assertThatThrownBy(() -> service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null))
                .isInstanceOf(FachlicheException.class);
        verify(zeiteintragRepository, never()).save(any());
    }

    @Test
    void ohneAnmeldungIstKeineAktionErlaubt() {
        assertThatThrownBy(() -> service.einstempeln(1, null, Standort.BUERO, null))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.ausstempeln(1, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.pauseStarten(1, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.zeiten(1, null, 1, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22)))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void pauseStartenOhneLaufendenEintragGehtNicht() {
        assertThatThrownBy(() -> service.pauseStarten(1, Rolle.MITARBEITER))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Sie sind nicht eingestempelt.");
    }

    @Test
    void unpassendePausenAktionenWerdenAbgelehntUndNichtsWirdGespeichert() {
        assertThatThrownBy(() -> service.pauseBeenden(1, Rolle.MITARBEITER))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Sie sind nicht eingestempelt.");

        Zeiteintrag laufenderEintrag = eintragMitId(5, berlin(2026, 10, 22, 8, 0));
        alsLaufend(laufenderEintrag);
        assertThatThrownBy(() -> service.pauseBeenden(1, Rolle.MITARBEITER))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Sie sind nicht in einer Pause.");

        Pause laufendePause = new Pause(laufenderEintrag, berlin(2026, 10, 22, 9, 0));
        when(pauseRepository.findByZeiteintrag_IdAndEndeIsNull(5)).thenReturn(Optional.of(laufendePause));
        assertThatThrownBy(() -> service.pauseStarten(1, Rolle.MITARBEITER))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Sie sind bereits in einer Pause.");

        verify(pauseRepository, never()).save(any());
        assertThat(laufendePause.getEnde()).isNull();
    }

    @Test
    void ausstempelnBeendetLaufendePauseZurSelbenZeit() {
        Zeiteintrag laufenderEintrag = eintragMitId(5, berlin(2026, 10, 22, 8, 0));
        Pause laufendePause = new Pause(laufenderEintrag, berlin(2026, 10, 22, 9, 0));
        alsLaufend(laufenderEintrag);
        when(pauseRepository.findByZeiteintrag_IdAndEndeIsNull(5)).thenReturn(Optional.of(laufendePause));

        service.ausstempeln(1, Rolle.MITARBEITER);

        assertThat(laufenderEintrag.getEnde()).isEqualTo(JETZT);
        assertThat(laufendePause.getEnde()).isEqualTo(JETZT);
    }

    @Test
    void nettoArbeitszeitZiehtAllePausenAb() {
        // 8:00 bis 16:40 mit 30 + 10 Minuten Pause = 8 Stunden netto
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 16, 40));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(eintrag, berlin(2026, 10, 21, 12, 0), berlin(2026, 10, 21, 12, 30)),
                pause(eintrag, berlin(2026, 10, 21, 15, 0), berlin(2026, 10, 21, 15, 10))));

        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(8));
        assertThat(service.pausen(eintrag)).isEqualTo(Duration.ofMinutes(40));
    }

    @Test
    void nettoArbeitszeitBeiLaufendemEintragRechnetBisJetzt() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 22, 8, 0));

        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void fehlendePflichtpauseWirdVonNettoAbgezogen() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 15, 0));

        assertThat(service.fehlendePauseMinuten(eintrag)).isEqualTo(30);
        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofMinutes(6 * 60 + 30));
    }

    @Test
    void hinweisNenntFehlendePause() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 15, 0));
        assertThat(service.hinweis(eintrag.getMitarbeiterId(), Rolle.MITARBEITER, eintrag)).contains("30 Minuten Pause").doesNotContain("Höchstarbeitszeit");
    }

    @Test
    void hinweisWarntBeiMehrAlsZehnStunden() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 7, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 18, 0));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(eintrag, berlin(2026, 10, 21, 12, 0), berlin(2026, 10, 21, 12, 45))));
        assertThat(service.hinweis(eintrag.getMitarbeiterId(), Rolle.MITARBEITER, eintrag)).contains("Höchstarbeitszeit").doesNotContain("Pause");
    }

    @Test
    void keinHinweisWennAllesPasst() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 12, 0));

        assertThat(service.hinweis(eintrag.getMitarbeiterId(), Rolle.MITARBEITER, eintrag)).isEmpty();
    }

    @Test
    void minderjaehrigeBrauchenSchonNachViereinhalbStundenPause() {
        Zeiteintrag mehmet = eintragVon(MEHMET_YILMAZ, 5, berlin(2026, 10, 21, 8, 0));
        mehmet.ausstempeln(berlin(2026, 10, 21, 13, 0));
        Zeiteintrag jonas = eintragMitId(6, berlin(2026, 10, 21, 8, 0));
        jonas.ausstempeln(berlin(2026, 10, 21, 13, 0));

        assertThat(service.fehlendePauseMinuten(mehmet)).isEqualTo(30);
        assertThat(service.nettoArbeitszeit(mehmet)).isEqualTo(Duration.ofMinutes(4 * 60 + 30));
        assertThat(service.fehlendePauseMinuten(jonas)).isEqualTo(0);
    }

    @Test
    void minderjaehrigenregelGiltBisZumTagVorDemAchtzehntenGeburtstag() {
        // Mehmet wird am 23.10.2027 volljaehrig; 7 Stunden ohne Pause
        Zeiteintrag letzterTagMinderjaehrig = eintragVon(MEHMET_YILMAZ, 5, berlin(2027, 10, 22, 8, 0));
        letzterTagMinderjaehrig.ausstempeln(berlin(2027, 10, 22, 15, 0));
        Zeiteintrag geburtstag = eintragVon(MEHMET_YILMAZ, 6, berlin(2027, 10, 23, 8, 0));
        geburtstag.ausstempeln(berlin(2027, 10, 23, 15, 0));

        assertThat(service.fehlendePauseMinuten(letzterTagMinderjaehrig)).isEqualTo(60);
        assertThat(service.fehlendePauseMinuten(geburtstag)).isEqualTo(30);
    }

    @Test
    void hinweisWarntMinderjaehrigeSchonBeiMehrAlsAchtStunden() {
        // 07:00 bis 16:45 mit 60 Minuten Pause = 8:45 Stunden
        Zeiteintrag mehmet = eintragVon(MEHMET_YILMAZ, 5, berlin(2026, 10, 21, 7, 0));
        mehmet.ausstempeln(berlin(2026, 10, 21, 16, 45));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(mehmet, berlin(2026, 10, 21, 12, 0), berlin(2026, 10, 21, 13, 0))));
        assertThat(service.hinweis(mehmet.getMitarbeiterId(), Rolle.MITARBEITER, mehmet)).contains("Höchstarbeitszeit").doesNotContain("Pause");
    }

    @Test
    void unbekannterMitarbeiterWirdNachDenRegelnFuerErwachseneBerechnet() {
        Zeiteintrag eintrag = eintragVon(99, 5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 13, 0));

        // 5 h ohne Pause: fuer Minderjaehrige fehlten 30 min, fuer Erwachsene nichts
        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(5));
    }

    @Test
    void nettoArbeitszeitUeberMitternacht() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 20, 22, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 2, 0));

        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void nachtragenSpeichertAbgeschlossenenEintrag() {
        Zeiteintrag eintrag = nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:00", null, null);

        assertThat(eintrag.getBeginn()).isEqualTo(berlin(2026, 10, 21, 8, 0));
        assertThat(eintrag.getEnde()).isEqualTo(berlin(2026, 10, 21, 16, 0));
    }

    @Test
    void nachtragenMitEndeVorBeginnSpeichertNichts() {
        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "07:00", null, null),
                "Das Ende muss nach dem Beginn liegen.");
    }

    @Test
    void nachtragenInDerZukunftSpeichertNichts() {
        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 22), "08:00", "10:00", null, null),
                "Das Ende darf nicht in der Zukunft liegen.");
    }

    @Test
    void nachtragenAusDemVorjahrSpeichertNichts() {
        assertThatThrownBy(() -> nachtragenMit(LocalDate.of(2025, 12, 30), "08:00", "16:00", null, null))
                .isInstanceOf(FachlicheException.class);
        verify(zeiteintragRepository, never()).save(any());
    }

    @Test
    void nachtragenOhnePflichtfelderSpeichertNichts() {
        assertThatThrownBy(() -> service.nachtragen(1, Rolle.MITARBEITER, LocalDate.of(2026, 10, 21),
                LocalTime.of(8, 0), LocalTime.of(16, 0), null, null, null, null))
                .isInstanceOf(FachlicheException.class);
        verify(zeiteintragRepository, never()).save(any());
    }

    // ---- Nachtragen mit Pause (FA-M2-06, FA-M2-07) ----

    @Test
    void nachtragenMitPauseSpeichertPauseMitBeginnUndEnde() {
        ArgumentCaptor<Pause> gespeichertePause = ArgumentCaptor.forClass(Pause.class);

        nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:30", "12:00", "12:30");

        verify(pauseRepository).save(gespeichertePause.capture());
        assertThat(gespeichertePause.getValue().getBeginn()).isEqualTo(berlin(2026, 10, 21, 12, 0));
        assertThat(gespeichertePause.getValue().getEnde()).isEqualTo(berlin(2026, 10, 21, 12, 30));
    }

    @Test
    void nachtragenOhnePauseSpeichertKeinePause() {
        nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "12:00", null, null);

        verify(pauseRepository, never()).save(any());
    }

    @Test
    void nachtragenMitHalberPauseSpeichertNichts() {
        assertThatThrownBy(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:00", "12:00", null))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie Beginn und Ende der Pause an.");
        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:00", null, "12:30"),
                "Bitte geben Sie Beginn und Ende der Pause an.");
        verify(pauseRepository, never()).save(any());
    }

    @Test
    void nachtragenMitPausenendeVorPausenbeginnSpeichertNichts() {
        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:00", "12:30", "12:00"),
                "Das Ende der Pause muss nach ihrem Beginn liegen.");
    }

    @Test
    void nachtragenMitPauseAusserhalbDesEintragsSpeichertNichts() {
        LocalDate tag = LocalDate.of(2026, 10, 21);
        // Pause beginnt mit dem Eintrag, endet mit dem Eintrag oder liegt ganz ausserhalb
        assertThatThrownBy(() -> nachtragenMit(tag, "08:00", "16:00", "08:00", "08:30"))
                .hasMessage("Die Pause muss innerhalb des Zeiteintrags liegen.");
        assertThatThrownBy(() -> nachtragenMit(tag, "08:00", "16:00", "15:30", "16:00"))
                .hasMessage("Die Pause muss innerhalb des Zeiteintrags liegen.");
        assertThatThrownBy(() -> nachtragenMit(tag, "08:00", "16:00", "17:00", "17:30"))
                .hasMessage("Die Pause muss innerhalb des Zeiteintrags liegen.");
        verify(zeiteintragRepository, never()).save(any());
        verify(pauseRepository, never()).save(any());
    }

    @Test
    void nachtragenMitPauseEineMinuteNachBeginnUndVorEndeIstErlaubt() {
        nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "09:00", "08:01", "08:59");

        verify(pauseRepository).save(any(Pause.class));
    }

    // ---- Projekt beim Einstempeln und Nachtragen (FA-M2-01, FA-M2-07, UC-ZE-01 3b) ----

    @Test
    void einstempelnMitAktivemZugeordnetemProjektSpeichertDasProjekt() {
        Zeiteintrag eintrag = service.einstempeln(JONAS_BECKER, Rolle.MITARBEITER,
                Standort.UNTERWEGS, EINBAUKUECHE_WAGNER);

        assertThat(eintrag.getProjektId()).isEqualTo(EINBAUKUECHE_WAGNER);
    }

    @Test
    void einstempelnOhneProjektSpeichertEintragOhneProjekt() {
        Zeiteintrag eintrag = service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null);

        assertThat(eintrag.getProjektId()).isNull();
    }

    @Test
    void einstempelnMitArchiviertemNichtZugeordnetemOderUnbekanntemProjektSpeichertNichts() {
        for (long projektId : List.of(LADENBAU_KRAUSE, BUEROUMBAU_SCHMITT, 9999L)) {
            assertThatThrownBy(() -> service.einstempeln(JONAS_BECKER, Rolle.MITARBEITER,
                    Standort.BUERO, projektId))
                    .isInstanceOf(FachlicheException.class)
                    .hasMessage("Das gewählte Projekt ist nicht mehr aktiv oder Ihnen nicht zugeordnet. "
                            + "Bitte wählen Sie ein anderes Projekt.");
        }
        verify(zeiteintragRepository, never()).save(any());
    }

    @Test
    void projektleitungStempeltNurAufZugeordneteProjekte() {
        Zeiteintrag eintrag = service.einstempeln(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG,
                Standort.BUERO, EINBAUKUECHE_WAGNER);

        assertThat(eintrag.getProjektId()).isEqualTo(EINBAUKUECHE_WAGNER);
        assertThatThrownBy(() -> service.einstempeln(SABINE_ENGEL, Rolle.PROJEKTLEITUNG,
                Standort.BUERO, TREPPE_LINDENSTRASSE)).isInstanceOf(FachlicheException.class);
    }

    @Test
    void nachtragenMitProjektPrueftAktivUndZugeordnet() {
        LocalDate tag = LocalDate.of(2026, 10, 21);

        Zeiteintrag eintrag = service.nachtragen(1, Rolle.MITARBEITER, tag, LocalTime.of(8, 0), LocalTime.of(12, 0),
                null, null, Standort.BUERO, TREPPE_LINDENSTRASSE);

        assertThat(eintrag.getProjektId()).isEqualTo(TREPPE_LINDENSTRASSE);
        assertThatThrownBy(() -> service.nachtragen(1, Rolle.MITARBEITER, tag, LocalTime.of(13, 0),
                LocalTime.of(15, 0), null, null, Standort.BUERO, LADENBAU_KRAUSE))
                .isInstanceOf(FachlicheException.class);
        verify(zeiteintragRepository).save(any());
    }

    @Test
    void auswahlZeigtNurAktiveZugeordneteProjekte() {
        assertThat(service.aktiveProjekte(JONAS_BECKER, Rolle.MITARBEITER))
                .extracting(ProjektInfo::projektId)
                .containsExactly(EINBAUKUECHE_WAGNER, TREPPE_LINDENSTRASSE);
        assertThatThrownBy(() -> service.aktiveProjekte(1, null)).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void startseiteZeigtDasProjektDesLaufendenEintrags() {
        Zeiteintrag laufend = new Zeiteintrag(1, JETZT.minusSeconds(3600), Standort.BUERO,
                EINBAUKUECHE_WAGNER);
        alsLaufend(laufend);

        assertThat(service.projektDesLaufendenEintrags(1, Rolle.MITARBEITER)).map(ProjektInfo::name)
                .contains("Einbauküche Wagner");
        assertThat(service.projektDesLaufendenEintrags(2, Rolle.PROJEKTLEITUNG)).isEmpty();
        assertThatThrownBy(() -> service.projektDesLaufendenEintrags(1, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void ueberschneidungMitAltemLaufendenEintragWirdErkannt() {
        // Am 19.10. eingestempelt und das Ausstempeln vergessen
        Zeiteintrag vergessen = eintragMitId(7, berlin(2026, 10, 19, 8, 0));
        when(zeiteintragRepository.findeUeberschneidende(1, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 12, 0)))
                .thenReturn(List.of(vergessen));

        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "12:00", null, null),
                "Der Eintrag überschneidet sich mit Ihrem Eintrag vom 19.10.2026 08:00 Uhr.");
    }

    // ---- Status abwesend und Hinweis zum Tag (FA-M2-05, FA-M2-21, FA-M2-27, UC-ZE-01 3c) ----

    @Test
    void ohneLaufendenEintragUndMitAbwesenheitIstDerStatusAbwesend() {
        when(abwesenheitService.istAbwesend(1, LocalDate.of(2026, 10, 22))).thenReturn(true);

        assertThat(service.status(1, Rolle.MITARBEITER)).isEqualTo(StempelStatus.ABWESEND);
    }

    @Test
    void ohneLaufendenEintragUndOhneAbwesenheitIstDerStatusNichtEingestempelt() {
        assertThat(service.status(1, Rolle.MITARBEITER)).isEqualTo(StempelStatus.NICHT_EINGESTEMPELT);
    }

    @Test
    void werTrotzAbwesenheitEinstempeltIstEingestempelt() {
        Zeiteintrag laufend = eintragMitId(5, JETZT.minusSeconds(600));
        when(abwesenheitService.istAbwesend(1, LocalDate.of(2026, 10, 22))).thenReturn(true);
        alsLaufend(laufend);

        assertThat(service.status(1, Rolle.MITARBEITER)).isEqualTo(StempelStatus.EINGESTEMPELT);
    }

    @Test
    void einstempelnAmFeiertagGehtMitHinweis() {
        when(feiertagService.istFeiertag(LocalDate.of(2026, 10, 22))).thenReturn(true);
        service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null);

        verify(zeiteintragRepository).save(any());
        assertThat(service.hinweisZumHeutigenTag(1, Rolle.MITARBEITER))
                .isEqualTo("Heute ist ein Feiertag. Ihre Arbeitszeit wird trotzdem erfasst.");
    }

    @Test
    void einstempelnBeiAbwesenheitGehtMitHinweis() {
        when(abwesenheitService.istAbwesend(1, LocalDate.of(2026, 10, 22))).thenReturn(true);
        service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null);

        verify(zeiteintragRepository).save(any());
        assertThat(service.hinweisZumHeutigenTag(1, Rolle.MITARBEITER)).isEqualTo(
                "Für heute ist eine genehmigte Abwesenheit eingetragen. Ihre Arbeitszeit wird trotzdem erfasst.");
    }

    @Test
    void amFeiertagMitAbwesenheitNenntDerHinweisDenFeiertag() {
        when(feiertagService.istFeiertag(LocalDate.of(2026, 10, 22))).thenReturn(true);
        when(abwesenheitService.istAbwesend(1, LocalDate.of(2026, 10, 22))).thenReturn(true);

        assertThat(service.hinweisZumHeutigenTag(1, Rolle.MITARBEITER)).startsWith("Heute ist ein Feiertag.");
    }

    @Test
    void anEinemNormalenTagGibtEsKeinenHinweis() {
        assertThat(service.hinweisZumHeutigenTag(1, Rolle.MITARBEITER)).isEmpty();
        assertThatThrownBy(() -> service.hinweisZumHeutigenTag(1, null)).isInstanceOf(NichtAngemeldetException.class);
    }

    // ---- Warnung Hoechstarbeitszeit bei laufendem Eintrag (FA-M2-15), Uhr: Do 22.10.2026 09:30 ----

    private void laeuftSeit(long mitarbeiterId, Instant beginn, Zeiteintrag... abgeschlossene) {
        Zeiteintrag laufend = eintragVon(mitarbeiterId, 99, beginn);
        alsLaufend(laufend);
        List<Zeiteintrag> alle = new ArrayList<>(List.of(abgeschlossene));
        alle.add(laufend);
        vorhandeneEintraege(mitarbeiterId, alle);
    }

    /** Wie die Datenbank: nur Eintraege mit Beginn im angefragten halboffenen Zeitraum [von, bis). */
    private void vorhandeneEintraege(long mitarbeiterId, List<Zeiteintrag> alle) {
        when(zeiteintragRepository.findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                eq(mitarbeiterId), any(), any()))
                .thenAnswer(aufruf -> imZeitraum(alle, aufruf.getArgument(1), aufruf.getArgument(2)));
    }

    /** Abgeschlossener Eintrag mit 60 Minuten Pause von 12 bis 13 Uhr (erfuellt jede Pausenregel). */
    private Zeiteintrag tagMitPause(long mitarbeiterId, long id, int tag, int endeStunde, int endeMinute) {
        Zeiteintrag eintrag = eintragVon(mitarbeiterId, id, berlin(2026, 10, tag, 7, 0));
        eintrag.ausstempeln(berlin(2026, 10, tag, endeStunde, endeMinute));
        when(pauseRepository.findByZeiteintrag_Id(id))
                .thenReturn(List.of(pause(eintrag, berlin(2026, 10, tag, 12, 0), berlin(2026, 10, tag, 13, 0))));
        return eintrag;
    }

    @Test
    void erwachseneWerdenDreissigMinutenVorZehnStundenGewarnt() {
        laeuftSeit(JONAS_BECKER, berlin(2026, 10, 21, 23, 15)); // 615 Minuten, 45 fehlende Pause: 570

        assertThat(service.warnungHoechstarbeitszeit(JONAS_BECKER, Rolle.MITARBEITER))
                .isEqualTo("In 30 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 10 Stunden am Tag.");
    }

    @Test
    void erwachseneWerdenEinundreissigMinutenVorderGrenzeNochNichtGewarnt() {
        laeuftSeit(JONAS_BECKER, berlin(2026, 10, 21, 23, 16)); // 614 - 45 = 569

        assertThat(service.warnungHoechstarbeitszeit(JONAS_BECKER, Rolle.MITARBEITER)).isEmpty();
    }

    @Test
    void laufenderEintragVonGesternAbendWirdAuchUeberMitternachtGewarnt() {
        // Am 21.10. um 23:14 eingestempelt: um 09:30 am 22.10. sind es 616 - 45 = 571 Minuten, zaehlt zum 21.10.
        laeuftSeit(JONAS_BECKER, berlin(2026, 10, 21, 23, 14));

        assertThat(service.warnungHoechstarbeitszeit(JONAS_BECKER, Rolle.MITARBEITER))
                .isEqualTo("In 29 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 10 Stunden am Tag.");
        verify(zeiteintragRepository).findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                JONAS_BECKER, Instant.parse("2026-10-20T22:00:00Z"), Instant.parse("2026-10-21T22:00:00Z"));
    }

    @Test
    void vergessenesAusstempelnVonGesternZeigtDieUeberschreitung() {
        laeuftSeit(JONAS_BECKER, berlin(2026, 10, 21, 22, 0)); // 690 Minuten

        assertThat(service.warnungHoechstarbeitszeit(JONAS_BECKER, Rolle.MITARBEITER))
                .isEqualTo("Sie haben an diesem Tag die erlaubte Höchstarbeitszeit von 10 Stunden überschritten.");
    }

    @Test
    void tagessummeAusFruehererUndLaufenderBuchungLoestDieWarnungAus() {
        long mehmet = MEHMET_YILMAZ;
        Zeiteintrag frueh = eintragVon(mehmet, 11, berlin(2026, 10, 22, 1, 0));
        frueh.ausstempeln(berlin(2026, 10, 22, 4, 0)); // 180 Minuten, danach 60 Minuten Luecke als Pause
        laeuftSeit(mehmet, berlin(2026, 10, 22, 5, 0), frueh); // 270 Minuten, zusammen 450

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER))
                .isEqualTo("In 30 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 8 Stunden am Tag.");
    }

    @Test
    void eintragVomVortagZaehltNichtZurTagessumme() {
        long jonas = JONAS_BECKER;
        Zeiteintrag gestern = tagMitPause(jonas, 11, 21, 18, 0);
        laeuftSeit(jonas, berlin(2026, 10, 22, 5, 0), gestern); // heute nur 270 Minuten

        assertThat(service.warnungHoechstarbeitszeit(jonas, Rolle.MITARBEITER)).isEmpty();
    }

    @Test
    void genauAnDerTagesgrenzeIstSieErreicht() {
        laeuftSeit(JONAS_BECKER, berlin(2026, 10, 21, 22, 45)); // 645 - 45 = 600

        assertThat(service.warnungHoechstarbeitszeit(JONAS_BECKER, Rolle.MITARBEITER))
                .isEqualTo("Sie haben die erlaubte Höchstarbeitszeit von 10 Stunden am Tag erreicht.");
    }

    @Test
    void genauAnDerWochengrenzeIstSieErreicht() {
        long mehmet = MEHMET_YILMAZ;
        // Mo, Di, Mi je 710 Minuten (07:00-19:50 mit 1 h Pause), Do laeuft 270 Minuten: 2400
        laeuftSeit(mehmet, berlin(2026, 10, 22, 5, 0), tagMitPause(mehmet, 11, 19, 19, 50),
                tagMitPause(mehmet, 12, 20, 19, 50), tagMitPause(mehmet, 13, 21, 19, 50));

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER))
                .isEqualTo("Sie haben die erlaubte Höchstarbeitszeit von 40 Stunden in der Woche erreicht.");
    }

    @Test
    void sonntagDerVorwocheZaehltNichtZurWochensumme() {
        long mehmet = MEHMET_YILMAZ;
        // Sonntag 18.10. zaehlt nicht. Di und Mi je 840 Minuten, Do laeuft 270 Minuten: 1950
        laeuftSeit(mehmet, berlin(2026, 10, 22, 5, 0), tagMitPause(mehmet, 11, 18, 22, 0),
                tagMitPause(mehmet, 12, 20, 22, 0), tagMitPause(mehmet, 13, 21, 22, 0));

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER)).isEmpty();
    }

    @Test
    void montagZaehltZurWochensumme() {
        long mehmet = MEHMET_YILMAZ;
        // Mo, Di, Mi je 700 Minuten (07:00-19:40 mit 1 h Pause), Do laeuft 270 Minuten: 2370
        laeuftSeit(mehmet, berlin(2026, 10, 22, 5, 0), tagMitPause(mehmet, 11, 19, 19, 40),
                tagMitPause(mehmet, 12, 20, 19, 40), tagMitPause(mehmet, 13, 21, 19, 40));

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER))
                .isEqualTo("In 30 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 40 Stunden in der Woche.");
    }

    @Test
    void sonntagDerselbenWocheZaehltImHinweisZurWochensumme() {
        long mehmet = MEHMET_YILMAZ;
        // Di 20.10. und Mi 21.10. je 840 Minuten, So 25.10. 840 Minuten: nur mit Sonntag ueber 2400
        Zeiteintrag dienstag = tagMitPause(mehmet, 11, 20, 22, 0);
        Zeiteintrag sonntag = tagMitPause(mehmet, 13, 25, 22, 0);
        vorhandeneEintraege(mehmet, List.of(dienstag, tagMitPause(mehmet, 12, 21, 22, 0), sonntag));

        assertThat(service.hinweis(dienstag.getMitarbeiterId(), Rolle.MITARBEITER, dienstag))
                .contains("Sie haben in dieser Woche die erlaubte Höchstarbeitszeit von 40 Stunden überschritten.");
    }

    @Test
    void ohneLaufendenEintragGibtEsKeineWarnung() {
        assertThat(service.warnungHoechstarbeitszeit(1, Rolle.MITARBEITER)).isEmpty();
        assertThatThrownBy(() -> service.warnungHoechstarbeitszeit(1, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void minderjaehrigeWerdenDreissigMinutenVorAchtStundenGewarnt() {
        laeuftSeit(MEHMET_YILMAZ, berlin(2026, 10, 22, 1, 0)); // 510 - 60 = 450

        assertThat(service.warnungHoechstarbeitszeit(MEHMET_YILMAZ, Rolle.MITARBEITER))
                .isEqualTo("In 30 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 8 Stunden am Tag.");
    }

    @Test
    void minderjaehrigeSehenDieUeberschreitungDerTagesgrenze() {
        laeuftSeit(MEHMET_YILMAZ, berlin(2026, 10, 22, 0, 29)); // 541 - 60 = 481

        assertThat(service.warnungHoechstarbeitszeit(MEHMET_YILMAZ, Rolle.MITARBEITER))
                .isEqualTo("Sie haben an diesem Tag die erlaubte Höchstarbeitszeit von 8 Stunden überschritten.");
    }

    @Test
    void minderjaehrigeWerdenDreissigMinutenVorVierzigWochenstundenGewarnt() {
        long mehmet = MEHMET_YILMAZ;
        // Mo bis Mi je 07:00-18:20 mit 1 h Pause = 3 x 620 Minuten, Do laeuft seit 00:00 = 570 - 60: 2370
        laeuftSeit(mehmet, berlin(2026, 10, 22, 0, 0), tagMitPause(mehmet, 11, 19, 18, 20),
                tagMitPause(mehmet, 12, 20, 18, 20), tagMitPause(mehmet, 13, 21, 18, 20));

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER))
                .contains("In 30 Minuten erreichen Sie die erlaubte Höchstarbeitszeit von 40 Stunden in der Woche.");
    }

    @Test
    void minderjaehrigeSehenDieUeberschreitungDerWochengrenze() {
        long mehmet = MEHMET_YILMAZ;
        // 3 x 631 + 510 = 2403 Minuten
        laeuftSeit(mehmet, berlin(2026, 10, 22, 0, 0), tagMitPause(mehmet, 11, 19, 18, 31),
                tagMitPause(mehmet, 12, 20, 18, 31), tagMitPause(mehmet, 13, 21, 18, 31));

        assertThat(service.warnungHoechstarbeitszeit(mehmet, Rolle.MITARBEITER))
                .contains("Sie haben in dieser Woche die erlaubte Höchstarbeitszeit von 40 Stunden überschritten.");
    }

    @Test
    void erwachseneHabenKeineWochengrenze() {
        long jonas = JONAS_BECKER;
        laeuftSeit(jonas, berlin(2026, 10, 22, 6, 0), tagMitPause(jonas, 11, 19, 18, 31),
                tagMitPause(jonas, 12, 20, 18, 31), tagMitPause(jonas, 13, 21, 18, 31));

        assertThat(service.warnungHoechstarbeitszeit(jonas, Rolle.MITARBEITER)).doesNotContain("Woche");
    }

    @Test
    void nachDemNachtragenNenntDerHinweisDieUeberschritteneWochengrenzeFuerMinderjaehrige() {
        long mehmet = MEHMET_YILMAZ;
        // Mo bis Mi je 07:00-22:00 mit 1 h Pause = 3 x 840 = 2520 Minuten
        Zeiteintrag montag = tagMitPause(mehmet, 11, 19, 22, 0);
        Zeiteintrag dienstag = tagMitPause(mehmet, 12, 20, 22, 0);
        Zeiteintrag mittwoch = tagMitPause(mehmet, 13, 21, 22, 0);
        vorhandeneEintraege(mehmet, List.of(montag, dienstag, mittwoch));

        assertThat(service.hinweis(mittwoch.getMitarbeiterId(), Rolle.MITARBEITER, mittwoch))
                .contains("Sie haben in dieser Woche die erlaubte Höchstarbeitszeit von 40 Stunden überschritten.");
    }

    @Test
    void nachtragenMitUeberschneidungSpeichertNichts() {
        Zeiteintrag vorhanden = eintragMitId(5, berlin(2026, 10, 21, 7, 0));
        vorhanden.ausstempeln(berlin(2026, 10, 21, 9, 0));
        when(zeiteintragRepository.findeUeberschneidende(1, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 12, 0)))
                .thenReturn(List.of(vorhanden));

        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "12:00", null, null),
                "Der Eintrag überschneidet sich mit Ihrem Eintrag vom 21.10.2026 07:00 Uhr.");
    }

    @Test
    void zeitenMitBisVorVonGehtNicht() {
        assertThatThrownBy(() -> service.zeiten(1, Rolle.MITARBEITER, 1,
                LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 20)))
                .isInstanceOf(FachlicheException.class);
    }

    @Test
    void nettoAmTagDerSommerzeitumstellungHatEineStundeWeniger() {
        // 29.03.2026: 01:00 bis 04:00 Uhr Ortszeit sind nur zwei Stunden
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 3, 29, 1, 0));
        eintrag.ausstempeln(berlin(2026, 3, 29, 4, 0));

        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void nettoAmTagDerWinterzeitumstellungHatEineStundeMehr() {
        // 25.10.2026: 01:00 bis 04:00 Uhr Ortszeit sind vier Stunden
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 25, 1, 0));
        eintrag.ausstempeln(berlin(2026, 10, 25, 4, 0));

        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void nachtragenAmUmstellungstagRechnetMitEchterDauer() {
        Zeiteintrag eintrag = nachtragenMit(LocalDate.of(2026, 3, 29), "01:00", "04:00", null, null);

        assertThat(eintrag.getBeginn()).isEqualTo(Instant.parse("2026-03-29T00:00:00Z"));
        assertThat(eintrag.getEnde()).isEqualTo(Instant.parse("2026-03-29T02:00:00Z"));
        assertThat(Duration.between(eintrag.getBeginn(), eintrag.getEnde())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void eintragUeberMitternachtZaehltZumTagDesBeginns() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 20, 22, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 2, 0));

        assertThat(service.tagDesBeginns(eintrag)).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void tagesgrenzenSindBerlinerMitternacht() {
        service.eintraegeImZeitraum(1, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21));

        verify(zeiteintragRepository).findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(1,
                Instant.parse("2026-10-20T22:00:00Z"), Instant.parse("2026-10-21T22:00:00Z"));
    }

    @Test
    void tagDerWinterzeitumstellungHatFuenfundzwanzigStunden() {
        service.eintraegeImZeitraum(1, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25));

        verify(zeiteintragRepository).findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(1,
                Instant.parse("2026-10-24T22:00:00Z"), Instant.parse("2026-10-25T23:00:00Z"));
    }

    @Test
    void projektleitungLiestZeitenDerMitarbeiterIhrerProjekte() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        List<Zeiteintrag> zeiten = service.zeiten(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG,
                JONAS_BECKER, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 21));

        assertThat(zeiten).containsExactly(eintrag);
    }

    @Test
    void fremdeZeitenSiehtNurDieZustaendigeProjektleitung() {
        LocalDate tag = LocalDate.of(2026, 10, 21);

        assertThatThrownBy(() -> service.zeiten(FELIX_BRANDT, Rolle.MITARBEITER,
                JONAS_BECKER, tag, tag)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zeiten(SABINE_ENGEL, Rolle.PROJEKTLEITUNG,
                JONAS_BECKER, tag, tag)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zeiten(PETRA_KRUEGER, Rolle.ADMIN,
                JONAS_BECKER, tag, tag)).isInstanceOf(ZugriffVerweigertException.class);
        verify(zeiteintragRepository, never())
                .findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(anyLong(), any(), any());
    }

    @Test
    void stempelnWirktImmerNurAufDieAngemeldetePerson() {
        Zeiteintrag eintrag = service.einstempeln(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, Standort.BUERO, null);

        assertThat(eintrag.getMitarbeiterId()).isEqualTo(PAUL_HARTMANN);
    }

    @Test
    void schreibendeAktionenSperrenZuerstDenMitarbeiter() {
        InOrder reihenfolge = inOrder(zeiteintragRepository);

        service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null);

        reihenfolge.verify(zeiteintragRepository).findFirstByMitarbeiterIdOrderByBeginnDesc(1);
        reihenfolge.verify(zeiteintragRepository).findByMitarbeiterIdAndEndeIsNull(1);
    }

    @Test
    void nachtragenSperrtVorDerPruefungAufUeberschneidung() {
        InOrder reihenfolge = inOrder(zeiteintragRepository);

        nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "12:00", null, null);

        reihenfolge.verify(zeiteintragRepository).findFirstByMitarbeiterIdOrderByBeginnDesc(1);
        reihenfolge.verify(zeiteintragRepository).findeUeberschneidende(eq(1L), any(), any());
    }

    // ---- Pausenregel je Tag (Entscheidung Noah 27.11.2026) ----

    private Zeiteintrag beendet(long id, Instant beginn, Instant ende) {
        Zeiteintrag eintrag = eintragMitId(id, beginn);
        eintrag.ausstempeln(ende);
        return eintrag;
    }

    @Test
    void zweiEintraegeEinesTagesVerlangenZusammenEinePauseVomSpaeterenAbgezogen() {
        // 08:00-12:00 und 12:10-16:10: 8 h Arbeit, 10 Minuten Luecke zaehlen nicht
        Zeiteintrag vormittag = beendet(5, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 12, 0));
        Zeiteintrag nachmittag = beendet(6, berlin(2026, 10, 21, 12, 10), berlin(2026, 10, 21, 16, 10));

        assertThat(service.nettoArbeitszeit(vormittag)).isEqualTo(Duration.ofHours(4));
        assertThat(service.nettoArbeitszeit(nachmittag)).isEqualTo(Duration.ofMinutes(210));
        assertThat(service.hinweis(nachmittag.getMitarbeiterId(), Rolle.MITARBEITER, nachmittag)).contains("An diesem Tag fehlen 30 Minuten Pause");
    }

    @Test
    void lueckeAbFuenfzehnMinutenZwischenEintraegenZaehltAlsPause() {
        Zeiteintrag vormittag = beendet(5, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 12, 0));
        Zeiteintrag nachmittag = beendet(6, berlin(2026, 10, 21, 12, 30), berlin(2026, 10, 21, 16, 30));

        assertThat(service.fehlendePauseMinuten(vormittag)).isZero();
        assertThat(service.fehlendePauseMinuten(nachmittag)).isZero();
        assertThat(service.hinweis(nachmittag.getMitarbeiterId(), Rolle.MITARBEITER, nachmittag)).isEmpty();
    }

    @Test
    void eintraegeVerschiedenerTageWerdenNichtZusammengezaehlt() {
        Zeiteintrag dienstag = beendet(5, berlin(2026, 10, 20, 20, 0), berlin(2026, 10, 21, 0, 0));
        Zeiteintrag mittwoch = beendet(6, berlin(2026, 10, 21, 0, 0), berlin(2026, 10, 21, 4, 0));

        assertThat(service.fehlendePauseMinuten(dienstag)).isZero();
        assertThat(service.fehlendePauseMinuten(mittwoch)).isZero();
    }

    @Test
    void istMinutenZaehltNurAbgeschlosseneEintraegeNachDemPflichtabzug() {
        beendet(5, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 15, 0));
        eintragMitId(6, berlin(2026, 10, 22, 8, 0));

        assertThat(service.istMinuten(1, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22))).isEqualTo(390);
    }

    @Test
    void uebersichtSummiertJeTagUndJeWoche() {
        beendet(5, berlin(2026, 10, 18, 8, 0), berlin(2026, 10, 18, 10, 0));
        beendet(6, berlin(2026, 10, 19, 8, 0), berlin(2026, 10, 19, 12, 0));
        beendet(7, berlin(2026, 10, 19, 12, 0), berlin(2026, 10, 19, 15, 0));
        beendet(8, berlin(2026, 10, 20, 22, 0), berlin(2026, 10, 21, 1, 0));

        Zeitenuebersicht uebersicht = service.uebersicht(1, Rolle.MITARBEITER, 1, LocalDate.of(2026, 10, 18),
                LocalDate.of(2026, 10, 22));

        // 19.10.: 7 h ohne Pause = 390; die Nachtschicht zaehlt ganz zum 20.10.; der 18.10. ist ein Sonntag
        assertThat(uebersicht.wochen()).extracting(UebersichtWoche::kalenderwoche, UebersichtWoche::summe)
                .containsExactly(tuple(42, Duration.ofHours(2)), tuple(43, Duration.ofMinutes(570)));
        assertThat(uebersicht.wochen()).flatExtracting(UebersichtWoche::tage)
                .extracting(UebersichtTag::datum, UebersichtTag::summe)
                .containsExactly(tuple(LocalDate.of(2026, 10, 18), Duration.ofHours(2)),
                        tuple(LocalDate.of(2026, 10, 19), Duration.ofMinutes(390)),
                        tuple(LocalDate.of(2026, 10, 20), Duration.ofHours(3)));
        UebersichtTag montag = uebersicht.wochen().get(1).tage().get(0);
        assertThat(montag.eintraege()).extracting(UebersichtEintrag::netto, UebersichtEintrag::fehlendePauseMinuten)
                .containsExactly(tuple(Duration.ofHours(4), 0), tuple(Duration.ofMinutes(150), 30));
        assertThat(uebersicht.summe()).isEqualTo(Duration.ofMinutes(690));
    }

    @Test
    void uebersichtLiefertPausenUndBeiLaufendemEintragKeinenAbzug() {
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 10, 22, 1, 0));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(laufend, berlin(2026, 10, 22, 2, 0), berlin(2026, 10, 22, 2, 10))));
        LocalDate heute = LocalDate.of(2026, 10, 22);

        UebersichtEintrag eintrag = service.uebersicht(1, Rolle.MITARBEITER, 1, heute, heute)
                .wochen().get(0).tage().get(0).eintraege().get(0);

        // 01:00 bis 09:30 abzueglich 10 Minuten Pause
        assertThat(eintrag.pausen()).isEqualTo(Duration.ofMinutes(10));
        assertThat(eintrag.netto()).isEqualTo(Duration.ofMinutes(500));
        assertThat(eintrag.fehlendePauseMinuten()).isZero();
    }

    @Test
    void uebersichtNenntDenProjektnamenUndLaesstUnbekannteProjekteOhneNamen() {
        LocalDate tag = LocalDate.of(2026, 10, 21);
        mitProjekt(5, berlin(2026, 10, 21, 8, 0), EINBAUKUECHE_WAGNER);
        mitProjekt(6, berlin(2026, 10, 21, 10, 0), 9999L);
        beendet(7, berlin(2026, 10, 21, 12, 0), berlin(2026, 10, 21, 13, 0));

        List<UebersichtEintrag> eintraege = service.uebersicht(1, Rolle.MITARBEITER, 1, tag, tag)
                .wochen().get(0).tage().get(0).eintraege();

        assertThat(eintraege).extracting(UebersichtEintrag::projektName)
                .containsExactly("Einbauküche Wagner", null, null);
    }

    private void mitProjekt(long id, Instant beginn, long projektId) {
        Zeiteintrag eintrag = spy(new Zeiteintrag(JONAS_BECKER, beginn, Standort.BUERO, projektId));
        doReturn(id).when(eintrag).getId();
        eintrag.ausstempeln(beginn.plus(Duration.ofHours(1)));
        angelegte.add(eintrag);
    }

    @Test
    void uebersichtMarkiertFeiertageUndAbwesenheitenAuchOhneZeiteintrag() {
        LocalDate montag = LocalDate.of(2026, 10, 19);
        LocalDate sonntag = montag.plusDays(6);
        beendet(5, berlin(2026, 10, 19, 8, 0), berlin(2026, 10, 19, 10, 0));
        when(feiertagService.imZeitraum(Rolle.MITARBEITER, montag, sonntag))
                .thenReturn(List.of(new Feiertag(montag, "Testfeiertag")));
        when(abwesenheitService.abwesenheitsTage(1, 1, montag, sonntag))
                .thenReturn(List.of(new AbwesenheitsTag(montag.plusDays(4), AbwesenheitsArt.URLAUB)));

        Zeitenuebersicht uebersicht = service.uebersicht(1, Rolle.MITARBEITER, 1, montag, sonntag);

        List<UebersichtTag> tage = uebersicht.wochen().get(0).tage();
        assertThat(tage).extracting(UebersichtTag::datum).containsExactly(montag, montag.plusDays(4));
        assertThat(tage.get(0).feiertag()).isEqualTo("Testfeiertag");
        assertThat(tage.get(0).abwesenheit()).isNull();
        assertThat(tage.get(0).eintraege()).hasSize(1);
        assertThat(tage.get(1).feiertag()).isNull();
        assertThat(tage.get(1).abwesenheit().art()).isEqualTo(AbwesenheitsArt.URLAUB);
        assertThat(tage.get(1).summe()).isEqualTo(Duration.ZERO);
        assertThat(uebersicht.summe()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void uebersichtFragtAbwesenheitenMitDerAngemeldetenPersonAlsBetrachterAb() {
        // Davon haengt ab, wer die Art der Abwesenheit sieht (FA-M2-20, QA-M2-07)
        long paul = PAUL_HARTMANN;
        long lena = LENA_SCHROEDER;
        LocalDate tag = LocalDate.of(2026, 10, 21);
        when(abwesenheitService.abwesenheitsTage(paul, lena, tag, tag))
                .thenReturn(List.of(new AbwesenheitsTag(tag, null)));

        Zeitenuebersicht uebersicht = service.uebersicht(paul, Rolle.PROJEKTLEITUNG, lena, tag, tag);

        assertThat(uebersicht.wochen()).flatExtracting(UebersichtWoche::tage)
                .extracting(UebersichtTag::abwesenheit)
                .containsExactly(new AbwesenheitsTag(tag, null));
        verify(abwesenheitService).abwesenheitsTage(paul, lena, tag, tag);
    }

    @Test
    void uebersichtOhneTrefferIstLeerUndFremdeNurFuerDieProjektleitung() {
        LocalDate tag = LocalDate.of(2026, 10, 21);

        Zeitenuebersicht leer = service.uebersicht(1, Rolle.MITARBEITER, 1, tag, tag);

        assertThat(leer.wochen()).isEmpty();
        assertThat(leer.summe()).isEqualTo(Duration.ZERO);
        assertThatThrownBy(() -> service.uebersicht(1, Rolle.MITARBEITER, LENA_SCHROEDER,
                tag, tag)).isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void hinweisGibtEsNurZuEigenenEintraegenUndNurMitAnmeldung() {
        Zeiteintrag eintrag = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        eintrag.ausstempeln(berlin(2026, 10, 21, 15, 0));

        assertThatThrownBy(() -> service.hinweis(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, eintrag))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.hinweis(1, null, eintrag))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void startseiteZaehltDenSeitGesternLaufendenEintragMit() {
        // Seit gestern 23:00, jetzt 09:30, Pause 02:00 bis 02:30
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 10, 21, 23, 0));
        alsLaufend(laufend);
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(laufend, berlin(2026, 10, 22, 2, 0), berlin(2026, 10, 22, 2, 30))));

        assertThat(service.arbeitszeitHeute(1, Rolle.MITARBEITER)).isEqualTo(Duration.ofHours(10));
        assertThat(service.pausenHeute(1, Rolle.MITARBEITER)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void heuteBegonnenerLaufenderEintragZaehltNurEinmal() {
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 10, 22, 8, 0));
        alsLaufend(laufend);

        assertThat(service.arbeitszeitHeute(1, Rolle.MITARBEITER)).isEqualTo(Duration.ofMinutes(90));
    }

    // ---- Minutengenaue Serverzeit (RB-M2-01) ----

    private ZeiteintragService serviceUm(String zeitpunkt) {
        return new ZeiteintragService(zeiteintragRepository, pauseRepository, new PausenRegelService(),
                new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), new FakeProjektAuskunft(), abwesenheitService,
                feiertagService, new ZugriffsPruefung(new FakeProjektAuskunft()),
                Clock.fixed(Instant.parse(zeitpunkt), BERLIN));
    }

    @Test
    void einstempelnUndPauseStartenSpeichernOhneSekunden() {
        ZeiteintragService mitSekunden = serviceUm("2026-10-22T07:30:59.900Z");
        assertThat(mitSekunden.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, null).getBeginn()).isEqualTo(JETZT);

        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 10, 22, 8, 0));
        alsLaufend(laufend);
        assertThat(mitSekunden.pauseStarten(1, Rolle.MITARBEITER).getBeginn()).isEqualTo(JETZT);
    }

    @Test
    void pauseBeendenUndAusstempelnSpeichernOhneSekunden() {
        ZeiteintragService mitSekunden = serviceUm("2026-10-22T07:30:45Z");
        Zeiteintrag laufend = eintragMitId(5, berlin(2026, 10, 22, 8, 0));
        Pause offen = new Pause(laufend, berlin(2026, 10, 22, 9, 0));
        alsLaufend(laufend);
        when(pauseRepository.findByZeiteintrag_IdAndEndeIsNull(5)).thenReturn(Optional.of(offen));

        mitSekunden.pauseBeenden(1, Rolle.MITARBEITER);
        when(pauseRepository.findByZeiteintrag_IdAndEndeIsNull(5)).thenReturn(Optional.empty());
        mitSekunden.ausstempeln(1, Rolle.MITARBEITER);

        assertThat(offen.getEnde()).isEqualTo(JETZT);
        assertThat(laufend.getEnde()).isEqualTo(JETZT);
    }

    @Test
    void nachtragenMitPausenendeGleichPausenbeginnSpeichertNichts() {
        assertAbgelehnt(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", "16:00", "12:00", "12:00"),
                "Das Ende der Pause muss nach ihrem Beginn liegen.");
    }

    @Test
    void abzugAmSelbenTagTrifftZuerstDenLaufendenEintrag() {
        // 02:15-09:15 beendet (420), seit 09:20 laufend (10): 30 fehlen, 10 vom laufenden, 20 vom frueheren
        Zeiteintrag frueh = beendet(5, berlin(2026, 10, 22, 2, 15), berlin(2026, 10, 22, 9, 15));
        Zeiteintrag laufend = eintragMitId(6, berlin(2026, 10, 22, 9, 20));
        alsLaufend(laufend);
        LocalDate heute = LocalDate.of(2026, 10, 22);

        assertThat(service.fehlendePauseMinuten(frueh)).isEqualTo(20);
        assertThat(service.istMinuten(1, heute, heute)).isEqualTo(400);
        assertThat(service.arbeitszeitHeute(1, Rolle.MITARBEITER)).isEqualTo(Duration.ofMinutes(410));
    }

    @Test
    void nachtragenSchneidetSekundenAb() {
        Zeiteintrag eintrag = nachtragenMit(LocalDate.of(2026, 10, 21), "08:00:40", "12:00:59", null, null);

        assertThat(eintrag.getBeginn()).isEqualTo(berlin(2026, 10, 21, 8, 0));
        assertThat(eintrag.getEnde()).isEqualTo(berlin(2026, 10, 21, 12, 0));
    }

    // ---- Pausen ausserhalb des Eintrags (nach einer Korrektur) ----

    @Test
    void pauseTeilweiseAusserhalbZaehltNurMitDemTeilImEintrag() {
        // Eintrag 12:30-16:00, Pause 12:00-13:00: nur 30 Minuten liegen im Eintrag
        Zeiteintrag eintrag = beendet(5, berlin(2026, 10, 21, 12, 30), berlin(2026, 10, 21, 16, 0));
        when(pauseRepository.findByZeiteintrag_Id(5)).thenReturn(List.of(
                pause(eintrag, berlin(2026, 10, 21, 12, 0), berlin(2026, 10, 21, 13, 0))));

        assertThat(service.pausen(eintrag)).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.nettoArbeitszeit(eintrag)).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void pauseGanzAusserhalbZaehltNichtUndAendertKeinenAnderenEintrag() {
        // Der zweite Eintrag wurde auf 13:00-13:30 gekuerzt, seine Pause 14:00-15:00 liegt ausserhalb
        Zeiteintrag vormittag = beendet(5, berlin(2026, 10, 21, 8, 0), berlin(2026, 10, 21, 12, 0));
        Zeiteintrag gekuerzt = beendet(6, berlin(2026, 10, 21, 13, 0), berlin(2026, 10, 21, 13, 30));
        when(pauseRepository.findByZeiteintrag_Id(6)).thenReturn(List.of(
                pause(gekuerzt, berlin(2026, 10, 21, 14, 0), berlin(2026, 10, 21, 15, 0))));

        assertThat(service.pausen(gekuerzt)).isEqualTo(Duration.ZERO);
        assertThat(service.nettoArbeitszeit(gekuerzt)).isEqualTo(Duration.ofMinutes(30));
        assertThat(service.nettoArbeitszeit(vormittag)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void uebersichtNurMitAnmeldungUndNichtFuerDenAdmin() {
        LocalDate tag = LocalDate.of(2026, 10, 21);
        long lena = LENA_SCHROEDER;

        assertThat(service.uebersicht(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, lena, tag, tag).wochen()).isEmpty();
        assertThatThrownBy(() -> service.uebersicht(PETRA_KRUEGER, Rolle.ADMIN, lena, tag, tag))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.uebersicht(lena, null, lena, tag, tag))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void fehlenderStandortBeimEinstempelnGehoertZumStandortfeld() {
        assertFelder(() -> service.einstempeln(1, Rolle.MITARBEITER, null, null), Feld.STANDORT);
    }

    @Test
    void nichtMehrWaehlbaresProjektGehoertZumProjektfeld() {
        assertFelder(() -> service.einstempeln(1, Rolle.MITARBEITER, Standort.BUERO, 9999L), Feld.PROJEKT);
    }

    @Test
    void fehlendePflichtangabenBeimNachtragenNennenNurDieLeerenFelder() {
        assertFelder(() -> service.nachtragen(1, Rolle.MITARBEITER, null, null, null, null, null, null, null),
                Feld.DATUM, Feld.BEGINN, Feld.ENDE, Feld.STANDORT);
        assertFelder(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "08:00", null, null, null), Feld.ENDE);
    }

    @Test
    void falscheZeitenBeimNachtragenNennenDasBetroffeneFeld() {
        LocalDate gestern = LocalDate.of(2026, 10, 21);

        assertFelder(() -> nachtragenMit(gestern, "07:00", "06:30", null, null), Feld.ENDE);
        assertFelder(() -> nachtragenMit(gestern.plusDays(2), "07:00", "08:00", null, null), Feld.ENDE);
        assertFelder(() -> nachtragenMit(LocalDate.of(2025, 12, 31), "07:00", "08:00", null, null), Feld.DATUM);
    }

    @Test
    void falschePausenBeimNachtragenNennenDasBetroffeneFeld() {
        LocalDate gestern = LocalDate.of(2026, 10, 21);

        assertFelder(() -> nachtragenMit(gestern, "07:00", "15:00", null, "12:00"), Feld.PAUSE_VON);
        assertFelder(() -> nachtragenMit(gestern, "07:00", "15:00", "12:00", null), Feld.PAUSE_BIS);
        assertFelder(() -> nachtragenMit(gestern, "07:00", "15:00", "12:00", "11:00"), Feld.PAUSE_BIS);
        assertFelder(() -> nachtragenMit(gestern, "07:00", "15:00", "06:00", "08:00"), Feld.PAUSE_VON, Feld.PAUSE_BIS);
    }

    @Test
    void ueberschneidungBeimNachtragenNenntBeginnUndEnde() {
        Zeiteintrag vorhandener = eintragMitId(5, berlin(2026, 10, 21, 8, 0));
        when(zeiteintragRepository.findeUeberschneidende(anyLong(), any(), any())).thenReturn(List.of(vorhandener));

        assertFelder(() -> nachtragenMit(LocalDate.of(2026, 10, 21), "07:00", "09:00", null, null),
                Feld.BEGINN, Feld.ENDE);
    }

    @Test
    void uebersichtNurMitFeiertagHatKeineZeiteintraege() {
        LocalDate montag = LocalDate.of(2026, 10, 19);
        when(feiertagService.imZeitraum(Rolle.MITARBEITER, montag, montag))
                .thenReturn(List.of(new Feiertag(montag, "Testfeiertag")));

        Zeitenuebersicht uebersicht = service.uebersicht(1, Rolle.MITARBEITER, 1, montag, montag);

        assertThat(uebersicht.wochen()).isNotEmpty();
        assertThat(uebersicht.ohneZeiteintraege()).isTrue();
    }

    @Test
    void uebersichtMitEinemEintragHatZeiteintraege() {
        LocalDate montag = LocalDate.of(2026, 10, 19);
        beendet(5, berlin(2026, 10, 19, 8, 0), berlin(2026, 10, 19, 10, 0));

        assertThat(service.uebersicht(1, Rolle.MITARBEITER, 1, montag, montag).ohneZeiteintraege()).isFalse();
    }

    private Zeiteintrag nachtragenMit(LocalDate datum, String beginn, String ende, String pauseVon, String pauseBis) {
        return service.nachtragen(1, Rolle.MITARBEITER, datum, uhrzeit(beginn), uhrzeit(ende), uhrzeit(pauseVon),
                uhrzeit(pauseBis), Standort.BUERO, null);
    }

    private static LocalTime uhrzeit(String text) {
        return text == null ? null : LocalTime.parse(text);
    }

    private void alsLaufend(Zeiteintrag eintrag) {
        when(zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(eintrag.getMitarbeiterId()))
                .thenReturn(Optional.of(eintrag));
    }

    private void assertAbgelehnt(ThrowingCallable aktion, String meldung) {
        assertThatThrownBy(aktion).isInstanceOf(FachlicheException.class).hasMessage(meldung);
        verify(zeiteintragRepository, never()).save(any());
    }

    private void assertFelder(ThrowingCallable aktion, Feld... felder) {
        assertThatThrownBy(aktion).isInstanceOfSatisfying(EingabeFehler.class,
                fehler -> assertThat(fehler.felder()).containsExactlyInAnyOrder(felder));
        verify(zeiteintragRepository, never()).save(any());
    }

    @Test
    void adminKannNichtStempelnNachtragenOderZeitenSehen() {
        long admin = PETRA_KRUEGER;
        LocalDate tag = LocalDate.of(2026, 10, 21);
        List<Runnable> aufrufe = List.of(
                () -> service.einstempeln(admin, Rolle.ADMIN, Standort.BUERO, null),
                () -> service.ausstempeln(admin, Rolle.ADMIN),
                () -> service.pauseStarten(admin, Rolle.ADMIN),
                () -> service.pauseBeenden(admin, Rolle.ADMIN),
                () -> service.nachtragen(admin, Rolle.ADMIN, tag, LocalTime.of(8, 0), LocalTime.of(12, 0), null, null,
                        Standort.BUERO, null),
                () -> service.aktiveProjekte(admin, Rolle.ADMIN),
                () -> service.status(admin, Rolle.ADMIN),
                () -> service.zeiten(admin, Rolle.ADMIN, admin, tag, tag),
                () -> service.uebersicht(admin, Rolle.ADMIN, admin, tag, tag));

        for (Runnable aufruf : aufrufe) {
            assertThatThrownBy(aufruf::run)
                    .isInstanceOf(ZugriffVerweigertException.class)
                    .hasMessage("Zeiten und Zeitkonten gibt es nur für Mitarbeiter und Projektleitungen.");
        }
        verify(zeiteintragRepository, never()).save(any());
    }

    @Test
    void zeitraumFehlerNennenDasFeld() {
        LocalDate tag = LocalDate.of(2026, 10, 21);
        long jonas = JONAS_BECKER;

        assertThatThrownBy(() -> service.zeiten(jonas, Rolle.MITARBEITER, jonas, null, tag))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactly(Feld.VON));
        assertThatThrownBy(() -> service.zeiten(jonas, Rolle.MITARBEITER, jonas, null, null))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactlyInAnyOrder(Feld.VON, Feld.BIS));
        assertThatThrownBy(() -> service.zeiten(jonas, Rolle.MITARBEITER, jonas, tag, tag.minusDays(1)))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactly(Feld.BIS));
    }

    @Test
    void dauerDesLaufendenEintragsZaehltOhneFruehereEintraegeUndPausen() {
        long jonas = JONAS_BECKER;
        // Heute (22.10., 09:30 Uhr): 05:00 bis 07:00 abgeschlossen, seit 08:00 laufend mit 15 Minuten Pause
        Zeiteintrag frueher = eintragVon(jonas, 1, JETZT.minusSeconds(270 * 60));
        frueher.ausstempeln(JETZT.minusSeconds(150 * 60));
        Zeiteintrag laufend = eintragVon(jonas, 2, JETZT.minusSeconds(90 * 60));
        Pause pause = new Pause(laufend, JETZT.minusSeconds(60 * 60));
        pause.pauseBeenden(JETZT.minusSeconds(45 * 60));
        when(pauseRepository.findByZeiteintrag_Id(2L)).thenReturn(List.of(pause));
        alsLaufend(laufend);

        assertThat(service.dauerLaufenderEintrag(jonas, Rolle.MITARBEITER)).contains(Duration.ofMinutes(75));
        assertThat(service.arbeitszeitHeute(jonas, Rolle.MITARBEITER)).isEqualTo(Duration.ofMinutes(195));
    }

    @Test
    void ohneLaufendenEintragGibtEsKeineLaufendeDauer() {
        assertThat(service.dauerLaufenderEintrag(1, Rolle.MITARBEITER)).isEmpty();
        assertThatThrownBy(() -> service.dauerLaufenderEintrag(1, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.dauerLaufenderEintrag(3, Rolle.ADMIN))
                .isInstanceOf(ZugriffVerweigertException.class);
    }
}
