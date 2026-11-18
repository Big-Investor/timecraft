package de.timecraft.kalender.service;

import static de.timecraft.kalender.service.Testzeit.BERLIN;
import static de.timecraft.kalender.service.Testzeit.instant;
import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.kalender.fake.FakeBuchungsAuskunft;
import de.timecraft.kalender.fake.FakeMitarbeiterAuskunft;
import de.timecraft.kalender.fake.FakeProjektAuskunft;
import de.timecraft.kalender.model.KalenderEintrag;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Filter nach Projekt und Standort, auch fuer die Summen (FA-M6-06, US-M6-02). */
class KalenderServiceFilterTest {

    private static final long JONAS = 1;
    private static final long KUECHE = 1024;
    private static final long TREPPE = 1031;
    private static final LocalDate MONTAG = LocalDate.of(2026, 10, 19);

    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
    private final FakeProjektAuskunft projekte = new FakeProjektAuskunft();
    private final KalenderService service = new KalenderService(buchungen, projekte, new FakeMitarbeiterAuskunft(),
            Clock.fixed(Instant.parse("2026-10-22T08:28:00Z"), BERLIN));

    KalenderServiceFilterTest() {
        projekte.hinzufuegen(new ProjektInfo(KUECHE, "Einbauküche Wagner", MONTAG, null, 2, true, BigDecimal.ZERO), JONAS);
        projekte.hinzufuegen(new ProjektInfo(TREPPE, "Treppe Lindenstraße", MONTAG, null, 2, true, BigDecimal.ZERO), JONAS);
        // Montag: Kueche unterwegs 4 h, Dienstag: Treppe Buero 3 h, Mittwoch: Kueche Buero 2 h,
        // Donnerstag: Kueche unterwegs, laeuft seit 07:02 (bis jetzt 10:28 = 206 Minuten)
        buchung(1, KUECHE, Standort.UNTERWEGS, "2026-10-19T08:00", "2026-10-19T12:00");
        buchung(2, TREPPE, Standort.BUERO, "2026-10-20T08:00", "2026-10-20T11:00");
        buchung(3, KUECHE, Standort.BUERO, "2026-10-21T08:00", "2026-10-21T10:00");
        buchungen.hinzufuegen(new BuchungInfo(4, JONAS, KUECHE, Standort.UNTERWEGS,
                instant("2026-10-22T07:02"), null, 0, 0, false));
    }

    @Test
    void ohneFilterErscheintAlles() {
        KalenderInhalt inhalt = woche(KalenderFilter.KEINER);

        assertThat(inhalt.eintraege()).hasSize(4);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(240 + 180 + 120 + 206);
    }

    @Test
    void nullAlsFilterWirktWieKeinFilter() {
        assertThat(woche(null)).isEqualTo(woche(KalenderFilter.KEINER));
    }

    @Test
    void projektfilterGiltFuerEintraegeUndSummen() {
        KalenderInhalt inhalt = woche(new KalenderFilter(KUECHE, null));

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(1L, 3L, 4L);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 20))).isZero();
        assertThat(inhalt.gesamtMinuten()).isEqualTo(240 + 120 + 206);
    }

    @Test
    void standortfilterGiltFuerEintraegeUndSummen() {
        KalenderInhalt inhalt = woche(new KalenderFilter(null, Standort.BUERO));

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(2L, 3L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(300);
    }

    @Test
    void beideFilterWirkenGemeinsam() {
        KalenderInhalt inhalt = woche(new KalenderFilter(KUECHE, Standort.UNTERWEGS));

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(1L, 4L);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 21))).isZero();
        assertThat(inhalt.gesamtMinuten()).isEqualTo(240 + 206);
    }

    @Test
    void ohneTrefferGibtEsDenHinweisUndSummeNull() {
        KalenderInhalt inhalt = woche(new KalenderFilter(TREPPE, Standort.HOMEOFFICE));

        assertThat(inhalt.keineTreffer()).isTrue();
        assertThat(inhalt.gesamtMinuten()).isZero();
        assertThat(inhalt.tagessummen()).hasSize(7);
    }

    @Test
    void zuruecksetzenZeigtWiederAlles() {
        KalenderInhalt gefiltert = woche(new KalenderFilter(TREPPE, null));
        KalenderInhalt zurueckgesetzt = woche(KalenderFilter.KEINER);

        assertThat(gefiltert.eintraege()).hasSize(1);
        assertThat(zurueckgesetzt.eintraege()).hasSize(4);
    }

    @Test
    void filterGiltAuchInDerMonatsansicht() {
        KalenderInhalt monat = service.eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.MONAT, MONTAG,
                new KalenderFilter(TREPPE, null));

        assertThat(monat.gesamtMinuten()).isEqualTo(180);
        assertThat(monat.tagessummen().get(LocalDate.of(2026, 10, 20))).isEqualTo(180);
    }

    @Test
    void projektfilterSchliesstBuchungOhneProjektAus() {
        buchungen.hinzufuegen(new BuchungInfo(5, JONAS, null, Standort.BUERO,
                instant("2026-10-23T08:00"), instant("2026-10-23T09:00"), 0, 60, false));

        assertThat(woche(new KalenderFilter(KUECHE, null)).eintraege())
                .extracting(KalenderEintrag::zeiteintragId).doesNotContain(5L);
        assertThat(woche(new KalenderFilter(null, Standort.BUERO)).eintraege())
                .extracting(KalenderEintrag::zeiteintragId).contains(5L);
    }

    private KalenderInhalt woche(KalenderFilter filter) {
        return service.eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.WOCHE, MONTAG, filter);
    }

    private void buchung(long id, long projektId, Standort standort, String beginn, String ende) {
        buchungen.hinzufuegen(new BuchungInfo(id, JONAS, projektId, standort, instant(beginn), instant(ende), 0, 0, false));
    }
}
