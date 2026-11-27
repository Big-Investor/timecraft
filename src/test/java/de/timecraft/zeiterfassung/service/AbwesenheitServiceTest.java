package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.FELIX_BRANDT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Abwesenheit;
import de.timecraft.zeiterfassung.model.AbwesenheitImKalender;
import de.timecraft.zeiterfassung.model.AbwesenheitsTag;
import de.timecraft.zeiterfassung.model.Urlaubskonto;
import de.timecraft.zeiterfassung.repository.AbwesenheitRepository;

/** @author Noah Cipa */
class AbwesenheitServiceTest {

    private static final LocalDate MONTAG = LocalDate.of(2026, 10, 19);
    private static final LocalDate SONNTAG = LocalDate.of(2026, 10, 25);

    private final AbwesenheitRepository abwesenheitRepository = mock(AbwesenheitRepository.class);
    private final FeiertagService feiertagService = mock(FeiertagService.class);
    private final AbwesenheitService service = new AbwesenheitService(abwesenheitRepository, feiertagService,
            new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), new ZugriffsPruefung(new FakeProjektAuskunft()));

    @Test
    void eineWocheUrlaubSindFuenfTage() {
        assertThat(service.urlaubstage(JONAS_BECKER, MONTAG, SONNTAG)).isEqualTo(5);
    }

    @Test
    void teilzeitMitDreiArbeitstagenZaehltNurDieseAlsUrlaubstage() {
        assertThat(service.urlaubstage(LENA_SCHROEDER, MONTAG, SONNTAG)).isEqualTo(3);
    }

    @Test
    void urlaubstageFuerUnbekanntenMitarbeiterGehenNicht() {
        assertThatThrownBy(() -> service.urlaubstage(99, MONTAG, SONNTAG))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Der Mitarbeiter wurde nicht gefunden.");
    }

    @Test
    void feiertagZaehltNichtAlsUrlaubstag() {
        when(feiertagService.istFeiertag(LocalDate.of(2026, 10, 21))).thenReturn(true);

        assertThat(service.urlaubstage(1, MONTAG, SONNTAG)).isEqualTo(4);
    }

    @Test
    void resturlaubIstAnspruchPlusUebertragMinusUrlaub() {
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG),
                new Abwesenheit(1, 51L, AbwesenheitsArt.KRANKHEIT, MONTAG.plusDays(14), MONTAG.plusDays(15)),
                new Abwesenheit(1, 52L, AbwesenheitsArt.SONSTIGE, MONTAG.plusDays(21), MONTAG.plusDays(21)));

        // 30 Anspruch + 2 Uebertrag - 5 Urlaubstage, Krankheit und Sonstiges zaehlen nicht
        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026))
                .isEqualTo(new Urlaubskonto(2026, 30, 2, 5, 27));
    }

    @Test
    void ohneUrlaubIstDerGanzeAnspruchUebrig() {
        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026))
                .isEqualTo(new Urlaubskonto(2026, 30, 2, 0, 32));
    }

    @Test
    void auchKuenftigerGenehmigterUrlaubZaehlt() {
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB,
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 22)));

        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026).resturlaubTage()).isEqualTo(30);
    }

    @Test
    void urlaubUeberDenJahreswechselZaehltNurMitDenTagenDesJahres() {
        // 28.12.2026 (Montag) bis 05.01.2027, ohne Feiertage: 2026 vier Arbeitstage, 2027 drei
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB,
                LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 5)));

        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026).genommenTage()).isEqualTo(4);
        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2027).genommenTage()).isEqualTo(3);
    }

    @Test
    void feiertageUndArbeitsfreieTageMindernDenResturlaubNicht() {
        when(feiertagService.istFeiertag(MONTAG.plusDays(2))).thenReturn(true);
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));

        assertThat(service.urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026).resturlaubTage()).isEqualTo(28);
    }

    @Test
    void zuVielUrlaubErgibtNegativenResturlaub() {
        MitarbeiterAuskunft auskunft = spy(new FakeMitarbeiterAuskunft(Rolle.MITARBEITER));
        doReturn(Optional.of(new UrlaubsDaten(2026, 3, 0))).when(auskunft).urlaubsdaten(JONAS_BECKER, 2026);
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));

        assertThat(serviceMit(auskunft).urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026).resturlaubTage())
                .isEqualTo(-2);
    }

    @Test
    void ohneUrlaubsdatenGibtEsKeinenResturlaub() {
        MitarbeiterAuskunft auskunft = spy(new FakeMitarbeiterAuskunft(Rolle.MITARBEITER));
        doReturn(Optional.empty()).when(auskunft).urlaubsdaten(JONAS_BECKER, 2026);
        abwesenheitenVonJonas(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));

        assertThat(serviceMit(auskunft).urlaubskonto(JONAS_BECKER, Rolle.MITARBEITER, 2026))
                .isEqualTo(new Urlaubskonto(2026, null, null, 5, null));
    }

    @Test
    void urlaubskontoMitUrlaubFuerUnbekanntenMitarbeiterGehtNicht() {
        abwesenheitenVonJonas(new Abwesenheit(99, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));

        assertThatThrownBy(() -> service.urlaubskonto(99, Rolle.MITARBEITER, 2026))
                .isInstanceOf(NichtGefundenException.class);
    }

    private void abwesenheitenVonJonas(Abwesenheit... abwesenheiten) {
        when(abwesenheitRepository.findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(anyLong(), any(), any()))
                .thenReturn(List.of(abwesenheiten));
    }

    private AbwesenheitService serviceMit(MitarbeiterAuskunft auskunft) {
        return new AbwesenheitService(abwesenheitRepository, feiertagService, auskunft,
                new ZugriffsPruefung(new FakeProjektAuskunft()));
    }

    @Test
    void zeitraumMitBisVorVonGehtNicht() {
        assertThatThrownBy(() -> service.urlaubstage(1, SONNTAG, MONTAG)).isInstanceOf(FachlicheException.class);
    }

    @Test
    void uebernehmenSpeichertAbwesenheitUndGibtIdZurueck() {
        Abwesenheit gespeichert = spy(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));
        doReturn(7L).when(gespeichert).getId();
        when(abwesenheitRepository.save(any(Abwesenheit.class))).thenReturn(gespeichert);

        long id = service.uebernehmen(50, 1, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG);

        assertThat(id).isEqualTo(7L);
        verify(abwesenheitRepository).save(any(Abwesenheit.class));
    }

    @Test
    void ueberschneidendeAbwesenheitWirdNichtGespeichert() {
        Abwesenheit vorhanden = new Abwesenheit(1, 40L, AbwesenheitsArt.KRANKHEIT, MONTAG, MONTAG.plusDays(1));
        when(abwesenheitRepository.findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(anyLong(), any(), any()))
                .thenReturn(List.of(vorhanden));

        assertThatThrownBy(() -> service.uebernehmen(50, 1, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("In diesem Zeitraum gibt es schon eine Abwesenheit.");
        verify(abwesenheitRepository, never()).save(any());
    }

    @Test
    void uebernehmenOhneArtGehtNicht() {
        assertThatThrownBy(() -> service.uebernehmen(50, 1, null, MONTAG, SONNTAG))
                .isInstanceOf(FachlicheException.class);
        verify(abwesenheitRepository, never()).save(any());
    }

    @Test
    void aufhebenEinerUnbekanntenAbwesenheitGehtNicht() {
        when(abwesenheitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aufheben(99)).isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void abwesenheitZuAntragFindetDieRichtigeId() {
        Abwesenheit gespeichert = spy(new Abwesenheit(1, 50L, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG));
        doReturn(7L).when(gespeichert).getId();
        when(abwesenheitRepository.findByAntragId(50)).thenReturn(Optional.of(gespeichert));

        assertThat(service.abwesenheitZuAntrag(50)).contains(7L);
        assertThat(service.abwesenheitZuAntrag(51)).isEmpty();
    }

    @Test
    void artSiehtNurDerBetroffeneKollegenSehenNurAbwesend() {
        Abwesenheit meinUrlaub = new Abwesenheit(JONAS_BECKER, 50L, AbwesenheitsArt.URLAUB, MONTAG, MONTAG.plusDays(2));
        Abwesenheit krankerKollege = new Abwesenheit(LENA_SCHROEDER, 51L, AbwesenheitsArt.KRANKHEIT, MONTAG, MONTAG);
        when(abwesenheitRepository.findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(any(), any()))
                .thenReturn(List.of(meinUrlaub, krankerKollege));

        List<AbwesenheitImKalender> kalender = service.kalender(JONAS_BECKER, Rolle.MITARBEITER, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::art).containsExactly(AbwesenheitsArt.URLAUB, null);
        assertThat(kalender).extracting(AbwesenheitImKalender::name).containsExactly("Jonas Becker", "Lena Schröder");
    }

    @Test
    void vorgesetzterSiehtDieArtSeinerMitarbeiter() {
        kalenderMitUrlaubUndKrankheit();

        List<AbwesenheitImKalender> kalender = service.kalender(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::art).containsExactly(AbwesenheitsArt.URLAUB, AbwesenheitsArt.KRANKHEIT);
    }

    @Test
    void kollegeOhneVorgesetztenrolleSiehtNurAbwesend() {
        kalenderMitUrlaubUndKrankheit();

        List<AbwesenheitImKalender> kalender = service.kalender(FELIX_BRANDT, Rolle.MITARBEITER, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::art).containsExactly(null, null);
    }

    @Test
    void adminSiehtNurAbwesend() {
        kalenderMitUrlaubUndKrankheit();

        List<AbwesenheitImKalender> kalender = service.kalender(PETRA_KRUEGER, Rolle.ADMIN, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::art).containsExactly(null, null);
    }

    private void kalenderMitUrlaubUndKrankheit() {
        Abwesenheit urlaub = new Abwesenheit(JONAS_BECKER, 50L, AbwesenheitsArt.URLAUB, MONTAG, MONTAG.plusDays(2));
        Abwesenheit krankheit = new Abwesenheit(LENA_SCHROEDER, 51L, AbwesenheitsArt.KRANKHEIT, MONTAG, MONTAG);
        when(abwesenheitRepository.findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(any(), any()))
                .thenReturn(List.of(urlaub, krankheit));
    }

    @Test
    void ohneAnmeldungKeinKalender() {
        assertThatThrownBy(() -> service.kalender(1, null, YearMonth.of(2026, 10)))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void projektleitungOhneVorgesetztenrolleSiehtNurAbwesend() {
        kalenderMitUrlaubUndKrankheit();

        // Sabine ist Projektleitung, aber nicht Vorgesetzte von Jonas und Lena
        List<AbwesenheitImKalender> kalender = service.kalender(SABINE_ENGEL, Rolle.PROJEKTLEITUNG, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::art).containsExactly(null, null);
    }

    @Test
    void abwesenheitEinesInM1UnbekanntenMitarbeitersFehltImKalender() {
        Abwesenheit unbekannt = new Abwesenheit(99, 52L, AbwesenheitsArt.URLAUB, MONTAG, MONTAG);
        Abwesenheit urlaub = new Abwesenheit(JONAS_BECKER, 50L, AbwesenheitsArt.URLAUB, MONTAG, MONTAG);
        when(abwesenheitRepository.findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(any(), any()))
                .thenReturn(List.of(unbekannt, urlaub));

        List<AbwesenheitImKalender> kalender = service.kalender(JONAS_BECKER, Rolle.MITARBEITER, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::mitarbeiterId).containsExactly(JONAS_BECKER);
    }

    @Test
    void ohneAnmeldungKeinUrlaubskonto() {
        assertThatThrownBy(() -> service.urlaubskonto(1, null, 2026))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void kalenderZaehltNurArbeitstageImAngezeigtenMonat() {
        // 28.09. bis 06.10.2026: im Oktober 01., 02., 05., 06., davon ist der 05. hier ein Feiertag
        when(feiertagService.istFeiertag(LocalDate.of(2026, 10, 5))).thenReturn(true);
        when(abwesenheitRepository.findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(any(), any()))
                .thenReturn(List.of(new Abwesenheit(JONAS_BECKER, 50L, AbwesenheitsArt.URLAUB,
                        LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 6))));

        List<AbwesenheitImKalender> kalender = service.kalender(JONAS_BECKER, Rolle.MITARBEITER, YearMonth.of(2026, 10));

        assertThat(kalender).extracting(AbwesenheitImKalender::arbeitstage).containsExactly(3);
    }

    @Test
    void abwesenheitsTageSindNurArbeitstageImZeitraum() {
        // Urlaub vom Freitag davor bis zum naechsten Dienstag, abgefragt Montag bis Sonntag, Mittwoch ist Feiertag
        when(feiertagService.istFeiertag(MONTAG.plusDays(2))).thenReturn(true);
        abwesenheitVonLena(MONTAG.minusDays(3), SONNTAG.plusDays(2));

        List<AbwesenheitsTag> tage = service.abwesenheitsTage(LENA_SCHROEDER, LENA_SCHROEDER, MONTAG, SONNTAG);

        // Lena arbeitet Montag bis Mittwoch
        assertThat(tage).extracting(AbwesenheitsTag::datum).containsExactly(MONTAG, MONTAG.plusDays(1));
        assertThat(tage).extracting(AbwesenheitsTag::art)
                .containsOnly(AbwesenheitsArt.KRANKHEIT);
    }

    @Test
    void abwesenheitsTageZeigenDemVorgesetztenDieArt() {
        abwesenheitVonLena(MONTAG, MONTAG);

        assertThat(service.abwesenheitsTage(PAUL_HARTMANN, LENA_SCHROEDER, MONTAG, SONNTAG))
                .containsExactly(new AbwesenheitsTag(MONTAG, AbwesenheitsArt.KRANKHEIT));
    }

    @Test
    void abwesenheitsTageVerbergenDieArtVorAnderenProjektleitungen() {
        abwesenheitVonLena(MONTAG, MONTAG);

        assertThat(service.abwesenheitsTage(SABINE_ENGEL, LENA_SCHROEDER, MONTAG, SONNTAG))
                .containsExactly(new AbwesenheitsTag(MONTAG, null));
    }

    private void abwesenheitVonLena(LocalDate von, LocalDate bis) {
        when(abwesenheitRepository.findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(
                LENA_SCHROEDER, SONNTAG, MONTAG))
                .thenReturn(List.of(new Abwesenheit(LENA_SCHROEDER, 51L, AbwesenheitsArt.KRANKHEIT, von, bis)));
    }

    @Test
    void urlaubskontoGiltNurFuerDieAngemeldetePerson() {
        service.urlaubskonto(PAUL_HARTMANN, Rolle.MITARBEITER, 2026);
        service.urlaubskonto(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, 2026);

        verify(abwesenheitRepository, times(2))
                .findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(
                        PAUL_HARTMANN, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));
    }

    @Test
    void adminHatKeinUrlaubskonto() {
        assertThatThrownBy(() -> service.urlaubskonto(3, Rolle.ADMIN, 2026))
                .isInstanceOf(de.timecraft.geruest.common.ZugriffVerweigertException.class);
    }

    @Test
    void uebernehmenFuerUnbekanntenMitarbeiterSpeichertNichts() {
        assertThatThrownBy(() -> service.uebernehmen(50, 999, AbwesenheitsArt.URLAUB, MONTAG, SONNTAG))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Der Mitarbeiter wurde nicht gefunden.");
        verify(abwesenheitRepository, never()).save(any());
    }
}
