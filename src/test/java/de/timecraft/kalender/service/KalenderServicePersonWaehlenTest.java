package de.timecraft.kalender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.fake.FakeBuchungsAuskunft;
import de.timecraft.kalender.fake.FakeMitarbeiterAuskunft;
import de.timecraft.kalender.fake.FakeProjektAuskunft;
import de.timecraft.kalender.model.KalenderEintrag;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.kalender.model.WaehlbarePerson;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * „Person wählen“ und fremder Kalender je Rolle (FA-M6-07, FA-M6-11, QA-M6-03, QA-M6-04, UC-M6-04, US-M6-04, US-M6-06).
 */
class KalenderServicePersonWaehlenTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate MONTAG = LocalDate.of(2026, 10, 19);

    // Personen
    private static final long JONAS = 1;
    private static final long PETRA_PL = 2;
    private static final long ADMIN = 3;
    private static final long LENA = 4;
    private static final long KARL_EHEMALIG = 5;
    private static final long MIA_FREMDES_TEAM = 6;
    private static final long OTTO_PL = 7;
    private static final long BEN_ENTFERNT = 8;
    private static final long NORA_NUR_ARCHIV = 9;
    private static final long PL_OHNE_TEAM = 10;

    // Projekte
    private static final long KUECHE = 1024;     // Petra, aktiv: Jonas, Lena, Karl, Petra
    private static final long TREPPE = 1031;     // Petra, aktiv: Lena (Jonas und Ben wurden entfernt)
    private static final long ARCHIV = 1001;     // Petra, archiviert: Jonas, Nora
    private static final long FREMD = 2001;      // Otto, aktiv: Jonas, Mia
    private static final long LEER = 3001;       // PL ohne Team, aktiv: niemand

    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
    private final FakeProjektAuskunft projekte = new FakeProjektAuskunft();
    private final FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
    // Donnerstag, 22.10.2026, 10:28 Uhr
    private final KalenderService service = new KalenderService(buchungen, projekte, personen,
            Clock.fixed(Instant.parse("2026-10-22T08:28:00Z"), BERLIN));

    KalenderServicePersonWaehlenTest() {
        personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, true);
        personen.hinzufuegen(PETRA_PL, "Petra", "Schulz", Rolle.PROJEKTLEITUNG, true);
        personen.hinzufuegen(ADMIN, "Anna", "Adler", Rolle.ADMIN, true);
        personen.hinzufuegen(LENA, "Lena", "Arndt", Rolle.MITARBEITER, true);
        personen.hinzufuegen(KARL_EHEMALIG, "Karl", "Engel", Rolle.MITARBEITER, false);
        personen.hinzufuegen(MIA_FREMDES_TEAM, "Mia", "Fischer", Rolle.MITARBEITER, true);
        personen.hinzufuegen(OTTO_PL, "Otto", "Graf", Rolle.PROJEKTLEITUNG, true);
        personen.hinzufuegen(BEN_ENTFERNT, "Ben", "Becker", Rolle.MITARBEITER, true);
        personen.hinzufuegen(NORA_NUR_ARCHIV, "Nora", "Hahn", Rolle.MITARBEITER, true);
        personen.hinzufuegen(PL_OHNE_TEAM, "Paul", "Ilg", Rolle.PROJEKTLEITUNG, true);

        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", PETRA_PL, true), JONAS, LENA, KARL_EHEMALIG, PETRA_PL);
        projekte.hinzufuegen(projekt(TREPPE, "Treppe Lindenstraße", PETRA_PL, true), LENA);
        projekte.hinzufuegen(projekt(ARCHIV, "Altbau Sanierung", PETRA_PL, false), JONAS, NORA_NUR_ARCHIV);
        projekte.hinzufuegen(projekt(FREMD, "Dachstuhl Meier", OTTO_PL, true), JONAS, MIA_FREMDES_TEAM);
        projekte.hinzufuegen(projekt(LEER, "Garage Neubau", PL_OHNE_TEAM, true));

        // Woche von Jonas: Mo Kueche 4 h, Di Treppe 3 h (inzwischen entfernt), Mi Archiv 2 h,
        // Mi Fremd 1 h, Fr ohne Projekt 30 min, Do laeuft auf Fremd seit 07:00 (bis 10:28 = 208 min)
        buchung(11, JONAS, KUECHE, Standort.UNTERWEGS, "2026-10-19T08:00", "2026-10-19T12:00");
        buchung(12, JONAS, TREPPE, Standort.BUERO, "2026-10-20T08:00", "2026-10-20T11:00");
        buchung(13, JONAS, ARCHIV, Standort.BUERO, "2026-10-21T08:00", "2026-10-21T10:00");
        buchung(14, JONAS, FREMD, Standort.HOMEOFFICE, "2026-10-21T13:00", "2026-10-21T14:00");
        buchungen.hinzufuegen(new BuchungInfo(15, JONAS, null, Standort.BUERO,
                instant("2026-10-23T08:00"), instant("2026-10-23T08:30"), 0, 30, false));
        buchungen.hinzufuegen(new BuchungInfo(16, JONAS, FREMD, Standort.UNTERWEGS,
                instant("2026-10-22T07:00"), null, 0, 0, false));
        // Ben wurde aus der Treppe entfernt, seine alten Buchungen bleiben
        buchung(21, BEN_ENTFERNT, TREPPE, Standort.BUERO, "2026-10-19T08:00", "2026-10-19T16:00");
        // Lena: Treppe und Kueche
        buchung(31, LENA, TREPPE, Standort.BUERO, "2026-10-20T09:00", "2026-10-20T12:00");
        buchung(32, LENA, KUECHE, Standort.HOMEOFFICE, "2026-10-21T09:00", "2026-10-21T10:00");
    }

    // ---------- Auswahlliste „Person wählen“ ----------

    @Test
    void projektleitungWaehltNurAktiveMitarbeiterIhrerAktivenProjekte() {
        assertThat(service.waehlbarePersonen(PETRA_PL, Rolle.PROJEKTLEITUNG))
                .containsExactly(new WaehlbarePerson(LENA, "Lena Arndt"), new WaehlbarePerson(JONAS, "Jonas Becker"));
    }

    @Test
    void ausDemProjektEntfernterMitarbeiterIstNichtMehrWaehlbar() {
        assertThat(service.waehlbarePersonen(PETRA_PL, Rolle.PROJEKTLEITUNG))
                .extracting(WaehlbarePerson::mitarbeiterId).doesNotContain(BEN_ENTFERNT);
    }

    @Test
    void mitarbeiterNurAusArchiviertemProjektIstNichtWaehlbar() {
        assertThat(service.waehlbarePersonen(PETRA_PL, Rolle.PROJEKTLEITUNG))
                .extracting(WaehlbarePerson::mitarbeiterId).doesNotContain(NORA_NUR_ARCHIV);
    }

    @Test
    void deaktivierterMitarbeiterIstFuerDieProjektleitungNichtWaehlbar() {
        assertThat(service.waehlbarePersonen(PETRA_PL, Rolle.PROJEKTLEITUNG))
                .extracting(WaehlbarePerson::mitarbeiterId).doesNotContain(KARL_EHEMALIG);
    }

    @Test
    void projektleitungSiehtNichtDasTeamEinerAnderenProjektleitung() {
        assertThat(service.waehlbarePersonen(PETRA_PL, Rolle.PROJEKTLEITUNG))
                .extracting(WaehlbarePerson::mitarbeiterId).doesNotContain(MIA_FREMDES_TEAM);
        assertThat(service.waehlbarePersonen(OTTO_PL, Rolle.PROJEKTLEITUNG))
                .extracting(WaehlbarePerson::mitarbeiterId).containsExactly(JONAS, MIA_FREMDES_TEAM);
    }

    @Test
    void projektleitungOhneZugeordneteMitarbeiterBekommtLeereListe() {
        assertThat(service.waehlbarePersonen(PL_OHNE_TEAM, Rolle.PROJEKTLEITUNG)).isEmpty();
    }

    @Test
    void adminWaehltJedenBenutzerAuchEhemaligeAberNichtSichSelbst() {
        assertThat(service.waehlbarePersonen(ADMIN, Rolle.ADMIN))
                .extracting(WaehlbarePerson::name)
                .containsExactly("Lena Arndt", "Ben Becker", "Jonas Becker", "Karl Engel", "Mia Fischer",
                        "Otto Graf", "Nora Hahn", "Paul Ilg", "Petra Schulz");
    }

    @Test
    void mitarbeiterDarfKeinePersonWaehlen() {
        assertThatThrownBy(() -> service.waehlbarePersonen(JONAS, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen nur Ihren eigenen Kalender sehen.");
    }

    @Test
    void ohneAnmeldungKeineAuswahl() {
        assertThatThrownBy(() -> service.waehlbarePersonen(PETRA_PL, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    // ---------- Fremder Kalender: Projektleitung ----------

    @Test
    void projektleitungSiehtNurBuchungenAufIhreAktivenProjekteMitZuordnung() {
        KalenderInhalt inhalt = woche(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS, KalenderFilter.KEINER);

        // nicht: Treppe (Jonas entfernt), Archiv, Fremd (auch nicht laufend), ohne Projekt
        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(11L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(240);
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 21))).isZero();
        assertThat(inhalt.tagessummen().get(LocalDate.of(2026, 10, 22))).isZero();
    }

    @Test
    void projektleitungSiehtBeiVollerZuordnungAlleIhreProjekte() {
        KalenderInhalt inhalt = woche(PETRA_PL, Rolle.PROJEKTLEITUNG, LENA, KalenderFilter.KEINER);

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(31L, 32L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(180 + 60);
    }

    @Test
    void andereProjektleitungSiehtBeiDerselbenPersonNurIhrProjektAuchDieLaufendeBuchung() {
        KalenderInhalt inhalt = woche(OTTO_PL, Rolle.PROJEKTLEITUNG, JONAS, KalenderFilter.KEINER);

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(14L, 16L);
        assertThat(inhalt.eintraege()).filteredOn(KalenderEintrag::laeuft).hasSize(1);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(60 + 208);
    }

    @Test
    void filterKannDieProjektbeschraenkungNichtAufheben() {
        KalenderInhalt fremdesProjekt = woche(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS, new KalenderFilter(FREMD, null));
        KalenderInhalt standort = woche(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS, new KalenderFilter(null, Standort.BUERO));

        assertThat(fremdesProjekt.keineTreffer()).isTrue();
        assertThat(fremdesProjekt.gesamtMinuten()).isZero();
        assertThat(standort.keineTreffer()).isTrue();
    }

    @Test
    void projektbeschraenkungGiltAuchInDerMonatsansicht() {
        KalenderInhalt monat = service.fremderKalender(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS, Kalenderansicht.MONAT,
                MONTAG, KalenderFilter.KEINER);

        assertThat(monat.gesamtMinuten()).isEqualTo(240);
    }

    @Test
    void projektleitungDarfEntferntenMitarbeiterNichtDirektAufrufen() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, BEN_ENTFERNT, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen nur Kalender von Mitarbeitern Ihrer aktiven Projekte öffnen.");
    }

    @Test
    void projektleitungDarfFremdesTeamNichtDirektAufrufen() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, MIA_FREMDES_TEAM, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungDarfMitarbeiterNurAusArchiviertemProjektNichtAufrufen() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, NORA_NUR_ARCHIV, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungDarfDeaktiviertenMitarbeiterNichtAufrufen() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, KARL_EHEMALIG, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungDarfUnbekanntePersonNichtAufrufen() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, 999, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    // ---------- Fremder Kalender: Admin ----------

    @Test
    void adminSiehtAlleBuchungenDerPerson() {
        KalenderInhalt inhalt = woche(ADMIN, Rolle.ADMIN, JONAS, KalenderFilter.KEINER);

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId)
                .containsExactly(11L, 12L, 13L, 14L, 16L, 15L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(240 + 180 + 120 + 60 + 208 + 30);
    }

    @Test
    void adminSiehtAlteBuchungenEinesEntferntenMitarbeiters() {
        KalenderInhalt inhalt = woche(ADMIN, Rolle.ADMIN, BEN_ENTFERNT, KalenderFilter.KEINER);

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(21L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(480);
    }

    @Test
    void adminFilterWirktAufFremdenKalender() {
        KalenderInhalt inhalt = woche(ADMIN, Rolle.ADMIN, JONAS, new KalenderFilter(null, Standort.BUERO));

        assertThat(inhalt.eintraege()).extracting(KalenderEintrag::zeiteintragId).containsExactly(12L, 13L, 15L);
        assertThat(inhalt.gesamtMinuten()).isEqualTo(180 + 120 + 30);
    }

    @Test
    void adminBekommtBeiUnbekannterPersonEineMeldung() {
        assertThatThrownBy(() -> woche(ADMIN, Rolle.ADMIN, 999, KalenderFilter.KEINER))
                .isInstanceOf(NichtGefundenException.class)
                .hasMessage("Diese Person gibt es nicht.");
    }

    // ---------- Fremder Kalender: Mitarbeiter, eigene Person, ohne Anmeldung ----------

    @Test
    void mitarbeiterDarfKeinenFremdenKalenderDirektAufrufen() {
        assertThatThrownBy(() -> woche(JONAS, Rolle.MITARBEITER, LENA, KalenderFilter.KEINER))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen nur Ihren eigenen Kalender sehen.");
    }

    @Test
    void eigenePersonGehtNurUeberMeinKalender() {
        assertThatThrownBy(() -> woche(PETRA_PL, Rolle.PROJEKTLEITUNG, PETRA_PL, KalenderFilter.KEINER))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Ihren eigenen Kalender öffnen Sie über „Mein Kalender“.");
    }

    @Test
    void ohneAnmeldungKeinFremderKalender() {
        assertThatThrownBy(() -> woche(ADMIN, null, JONAS, KalenderFilter.KEINER))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    private KalenderInhalt woche(long angemeldet, Rolle rolle, long person, KalenderFilter filter) {
        return service.fremderKalender(angemeldet, rolle, person, Kalenderansicht.WOCHE, MONTAG, filter);
    }

    private static ProjektInfo projekt(long id, String name, long projektleitung, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, projektleitung, aktiv, BigDecimal.ZERO);
    }

    private void buchung(long id, long person, long projektId, Standort standort, String beginn, String ende) {
        buchungen.hinzufuegen(new BuchungInfo(id, person, projektId, standort, instant(beginn), instant(ende), 0, 0, false));
    }

    private static Instant instant(String ortszeit) {
        return LocalDateTime.parse(ortszeit).atZone(BERLIN).toInstant();
    }
}
