package de.timecraft.kalender.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.kalender.fake.FakeBuchungsAuskunft;
import de.timecraft.kalender.fake.FakeMitarbeiterAuskunft;
import de.timecraft.kalender.fake.FakeProjektAuskunft;
import de.timecraft.kalender.model.Kalenderansicht;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * Zeitraum und Blaettern (FA-M6-01, FA-M6-02).
 *
 * @author Bennet Antonio Brinkmeier
 */
class KalenderServiceZeitraumTest {

    // Donnerstag, 22.10.2026, 23:30 in Berlin (21:30 UTC): in UTC ist es auch noch der 22.
    private static final Clock DONNERSTAG = Clock.fixed(Instant.parse("2026-10-22T21:30:00Z"), ZoneId.of("Europe/Berlin"));

    private final KalenderService service = serviceMit(DONNERSTAG);

    @Test
    void heuteRichtetSichNachBerlinNichtNachUtc() {
        // 23:30 UTC am 22.10. ist in Berlin schon der 23.10.
        Clock spaet = Clock.fixed(Instant.parse("2026-10-22T23:30:00Z"), ZoneId.of("Europe/Berlin"));
        KalenderService spaeterService = serviceMit(spaet);

        assertThat(spaeterService.heute()).isEqualTo(LocalDate.of(2026, 10, 23));
    }

    @Test
    void wocheReichtVonMontagBisSonntag() {
        LocalDate donnerstag = LocalDate.of(2026, 10, 22);

        assertThat(service.ersterTag(Kalenderansicht.WOCHE, donnerstag)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(service.letzterTag(Kalenderansicht.WOCHE, donnerstag)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(service.ersterTag(Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 19))).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(service.ersterTag(Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 25))).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void wocheUeberJahreswechsel() {
        LocalDate silvester = LocalDate.of(2026, 12, 31);

        assertThat(service.ersterTag(Kalenderansicht.WOCHE, silvester)).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(service.letzterTag(Kalenderansicht.WOCHE, silvester)).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    void tagIstGenauEinTag() {
        LocalDate tag = LocalDate.of(2026, 10, 22);

        assertThat(service.ersterTag(Kalenderansicht.TAG, tag)).isEqualTo(tag);
        assertThat(service.letzterTag(Kalenderansicht.TAG, tag)).isEqualTo(tag);
    }

    @Test
    void monatReichtVomErstenBisZumLetztenTag() {
        assertThat(service.ersterTag(Kalenderansicht.MONAT, LocalDate.of(2028, 2, 15))).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(service.letzterTag(Kalenderansicht.MONAT, LocalDate.of(2028, 2, 15))).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void vorUndZurueckBlaetternUmEinenZeitraum() {
        LocalDate tag = LocalDate.of(2026, 10, 22);

        assertThat(service.blaettern(Kalenderansicht.TAG, tag, 1)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(service.blaettern(Kalenderansicht.TAG, tag, -1)).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(service.blaettern(Kalenderansicht.WOCHE, tag, 1)).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(service.blaettern(Kalenderansicht.WOCHE, tag, -1)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(service.blaettern(Kalenderansicht.MONAT, tag, 1)).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(service.blaettern(Kalenderansicht.MONAT, tag, -1)).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void monatBlaetternAmMonatsendeBleibtImZielmonat() {
        LocalDate einunddreissigster = LocalDate.of(2026, 1, 31);

        assertThat(service.blaettern(Kalenderansicht.MONAT, einunddreissigster, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    private static KalenderService serviceMit(Clock uhr) {
        return new KalenderService(new FakeBuchungsAuskunft(), new FakeProjektAuskunft(), new FakeMitarbeiterAuskunft(),
                uhr);
    }
}
