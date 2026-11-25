package de.timecraft.kalender.service;

import static de.timecraft.kalender.service.Testzeit.BERLIN;
import static de.timecraft.kalender.service.Testzeit.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
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
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Eigener Kalender: Eintraege, Summen, Mitternacht, laufende Buchung, Zeitumstellung (FA-M6-01, 03, 04, 11; QA-M6-01).
 *
 * @author Bennet Antonio Brinkmeier
 */
class KalenderServiceInhaltTest {

    private static final long JONAS = 1;
    private static final long LENA = 2;
    private static final long KUECHE = 1024;
    private static final long TREPPE = 1031;
    private static final LocalDate DONNERSTAG = LocalDate.of(2026, 10, 22);

    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
    private final FakeProjektAuskunft projekte = new FakeProjektAuskunft();

    KalenderServiceInhaltTest() {
        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner"), JONAS);
        projekte.hinzufuegen(projekt(TREPPE, "Treppe Lindenstraße"), JONAS);
    }

    @Test
    void ohneAnmeldungWirdNichtsGezeigt() {
        assertThatThrownBy(() -> service("2026-10-22T10:00").eigenerKalender(JONAS, null, Kalenderansicht.WOCHE, DONNERSTAG, KalenderFilter.KEINER))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void jedeRolleSiehtIhrenEigenenKalender() {
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-19T07:00", "2026-10-19T15:00", 0);
        buchung(2, LENA, KUECHE, Standort.BUERO, "2026-10-19T08:00", "2026-10-19T12:00", 0);

        for (Rolle rolle : Rolle.values()) {
            KalenderInhalt jonas = service("2026-10-22T10:00").eigenerKalender(JONAS, rolle, Kalenderansicht.WOCHE, DONNERSTAG, KalenderFilter.KEINER);
            assertThat(jonas.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(1L);
        }
    }

    @Test
    void wocheZeigtBlockMitProjektStandortUndUhrzeit() {
        buchung(1, JONAS, KUECHE, Standort.UNTERWEGS, "2026-10-19T07:00", "2026-10-19T15:45", 45);

        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.ersterTag()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(inhalt.letzterTag()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(inhalt.eintraege()).singleElement().satisfies(e -> {
            assertThat(e.projektName()).isEqualTo("Einbauküche Wagner");
            assertThat(e.projektId()).isEqualTo(KUECHE);
            assertThat(e.standort()).isEqualTo(Standort.UNTERWEGS);
            assertThat(e.tag()).isEqualTo(LocalDate.of(2026, 10, 19));
            assertThat(e.beginn()).isEqualTo(LocalDateTime.parse("2026-10-19T07:00"));
            assertThat(e.ende()).isEqualTo(LocalDateTime.parse("2026-10-19T15:45"));
            assertThat(e.laeuft()).isFalse();
            assertThat(e.geaendert()).isFalse();
        });
    }

    @Test
    void summenSindDieAnrechenbarenMinutenVonM2() {
        // Wie im Mockup: 07:00–15:45 mit 45 Minuten Pause = 8:00 h, 07:15–16:30 mit 45 Minuten Pause = 8:30 h
        buchung(1, JONAS, KUECHE, Standort.UNTERWEGS, "2026-10-19T07:00", "2026-10-19T15:45", 45);
        buchung(2, JONAS, TREPPE, Standort.BUERO, "2026-10-20T07:15", "2026-10-20T16:30", 45);

        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 19))).isEqualTo(480);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 20))).isEqualTo(510);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(990);
    }

    @Test
    void tagessummenEnthaltenJedenTagDerWocheAuchOhneBuchung() {
        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.tagessummen()).hasSize(7).containsValues(0);
        assertThat(inhalt.tagessummen().keySet()).first().isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(inhalt.gesamtMinuten()).isZero();
        assertThat(inhalt.keineTreffer()).isTrue();
    }

    @Test
    void mehrereBuchungenAmTagWerdenAddiertUndNachBeginnSortiert() {
        buchung(2, JONAS, TREPPE, Standort.BUERO, "2026-10-21T13:00", "2026-10-21T16:00", 0);
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-21T08:00", "2026-10-21T12:00", 0);

        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(1L, 2L);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 21))).isEqualTo(420);
    }

    @Test
    void buchungUeberMitternachtWirdAufBeideTageAufgeteilt() {
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-20T22:00", "2026-10-21T02:30", 0);

        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.eintraege()).hasSize(2);
        KalenderEintrag erster = inhalt.eintraege().get(0);
        KalenderEintrag zweiter = inhalt.eintraege().get(1);
        assertThat(erster.tag()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(erster.ende()).isEqualTo(LocalDateTime.parse("2026-10-21T00:00"));
        assertThat(zweiter.tag()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(zweiter.beginn()).isEqualTo(LocalDateTime.parse("2026-10-21T00:00"));
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 20))).isEqualTo(120);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 21))).isEqualTo(150);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(270);
    }

    @Test
    void buchungUeberWochengrenzeZaehltNurMitDemTeilInDerWoche() {
        // Sonntag 25.10. 22:00 bis Montag 26.10. 01:00 (Winterzeit)
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-25T22:00", "2026-10-26T01:00", 0);

        KalenderInhalt dieseWoche = wocheMit(LocalDate.of(2026, 10, 25));
        KalenderInhalt naechsteWoche = wocheMit(LocalDate.of(2026, 10, 26));

        assertThat(dieseWoche.gesamtMinuten()).isEqualTo(120);
        assertThat(dieseWoche.eintraege()).singleElement().extracting(KalenderEintrag::tag).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(naechsteWoche.gesamtMinuten()).isEqualTo(60);
    }

    @Test
    void buchungUeberMonatsendeZaehltInDerMonatsansichtNurImEigenenMonat() {
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-31T22:00", "2026-11-01T02:00", 0);

        KalenderService service = service("2026-11-05T10:00");
        KalenderInhalt oktober = service.eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.MONAT, LocalDate.of(2026, 10, 10), KalenderFilter.KEINER);
        KalenderInhalt november = service.eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.MONAT, LocalDate.of(2026, 11, 10), KalenderFilter.KEINER);

        assertThat(oktober.tagessummen()).hasSize(31);
        assertThat(oktober.tagessummen().get(LocalDate.of(2026, 10, 31))).isEqualTo(120);
        assertThat(oktober.gesamtMinuten()).isEqualTo(120);
        assertThat(november.tagessummen().get(LocalDate.of(2026, 11, 1))).isEqualTo(120);
        assertThat(november.gesamtMinuten()).isEqualTo(120);
    }

    @Test
    void wocheUeberMonatsgrenzeHoltBeideMonate() {
        // Woche 26.10.–01.11.2026
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-30T08:00", "2026-10-30T12:00", 0);
        buchung(2, JONAS, KUECHE, Standort.BUERO, "2026-11-01T08:00", "2026-11-01T09:00", 0);

        KalenderInhalt inhalt = service("2026-11-05T10:00")
                .eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 28), KalenderFilter.KEINER);

        assertThat(inhalt.gesamtMinuten()).isEqualTo(300);
    }

    @Test
    void zeitumstellungImHerbstZaehltDieZusaetzlicheStunde() {
        // 25.10.2026: Die Uhr springt um 03:00 auf 02:00 zurueck. 00:00–06:00 Ortszeit sind 7 echte Stunden.
        buchungInstant(1, JONAS, "2026-10-24T22:00:00Z", "2026-10-25T05:00:00Z", 0);

        KalenderInhalt inhalt = wocheMit(LocalDate.of(2026, 10, 25));

        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 25))).isEqualTo(420);
        assertThat(inhalt.eintraege()).singleElement().satisfies(e -> {
            assertThat(e.beginn()).isEqualTo(LocalDateTime.parse("2026-10-25T00:00"));
            assertThat(e.ende()).isEqualTo(LocalDateTime.parse("2026-10-25T06:00"));
        });
    }

    @Test
    void zeitumstellungImFruehjahrZaehltDieFehlendeStundeNicht() {
        // 29.03.2026: Die Uhr springt um 02:00 auf 03:00 vor. 00:00–06:00 Ortszeit sind 5 echte Stunden.
        buchungInstant(1, JONAS, "2026-03-28T23:00:00Z", "2026-03-29T04:00:00Z", 0);

        KalenderInhalt inhalt = service("2026-03-30T10:00")
                .eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.TAG, LocalDate.of(2026, 3, 29), KalenderFilter.KEINER);

        assertThat(inhalt.gesamtMinuten()).isEqualTo(300);
    }

    @Test
    void buchungUeberMitternachtInDerUmstellungsnacht() {
        // Samstag 24.10. 22:00 (Sommerzeit) bis Sonntag 25.10. 04:00 (Winterzeit): 2 h + 5 h
        buchungInstant(1, JONAS, "2026-10-24T20:00:00Z", "2026-10-25T03:00:00Z", 0);

        KalenderInhalt inhalt = wocheMit(LocalDate.of(2026, 10, 25));

        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 24))).isEqualTo(120);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 25))).isEqualTo(300);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(420);
    }

    @Test
    void laufendeBuchungReichtBisJetztUndIstMarkiert() {
        buchungLaufend(1, JONAS, "2026-10-22T07:02");

        KalenderInhalt inhalt = woche("2026-10-22T10:28");

        assertThat(inhalt.eintraege()).singleElement().satisfies(e -> {
            assertThat(e.laeuft()).isTrue();
            assertThat(e.beginn()).isEqualTo(LocalDateTime.parse("2026-10-22T07:02"));
            assertThat(e.ende()).isEqualTo(LocalDateTime.parse("2026-10-22T10:28"));
            assertThat(e.minuten()).isEqualTo(206);
            assertThat(e.projektName()).isEqualTo("Einbauküche Wagner");
        });
        assertThat(inhalt.tagessummen().get(DONNERSTAG)).isEqualTo(206);
    }

    @Test
    void laufendeBuchungUeberMitternachtWirdAufgeteilt() {
        buchungLaufend(1, JONAS, "2026-10-21T22:00");

        KalenderInhalt inhalt = woche("2026-10-22T01:15");

        assertThat(inhalt.eintraege()).hasSize(2).allMatch(KalenderEintrag::laeuft);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 21))).isEqualTo(120);
        assertThat(inhalt.tagessummen().get(DONNERSTAG)).isEqualTo(75);
    }

    @Test
    void laufendeBuchungInDerUmstellungsnachtZaehltEchteMinuten() {
        // Laeuft seit Samstag 24.10. 22:00 (Sommerzeit), jetzt Sonntag 25.10. 10:00 (Winterzeit)
        buchungen.hinzufuegen(new BuchungInfo(1, JONAS, KUECHE, Standort.BUERO,
                Instant.parse("2026-10-24T20:00:00Z"), null, 0, 0, false));

        KalenderInhalt inhalt = woche("2026-10-25T10:00");

        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 24))).isEqualTo(120);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 25))).isEqualTo(660);
    }

    @Test
    void laufendeBuchungErscheintNichtInEinerSpaeterenWoche() {
        buchungLaufend(1, JONAS, "2026-10-22T07:00");

        KalenderInhalt naechsteWoche = service("2026-10-22T10:00")
                .eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 29), KalenderFilter.KEINER);

        assertThat(naechsteWoche.keineTreffer()).isTrue();
        assertThat(naechsteWoche.gesamtMinuten()).isZero();
    }

    @Test
    void geaenderteBuchungIstMarkiert() {
        buchungen.hinzufuegen(new BuchungInfo(1, JONAS, TREPPE, Standort.BUERO,
                instant("2026-10-20T07:15"), instant("2026-10-20T16:30"), 45, 510, true));

        assertThat(woche("2026-10-22T10:00").eintraege()).singleElement().extracting(KalenderEintrag::geaendert).isEqualTo(true);
    }

    @Test
    void buchungOhneProjektOderMitUnbekanntemProjektHatVerstaendlichenNamen() {
        buchungen.hinzufuegen(new BuchungInfo(1, JONAS, null, Standort.BUERO,
                instant("2026-10-19T08:00"), instant("2026-10-19T09:00"), 0, 60, false));
        buchung(2, JONAS, 9999, Standort.BUERO, "2026-10-20T08:00", "2026-10-20T09:00", 0);

        KalenderInhalt inhalt = woche("2026-10-22T10:00");

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::projektName)
                .containsExactly("Ohne Projekt", "Unbekanntes Projekt");
    }

    @Test
    void tagesansichtZeigtNurDenTag() {
        buchung(1, JONAS, KUECHE, Standort.BUERO, "2026-10-21T08:00", "2026-10-21T12:00", 0);
        buchung(2, JONAS, KUECHE, Standort.BUERO, "2026-10-22T08:00", "2026-10-22T09:00", 0);

        KalenderInhalt inhalt = service("2026-10-22T10:00")
                .eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.TAG, DONNERSTAG, KalenderFilter.KEINER);

        assertThat(inhalt.tagessummen()).containsOnlyKeys(DONNERSTAG);
        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(2L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(60);
    }

    // --- Hilfen ---

    private KalenderService service(String jetztOrtszeit) {
        Clock uhr = Clock.fixed(instant(jetztOrtszeit), BERLIN);
        return new KalenderService(buchungen, projekte, new FakeMitarbeiterAuskunft(), uhr);
    }

    private KalenderInhalt woche(String jetztOrtszeit) {
        KalenderService service = service(jetztOrtszeit);
        return service.eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.WOCHE, service.heute(), KalenderFilter.KEINER);
    }

    /** Woche um den Bezugstag, angesehen am 05.11.2026 (alle Buchungen der Tests liegen davor). */
    private KalenderInhalt wocheMit(LocalDate bezugstag) {
        return service("2026-11-05T10:00").eigenerKalender(JONAS, Rolle.MITARBEITER, Kalenderansicht.WOCHE, bezugstag, KalenderFilter.KEINER);
    }

    private void buchung(long id, long mitarbeiterId, long projektId, Standort standort,
            String beginnOrtszeit, String endeOrtszeit, int pause) {
        buchungen.hinzufuegen(new BuchungInfo(id, mitarbeiterId, projektId, standort,
                instant(beginnOrtszeit), instant(endeOrtszeit), pause, 0, false));
    }

    private void buchungInstant(long id, long mitarbeiterId, String beginnUtc, String endeUtc, int pause) {
        buchungen.hinzufuegen(new BuchungInfo(id, mitarbeiterId, KUECHE, Standort.BUERO,
                Instant.parse(beginnUtc), Instant.parse(endeUtc), pause, 0, false));
    }

    private void buchungLaufend(long id, long mitarbeiterId, String beginnOrtszeit) {
        buchungen.hinzufuegen(new BuchungInfo(id, mitarbeiterId, KUECHE, Standort.UNTERWEGS,
                instant(beginnOrtszeit), null, 0, 0, false));
    }

    private static ProjektInfo projekt(long id, String name) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, LENA, true, BigDecimal.ZERO);
    }
}
