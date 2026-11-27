package de.timecraft.zeiterfassung.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.repository.FeiertagRepository;

/** @author Noah Cipa */
class FeiertagServiceTest {

    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-10-22T07:30:00Z"), ZoneId.of("Europe/Berlin"));

    private final FeiertagRepository feiertagRepository = mock(FeiertagRepository.class);
    private final FeiertagService service = new FeiertagService(feiertagRepository, FESTE_UHR);

    @Test
    void ostersonntagWirdRichtigBerechnet() {
        assertThat(service.ostersonntag(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(service.ostersonntag(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(service.ostersonntag(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void nrwHatElfFeiertageMitRichtigenBeweglichenTagen() {
        List<Feiertag> feiertage = service.gesetzlicheFeiertageNrw(2026);

        assertThat(feiertage).hasSize(11);
        assertThat(feiertage).extracting(Feiertag::getDatum).contains(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 3),   // Karfreitag
                LocalDate.of(2026, 4, 6),   // Ostermontag
                LocalDate.of(2026, 5, 14),  // Christi Himmelfahrt
                LocalDate.of(2026, 5, 25),  // Pfingstmontag
                LocalDate.of(2026, 6, 4),   // Fronleichnam
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 12, 26));
    }

    @Test
    void vorbelegenLegtFeiertageFuerZweiJahreAn() {
        service.vorbelegen();

        verify(feiertagRepository, times(2)).saveAll(argThat(feiertage -> zaehle(feiertage) == 11));
    }

    /** Ein Jahr mit mindestens einem Feiertag bleibt unveraendert: Geloeschtes kommt nicht wieder. */
    @Test
    void vorbelegenLaesstBelegteJahreUnveraendert() {
        when(feiertagRepository.findByDatumBetweenOrderByDatum(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(new Feiertag(LocalDate.of(2026, 1, 1), "Neujahr")));

        service.vorbelegen();

        verify(feiertagRepository).saveAll(argThat(feiertage -> feiertage.iterator().next()
                .getDatum().getYear() == 2027));
    }

    @Test
    void adminKannFeiertagAnlegen() {
        when(feiertagRepository.save(any(Feiertag.class))).thenAnswer(aufruf -> aufruf.getArgument(0));

        Feiertag feiertag = service.anlegen(Rolle.ADMIN, LocalDate.of(2026, 12, 24), "  Heiligabend  ");

        assertThat(feiertag.getDatum()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(feiertag.getName()).isEqualTo("Heiligabend");
    }

    @Test
    void mitarbeiterUndProjektleitungDuerfenKeineFeiertagePflegen() {
        assertThatThrownBy(() -> service.anlegen(Rolle.MITARBEITER, LocalDate.of(2026, 12, 24), "Heiligabend"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.anlegen(Rolle.PROJEKTLEITUNG, LocalDate.of(2026, 12, 24), "Heiligabend"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.loeschen(Rolle.MITARBEITER, 1))
                .isInstanceOf(ZugriffVerweigertException.class);
        verify(feiertagRepository, never()).save(any());
        verify(feiertagRepository, never()).delete(any());
    }

    @Test
    void zweiterFeiertagAmSelbenDatumGehtNicht() {
        when(feiertagRepository.existsByDatum(LocalDate.of(2026, 10, 3))).thenReturn(true);

        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, LocalDate.of(2026, 10, 3), "Doppelt"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Am 03.10.2026 gibt es schon einen Feiertag.");
        verify(feiertagRepository, never()).save(any());
    }

    @Test
    void anlegenOhneNameOderDatumGehtNicht() {
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, LocalDate.of(2026, 12, 24), "   "))
                .isInstanceOf(FachlicheException.class);
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, null, "Heiligabend"))
                .isInstanceOf(FachlicheException.class);
        verify(feiertagRepository, never()).save(any());
    }

    @Test
    void aendernEinesUnbekanntenFeiertagsGehtNicht() {
        when(feiertagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aendern(Rolle.ADMIN, 99, LocalDate.of(2026, 12, 24), "Heiligabend"))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void ohneAnmeldungKeineFeiertageSehen() {
        assertThatThrownBy(() -> service.alle(null)).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void eingabeFehlerNennenDasFeld() {
        when(feiertagRepository.existsByDatum(LocalDate.of(2026, 10, 3))).thenReturn(true);

        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, null, "Heiligabend"))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactly(EingabeFehler.Feld.DATUM));
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, LocalDate.of(2026, 10, 3), "Doppelt"))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactly(EingabeFehler.Feld.DATUM));
        assertThatThrownBy(() -> service.anlegen(Rolle.ADMIN, LocalDate.of(2026, 12, 24), "x".repeat(51)))
                .isInstanceOfSatisfying(EingabeFehler.class,
                        fehler -> assertThat(fehler.felder()).containsExactly(EingabeFehler.Feld.NAME));
    }

    private static int zaehle(Iterable<Feiertag> feiertage) {
        int anzahl = 0;
        for (Feiertag ignoriert : feiertage) {
            anzahl++;
        }
        return anzahl;
    }
}
