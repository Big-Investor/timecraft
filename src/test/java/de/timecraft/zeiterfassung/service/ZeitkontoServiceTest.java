package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.ANNA_VOGT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.ZeitkontoInfo;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Abwesenheit;
import de.timecraft.zeiterfassung.model.ZeitkontoMonat;
import de.timecraft.zeiterfassung.model.ZeitkontoZeile;
import de.timecraft.zeiterfassung.model.Zeitkontouebersicht;
import de.timecraft.zeiterfassung.repository.AbwesenheitRepository;

/**
 * Jonas Becker (Fake): 40 Stunden, Montag bis Freitag, Konto ab 01.01.2026, Startsaldo 0.
 *
 * @author Noah Cipa
 */
class ZeitkontoServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Clock UHR_OKTOBER = Clock.fixed(Instant.parse("2026-10-22T07:30:00Z"), BERLIN);
    private static final Clock UHR_JANUAR = Clock.fixed(Instant.parse("2026-01-07T07:30:00Z"), BERLIN);
    private static final Set<DayOfWeek> MONTAG_BIS_FREITAG = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> MONTAG_BIS_DONNERSTAG = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);

    private final ZeiteintragService zeiteintragService = mock(ZeiteintragService.class);
    private final FeiertagService feiertagService = mock(FeiertagService.class);
    private final AbwesenheitRepository abwesenheitRepository = mock(AbwesenheitRepository.class);
    private final MitarbeiterAuskunft fake = new FakeMitarbeiterAuskunft(Rolle.MITARBEITER);
    private final ZeitkontoService service = service(UHR_OKTOBER, fake);

    private ZeitkontoService service(Clock uhr, MitarbeiterAuskunft mitarbeiterAuskunft) {
        ZugriffsPruefung zugriffsPruefung = new ZugriffsPruefung(new FakeProjektAuskunft());
        AbwesenheitService abwesenheitService = new AbwesenheitService(abwesenheitRepository, feiertagService,
                mitarbeiterAuskunft, zugriffsPruefung);
        return new ZeitkontoService(zeiteintragService, abwesenheitService, mitarbeiterAuskunft, zugriffsPruefung, uhr);
    }

    /** Eine Person mit eigenem Arbeitszeitmodell als Mitarbeiter 1. */
    private MitarbeiterAuskunft person(LocalDate kontoStart, String wochenstunden, Set<DayOfWeek> arbeitstage,
            int startsaldo) {
        MitarbeiterAuskunft auskunft = mock(MitarbeiterAuskunft.class);
        when(auskunft.finde(1)).thenReturn(Optional.of(new MitarbeiterInfo(1, "P-1001", "Test", "Person",
                Rolle.MITARBEITER, true, null, LocalDate.of(1990, 1, 1), new BigDecimal(wochenstunden), arbeitstage,
                kontoStart, startsaldo)));
        return auskunft;
    }

    private void feiertagAm(LocalDate tag) {
        when(feiertagService.datenImZeitraum(any(), any())).thenReturn(Set.of(tag));
    }

    private void zweiEintraegeMitAchtUndNeunStunden() {
        when(zeiteintragService.istMinuten(anyLong(), any(), any())).thenReturn(17 * 60);
    }

    @Test
    void wochenSollIstWochenstundenJeArbeitstagLautModell() {
        assertThat(service.wochenSollMinuten(JONAS_BECKER, Rolle.MITARBEITER)).isEqualTo(5 * 480); // 40 h / 5 Tage
        assertThat(service.wochenSollMinuten(LENA_SCHROEDER, Rolle.MITARBEITER)).isEqualTo(3 * 400); // 20 h / 3 Tage
    }

    @Test
    void tagesSollWirdAufGanzeMinutenGerundet() {
        // 38,5 h / 4 Tage = 577,5 Minuten
        ZeitkontoService teilzeit = service(UHR_OKTOBER,
                person(LocalDate.of(2026, 1, 1), "38.5", MONTAG_BIS_DONNERSTAG, 0));

        assertThat(teilzeit.wochenSollMinuten(1, Rolle.MITARBEITER)).isEqualTo(4 * 578);
    }

    @Test
    void anFeiertagenIstDasSollNull() {
        feiertagAm(LocalDate.of(2026, 10, 21));

        assertThat(service.wochenSollMinuten(JONAS_BECKER, Rolle.MITARBEITER)).isEqualTo(4 * 480);
    }

    @Test
    void laufenderMonatWirdNurBisGesternGerechnet() {
        zweiEintraegeMitAchtUndNeunStunden();

        ZeitkontoMonat oktober = service.monat(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER, YearMonth.of(2026, 10));

        // 01.10. bis 21.10.2026: 15 Arbeitstage * 480 Minuten
        assertThat(oktober.sollMinuten()).isEqualTo(7200);
        assertThat(oktober.istMinuten()).isEqualTo(17 * 60);
        assertThat(oktober.saldoMonatMinuten()).isEqualTo(17 * 60 - 7200);
    }

    @Test
    void teilzeitMonatsSollNachArbeitszeitmodell() {
        ZeitkontoMonat oktober = service.monat(LENA_SCHROEDER, Rolle.MITARBEITER, LENA_SCHROEDER, YearMonth.of(2026, 10));

        // 01.10. bis 21.10.2026 Montag bis Mittwoch: 05.-07., 12.-14., 19.-21. = 9 Tage * 400 Minuten
        assertThat(oktober.sollMinuten()).isEqualTo(3600);
    }

    @Test
    void saldoStimmtMitHandrechnungUeberein() {
        ZeitkontoService januarService = service(UHR_JANUAR, fake);
        feiertagAm(LocalDate.of(2026, 1, 1));
        zweiEintraegeMitAchtUndNeunStunden();

        ZeitkontoMonat januar = januarService.monat(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER, YearMonth.of(2026, 1));

        // 01.01. Feiertag, 02.01. Freitag, 05.01. und 06.01.: 3 Tage * 480 = 1440; Ist 17 h = 1020
        assertThat(januar.sollMinuten()).isEqualTo(24 * 60);
        assertThat(januar.istMinuten()).isEqualTo(17 * 60);
        assertThat(januar.saldoMonatMinuten()).isEqualTo(-420);
        assertThat(januar.gesamtsaldoMinuten()).isEqualTo(-420);
    }

    @Test
    void startsaldoAusM1ZaehltZumGesamtsaldo() {
        ZeitkontoService januarService = service(UHR_JANUAR, fake);
        feiertagAm(LocalDate.of(2026, 1, 1));
        zweiEintraegeMitAchtUndNeunStunden();

        ZeitkontoMonat januar = januarService.monat(ANNA_VOGT, Rolle.MITARBEITER, ANNA_VOGT, YearMonth.of(2026, 1));
        List<ZeitkontoMonat> monate = monate(januarService.jahresuebersicht(ANNA_VOGT, Rolle.MITARBEITER, ANNA_VOGT));

        // Startsaldo 120 + (1020 - 1440) = -300
        assertThat(januar.saldoMonatMinuten()).isEqualTo(-420);
        assertThat(januar.gesamtsaldoMinuten()).isEqualTo(-300);
        assertThat(monate.get(0).gesamtsaldoMinuten()).isEqualTo(-300);
    }

    @Test
    void anTagenMitAbwesenheitGiltDasSollAlsErfuellt() {
        ZeitkontoService januarService = service(UHR_JANUAR, fake);
        feiertagAm(LocalDate.of(2026, 1, 1));
        abwesend(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6));

        ZeitkontoMonat januar = januarService.monat(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER, YearMonth.of(2026, 1));

        assertThat(januar.sollMinuten()).isEqualTo(8 * 60);
    }

    private void abwesend(LocalDate von, LocalDate bis) {
        when(abwesenheitRepository.findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(anyLong(), any(),
                any())).thenReturn(List.of(new Abwesenheit(JONAS_BECKER, null, AbwesenheitsArt.URLAUB, von, bis)));
    }

    @Test
    void schnittstelleRechnetDenLaufendenMonatBisHeute() {
        zweiEintraegeMitAchtUndNeunStunden();

        ZeitkontoInfo info = service.monat(JONAS_BECKER, YearMonth.of(2026, 10)).orElseThrow();

        assertThat(info.mitarbeiterId()).isEqualTo(JONAS_BECKER);
        // 01.10. bis einschliesslich 22.10.2026: 16 Arbeitstage * 480 Minuten
        assertThat(info.sollMinuten()).isEqualTo(7680);
        assertThat(info.istMinuten()).isEqualTo(17 * 60);
        assertThat(info.differenzMinuten()).isEqualTo(17 * 60 - 7680);
        verify(zeiteintragService, atLeastOnce())
                .istMinuten(JONAS_BECKER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 22));
    }

    @Test
    void adminHatKeinZeitkonto() {
        assertThatThrownBy(() -> service.monat(PETRA_KRUEGER, Rolle.ADMIN, PETRA_KRUEGER, YearMonth.of(2026, 10)))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.jahresuebersicht(PETRA_KRUEGER, Rolle.ADMIN, PETRA_KRUEGER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.wochenSollMinuten(PETRA_KRUEGER, Rolle.ADMIN))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void schnittstelleLiefertFuerZukuenftigeMonateNichts() {
        assertThat(service.monat(JONAS_BECKER, YearMonth.of(2026, 11))).isEmpty();
    }

    @Test
    void schnittstelleLiefertFuerUnbekanntenMitarbeiterNichts() {
        assertThat(service.monat(99, YearMonth.of(2026, 10))).isEmpty();
    }

    @Test
    void unbekannterMitarbeiterHatKeinZeitkonto() {
        assertThatThrownBy(() -> service.monat(99, Rolle.MITARBEITER, 99, YearMonth.of(2026, 10)))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Der Mitarbeiter wurde nicht gefunden.");
    }

    @Test
    void ohneAnmeldungKeinZeitkonto() {
        assertThatThrownBy(() -> service.monat(JONAS_BECKER, null, JONAS_BECKER, YearMonth.of(2026, 10)))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.jahresuebersicht(JONAS_BECKER, null, JONAS_BECKER))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void jahresuebersichtRechnetDenGesamtsaldoFort() {
        ZeitkontoService januarService = service(UHR_JANUAR, fake);
        feiertagAm(LocalDate.of(2026, 1, 1));
        zweiEintraegeMitAchtUndNeunStunden();

        List<ZeitkontoMonat> monate = monate(januarService.jahresuebersicht(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER));

        assertThat(monate).hasSize(1);
        assertThat(monate.get(0).sollMinuten()).isEqualTo(24 * 60);
        assertThat(monate.get(0).gesamtsaldoMinuten()).isEqualTo(-420);
    }

    @Test
    void kontoBeginntAmStartdatumAusM1() {
        ZeitkontoService spaeterStart = service(UHR_OKTOBER,
                person(LocalDate.of(2026, 10, 19), "40", MONTAG_BIS_FREITAG, 0));
        zweiEintraegeMitAchtUndNeunStunden();

        ZeitkontoMonat oktober = spaeterStart.monat(1, Rolle.MITARBEITER, 1, YearMonth.of(2026, 10));
        List<ZeitkontoMonat> monate = monate(spaeterStart.jahresuebersicht(1, Rolle.MITARBEITER, 1));

        // 19.10. bis 21.10.: 3 Tage * 480; Ist wird erst ab dem Startdatum gesucht
        assertThat(oktober.sollMinuten()).isEqualTo(24 * 60);
        assertThat(oktober.gesamtsaldoMinuten()).isEqualTo(17 * 60 - 24 * 60);
        assertThat(monate).hasSize(1);
        assertThat(monate.get(0).monat()).isEqualTo(YearMonth.of(2026, 10));
        verify(zeiteintragService, atLeastOnce())
                .istMinuten(1, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 21));
    }

    @Test
    void ohneStartdatumBeginntDasKontoHeute() {
        ZeitkontoService ohneStart = service(UHR_OKTOBER, person(null, "40", MONTAG_BIS_FREITAG, 60));

        ZeitkontoMonat oktober = ohneStart.monat(1, Rolle.MITARBEITER, 1, YearMonth.of(2026, 10));

        assertThat(oktober.sollMinuten()).isEqualTo(0);
        assertThat(oktober.gesamtsaldoMinuten()).isEqualTo(60);
    }

    @Test
    void startdatumInDerZukunftZeigtDenLaufendenMonatMitStartsaldo() {
        ZeitkontoService kuenftig = service(UHR_OKTOBER,
                person(LocalDate.of(2026, 11, 2), "40", MONTAG_BIS_FREITAG, 90));

        List<ZeitkontoMonat> monate = monate(kuenftig.jahresuebersicht(1, Rolle.MITARBEITER, 1));
        ZeitkontoMonat oktober = kuenftig.monat(1, Rolle.MITARBEITER, 1, YearMonth.of(2026, 10));

        assertThat(monate).hasSize(1);
        assertThat(monate.get(0).monat()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(monate.get(0).sollMinuten()).isEqualTo(0);
        assertThat(monate.get(0).istMinuten()).isEqualTo(0);
        assertThat(monate.get(0).gesamtsaldoMinuten()).isEqualTo(90);
        assertThat(oktober.sollMinuten()).isEqualTo(0);
        assertThat(oktober.gesamtsaldoMinuten()).isEqualTo(90);
    }

    @Test
    void zukuenftigerMonatGehtNicht() {
        assertThatThrownBy(() -> service.monat(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER, YearMonth.of(2026, 11)))
                .isInstanceOf(FachlicheException.class);
    }

    @Test
    void projektleitungSiehtZeitkontenDerMitarbeiterIhrerProjekte() {
        ZeitkontoMonat oktober = service.monat(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, LENA_SCHROEDER, YearMonth.of(2026, 10));
        List<ZeitkontoMonat> jahr = monate(service.jahresuebersicht(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, LENA_SCHROEDER));

        assertThat(oktober.sollMinuten()).isEqualTo(3600);
        assertThat(jahr).isNotEmpty();
    }

    @Test
    void fremdeZeitkontenSiehtNurDieZustaendigeProjektleitung() {
        YearMonth oktober = YearMonth.of(2026, 10);

        assertThatThrownBy(() -> service.monat(JONAS_BECKER, Rolle.MITARBEITER, LENA_SCHROEDER, oktober))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.monat(SABINE_ENGEL, Rolle.PROJEKTLEITUNG, LENA_SCHROEDER, oktober))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.monat(PETRA_KRUEGER, Rolle.ADMIN, LENA_SCHROEDER, oktober))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.jahresuebersicht(JONAS_BECKER, Rolle.MITARBEITER, LENA_SCHROEDER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void wochenSollNurMitAnmeldung() {
        // Woche 19.10. bis 25.10.2026: 5 Arbeitstage, der 21.10. ist hier kein Feiertag
        assertThat(service.wochenSollMinuten(JONAS_BECKER, Rolle.MITARBEITER)).isEqualTo(5 * 480);
        assertThatThrownBy(() -> service.wochenSollMinuten(JONAS_BECKER, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    private static List<ZeitkontoMonat> monate(Zeitkontouebersicht uebersicht) {
        return uebersicht.monate().stream().map(ZeitkontoZeile::konto).toList();
    }

    @Test
    void anfangssaldoImStartjahrIstDerStartsaldoAmStartdatum() {
        ZeitkontoService spaeterStart = service(UHR_OKTOBER,
                person(LocalDate.of(2026, 9, 14), "40", MONTAG_BIS_FREITAG, 90));

        Zeitkontouebersicht uebersicht = spaeterStart.jahresuebersicht(1, Rolle.MITARBEITER, 1);

        assertThat(uebersicht.anfangssaldoDatum()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(uebersicht.anfangssaldoMinuten()).isEqualTo(90);
        ZeitkontoZeile september = uebersicht.monate().get(0);
        assertThat(september.ab()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(september.bis()).isNull();
        assertThat(september.laufend()).isFalse();
        ZeitkontoZeile oktober = uebersicht.monate().get(1);
        assertThat(oktober.ab()).isNull();
        assertThat(oktober.bis()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(oktober.laufend()).isTrue();
    }

    @Test
    void anfangssaldoEnthaeltDieVorjahre() {
        // Konto ab 29.12.2025 (Montag): 29. bis 31.12. sind 3 Arbeitstage ohne Zeiten = -1440, Startsaldo 90
        ZeitkontoService seitVorjahr = service(UHR_OKTOBER,
                person(LocalDate.of(2025, 12, 29), "40", MONTAG_BIS_FREITAG, 90));

        Zeitkontouebersicht uebersicht = seitVorjahr.jahresuebersicht(1, Rolle.MITARBEITER, 1);

        assertThat(uebersicht.anfangssaldoDatum()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(uebersicht.anfangssaldoMinuten()).isEqualTo(90 - 3 * 480);
        assertThat(uebersicht.monate()).hasSize(10);
        assertThat(uebersicht.monate().get(0).ab()).isNull();
    }

    @Test
    void amStartmonatDesLaufendenMonatsStehenBeideGrenzen() {
        ZeitkontoService spaeterStart = service(UHR_OKTOBER,
                person(LocalDate.of(2026, 10, 19), "40", MONTAG_BIS_FREITAG, 0));

        ZeitkontoZeile oktober = spaeterStart.jahresuebersicht(1, Rolle.MITARBEITER, 1).monate().get(0);

        assertThat(oktober.ab()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(oktober.bis()).isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void amMonatsErstenIstImLaufendenMonatNochNichtsGerechnet() {
        Clock ersterOktober = Clock.fixed(Instant.parse("2026-10-01T07:00:00Z"), ZoneId.of("Europe/Berlin"));
        ZeitkontoService amErsten = service(ersterOktober,
                person(LocalDate.of(2026, 9, 1), "40", MONTAG_BIS_FREITAG, 0));

        List<ZeitkontoZeile> zeilen = amErsten.jahresuebersicht(1, Rolle.MITARBEITER, 1).monate();

        assertThat(zeilen.get(zeilen.size() - 1).laufend()).isTrue();
        assertThat(zeilen.get(zeilen.size() - 1).bis()).isNull();
    }
}
