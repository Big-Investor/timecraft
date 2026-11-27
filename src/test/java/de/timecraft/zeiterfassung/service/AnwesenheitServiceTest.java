package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.ANNA_VOGT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.FELIX_BRANDT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LUKAS_MEYER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.MEHMET_YILMAZ;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.Anwesenheit;
import de.timecraft.zeiterfassung.model.AnwesenheitsUebersicht;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.model.Zeiteintrag;

/** @author Noah Cipa */
class AnwesenheitServiceTest {

    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-10-22T07:30:00Z"), ZoneId.of("Europe/Berlin"));
    private static final Rolle ROLLE = Rolle.MITARBEITER;

    private final ZeiteintragService zeiteintragService = mock(ZeiteintragService.class);
    private final AnwesenheitService service = new AnwesenheitService(zeiteintragService,
            new FakeMitarbeiterAuskunft(ROLLE), new FakeProjektAuskunft(), FESTE_UHR);

    @BeforeEach
    void jonasEingestempeltPaulInPauseAlleAnderenNicht() {
        when(zeiteintragService.statusVon(anyLong())).thenReturn(StempelStatus.NICHT_EINGESTEMPELT);
        when(zeiteintragService.laufenderEintragVon(anyLong())).thenReturn(Optional.empty());
        when(zeiteintragService.statusVon(JONAS_BECKER)).thenReturn(StempelStatus.EINGESTEMPELT);
        when(zeiteintragService.laufenderEintragVon(JONAS_BECKER)).thenReturn(Optional.of(
                new Zeiteintrag(JONAS_BECKER, Instant.parse("2026-10-22T06:00:00Z"), Standort.BUERO, null)));
        when(zeiteintragService.statusVon(PAUL_HARTMANN)).thenReturn(StempelStatus.IN_PAUSE);
        when(zeiteintragService.laufenderEintragVon(PAUL_HARTMANN)).thenReturn(Optional.of(
                new Zeiteintrag(PAUL_HARTMANN, Instant.parse("2026-10-22T05:00:00Z"), Standort.HOMEOFFICE, 1024L)));
    }

    @Test
    void ohneFilterSiehtManAlleAktivenMitarbeiterOhneAdminUndEhemalige() {
        List<Anwesenheit> liste = service.uebersicht(ROLLE, "", null, null).treffer();

        assertThat(liste).extracting(Anwesenheit::mitarbeiterId).containsExactly(JONAS_BECKER, PAUL_HARTMANN,
                LENA_SCHROEDER, MEHMET_YILMAZ, FELIX_BRANDT, ANNA_VOGT, LUKAS_MEYER, SABINE_ENGEL);
        assertThat(liste.get(0).name()).isEqualTo("Jonas Becker");
        assertThat(liste.get(0).standort()).isEqualTo(Standort.BUERO);
        assertThat(liste.get(1).projektId()).isEqualTo(1024L);
        assertThat(liste.get(2).standort()).isNull();
    }

    @Test
    void filterNachStatus() {
        List<Anwesenheit> liste = service.uebersicht(ROLLE, null, StempelStatus.EINGESTEMPELT, null).treffer();

        assertThat(liste).extracting(Anwesenheit::mitarbeiterId).containsExactly(JONAS_BECKER);
    }

    @Test
    void filterNachStandort() {
        List<Anwesenheit> liste = service.uebersicht(ROLLE, null, null, Standort.HOMEOFFICE).treffer();

        assertThat(liste).extracting(Anwesenheit::mitarbeiterId).containsExactly(PAUL_HARTMANN);
    }

    @Test
    void sucheNachNameIgnoriertGrossUndKleinschreibung() {
        List<Anwesenheit> liste = service.uebersicht(ROLLE, "  HARTMANN ", null, null).treffer();

        assertThat(liste).extracting(Anwesenheit::mitarbeiterId).containsExactly(PAUL_HARTMANN);
    }

    @Test
    void sucheNachAdminFindetNichts() {
        assertThat(service.uebersicht(ROLLE, "Krüger", null, null).treffer()).isEmpty();
    }

    @Test
    void filterWirkenGemeinsam() {
        // Eingestempelt ist nur Jonas, der ist aber im Buero -> kein Treffer
        List<Anwesenheit> liste = service.uebersicht(ROLLE, null, StempelStatus.EINGESTEMPELT, Standort.HOMEOFFICE).treffer();

        assertThat(liste).isEmpty();
    }

    @Test
    void werHeuteAbwesendIstWirdAlsAbwesendAngezeigt() {
        when(zeiteintragService.statusVon(LENA_SCHROEDER)).thenReturn(StempelStatus.ABWESEND);

        List<Anwesenheit> liste = service.uebersicht(ROLLE, null, StempelStatus.ABWESEND, null).treffer();

        assertThat(liste).extracting(Anwesenheit::mitarbeiterId).containsExactly(LENA_SCHROEDER);
    }

    @Test
    void projektleitungSiehtDieselbeListe() {
        assertThat(service.uebersicht(Rolle.PROJEKTLEITUNG, null, null, null).treffer())
                .isEqualTo(service.uebersicht(ROLLE, null, null, null).treffer());
    }

    @Test
    void adminUndNichtAngemeldeteSehenNichts() {
        assertThatThrownBy(() -> service.uebersicht(Rolle.ADMIN, null, null, null))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.uebersicht(null, null, null, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void anzahlJeStatusZaehltAlleUnabhaengigVomFilter() {
        when(zeiteintragService.statusVon(LENA_SCHROEDER)).thenReturn(StempelStatus.ABWESEND);

        AnwesenheitsUebersicht uebersicht = service.uebersicht(ROLLE, "Hartmann", null, null);

        assertThat(uebersicht.treffer()).hasSize(1);
        assertThat(uebersicht.anzahlJeStatus()).containsOnly(
                entry(StempelStatus.NICHT_EINGESTEMPELT, 5), entry(StempelStatus.EINGESTEMPELT, 1),
                entry(StempelStatus.IN_PAUSE, 1), entry(StempelStatus.ABWESEND, 1));
    }

    @Test
    void anzahlIstNullWennNiemandDenStatusHat() {
        assertThat(service.uebersicht(ROLLE, null, null, null).anzahlJeStatus())
                .containsEntry(StempelStatus.ABWESEND, 0);
    }

    @Test
    void projektWirdMitIdUndNameGeliefert() {
        Anwesenheit paul = service.uebersicht(ROLLE, "Hartmann", null, null).treffer().get(0);

        assertThat(paul.projektId()).isEqualTo(1024L);
        assertThat(paul.projektName()).isEqualTo("Einbauküche Wagner");
    }

    @Test
    void unbekanntesProjektBehaeltNurDieId() {
        when(zeiteintragService.laufenderEintragVon(PAUL_HARTMANN)).thenReturn(Optional.of(
                new Zeiteintrag(PAUL_HARTMANN, Instant.parse("2026-10-22T05:00:00Z"), Standort.BUERO, 9999L)));

        Anwesenheit paul = service.uebersicht(ROLLE, "Hartmann", null, null).treffer().get(0);

        assertThat(paul.projektId()).isEqualTo(9999L);
        assertThat(paul.projektName()).isNull();
    }

    @Test
    void standIstDieUhrzeitDerAbfrage() {
        assertThat(service.uebersicht(ROLLE, null, null, null).stand()).isEqualTo(LocalTime.of(9, 30));
    }
}
