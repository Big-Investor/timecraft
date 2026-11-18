package de.timecraft.kalender.service;

import static de.timecraft.kalender.service.Testzeit.BERLIN;
import static de.timecraft.kalender.service.Testzeit.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.fake.FakeBuchungsAuskunft;
import de.timecraft.kalender.fake.FakeMitarbeiterAuskunft;
import de.timecraft.kalender.fake.FakeProjektAuskunft;
import de.timecraft.kalender.model.BuchungDetails;
import de.timecraft.kalender.model.HistorienEintrag;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Detailfenster einer Buchung mit Historie und Sichtpruefung je Rolle
 * (FA-M6-05, 10, 11, QA-M6-03, QA-M6-04, US-M6-03).
 */
class KalenderServiceDetailsTest {

    private static final long JONAS = 1;
    private static final long PETRA_PL = 2;
    private static final long ADMIN = 3;
    private static final long OTTO_PL = 7;

    private static final long KUECHE = 1024;   // Petra, aktiv: Jonas
    private static final long ARCHIV = 1001;   // Petra, archiviert: Jonas
    private static final long FREMD = 2001;    // Otto, aktiv: Jonas

    private static final long GEAENDERT = 11;  // Kueche, von Petra geaendert
    private static final long LAEUFT = 12;     // Kueche, laeuft seit 07:02
    private static final long AUF_FREMD = 13;
    private static final long AUF_ARCHIV = 14;
    private static final long OHNE_PROJEKT = 15;
    private static final long UNBEKANNTES_PROJEKT = 16;

    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
    private final FakeProjektAuskunft projekte = new FakeProjektAuskunft();
    private final FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
    // Donnerstag, 22.10.2026, 10:28 Uhr (Sommerzeit)
    private final KalenderService service = new KalenderService(buchungen, projekte, personen,
            Clock.fixed(Instant.parse("2026-10-22T08:28:00Z"), BERLIN));

    KalenderServiceDetailsTest() {
        personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, true);
        personen.hinzufuegen(PETRA_PL, "Petra", "Schulz", Rolle.PROJEKTLEITUNG, true);
        personen.hinzufuegen(ADMIN, "Anna", "Adler", Rolle.ADMIN, true);
        personen.hinzufuegen(OTTO_PL, "Otto", "Graf", Rolle.PROJEKTLEITUNG, true);

        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", PETRA_PL, true), JONAS);
        projekte.hinzufuegen(projekt(ARCHIV, "Altbau Sanierung", PETRA_PL, false), JONAS);
        projekte.hinzufuegen(projekt(FREMD, "Dachstuhl Meier", OTTO_PL, true), JONAS);

        buchungen.hinzufuegen(new BuchungInfo(GEAENDERT, JONAS, KUECHE, Standort.BUERO,
                instant("2026-10-20T07:15"), instant("2026-10-20T16:30"), 45, 510, true));
        buchungen.hinzufuegen(new BuchungInfo(LAEUFT, JONAS, KUECHE, Standort.UNTERWEGS,
                instant("2026-10-22T07:02"), null, 0, 0, false));
        buchung(AUF_FREMD, FREMD, "2026-10-19T08:00", "2026-10-19T12:00");
        buchung(AUF_ARCHIV, ARCHIV, "2026-10-19T13:00", "2026-10-19T14:00");
        buchung(OHNE_PROJEKT, null, "2026-10-19T14:00", "2026-10-19T15:00");
        buchung(UNBEKANNTES_PROJEKT, 4711L, "2026-10-19T15:00", "2026-10-19T16:00");

        // Zwei Aenderungen, absichtlich in umgekehrter Reihenfolge geliefert
        buchungen.historieHinzufuegen(GEAENDERT, new AenderungInfo(instant("2026-10-21T09:00"), ADMIN,
                AenderungsArt.AENDERUNG, "Standort", "Homeoffice", "Büro", "War im Büro"));
        buchungen.historieHinzufuegen(GEAENDERT, new AenderungInfo(instant("2026-10-20T17:05"), PETRA_PL,
                AenderungsArt.AENDERUNG, "Ende", "20.10.2026 17:30", "20.10.2026 16:30", "Ausstempeln vergessen"));
    }

    // --- Inhalt ---

    @Test
    void detailsZeigenProjektProjektleitungStandortZeitenDauerUndHistorie() {
        BuchungDetails details = service.details(JONAS, Rolle.MITARBEITER, GEAENDERT);

        assertThat(details.projektId()).isEqualTo(KUECHE);
        assertThat(details.projektName()).isEqualTo("Einbauküche Wagner");
        assertThat(details.projektleitung()).isEqualTo("Petra Schulz");
        assertThat(details.standort()).isEqualTo(Standort.BUERO);
        assertThat(details.beginn()).isEqualTo(LocalDateTime.parse("2026-10-20T07:15"));
        assertThat(details.ende()).isEqualTo(LocalDateTime.parse("2026-10-20T16:30"));
        assertThat(details.dauerMinuten()).isEqualTo(510);
        assertThat(details.geaendert()).isTrue();
        assertThat(details.laeuft()).isFalse();
    }

    @Test
    void historieZeigtAlteUndNeueWerteAenderndePersonZeitpunktUndBegruendungAeltesteZuerst() {
        assertThat(service.details(JONAS, Rolle.MITARBEITER, GEAENDERT).historie()).containsExactly(
                new HistorienEintrag(LocalDateTime.parse("2026-10-20T17:05"), "Petra Schulz", "Ende",
                        "20.10.2026 17:30", "20.10.2026 16:30", "Ausstempeln vergessen"),
                new HistorienEintrag(LocalDateTime.parse("2026-10-21T09:00"), "Anna Adler", "Standort",
                        "Homeoffice", "Büro", "War im Büro"));
    }

    @Test
    void laufendeBuchungHatKeinEndeUndDieDauerReichtBisJetzt() {
        BuchungDetails details = service.details(JONAS, Rolle.MITARBEITER, LAEUFT);

        assertThat(details.laeuft()).isTrue();
        assertThat(details.ende()).isNull();
        assertThat(details.dauerMinuten()).isEqualTo(206);
        assertThat(details.historie()).isEmpty();
    }

    @Test
    void buchungOhneProjektUndMitUnbekanntemProjekt() {
        BuchungDetails ohne = service.details(JONAS, Rolle.MITARBEITER, OHNE_PROJEKT);
        BuchungDetails unbekannt = service.details(JONAS, Rolle.MITARBEITER, UNBEKANNTES_PROJEKT);

        assertThat(ohne.projektName()).isEqualTo("Ohne Projekt");
        assertThat(ohne.projektleitung()).isEqualTo("–");
        assertThat(unbekannt.projektName()).isEqualTo("Unbekanntes Projekt");
        assertThat(unbekannt.projektleitung()).isEqualTo("–");
    }

    @Test
    void unbekannteAenderndePersonWirdAlsSolcheAngezeigt() {
        buchungen.historieHinzufuegen(AUF_FREMD, new AenderungInfo(instant("2026-10-20T08:00"), 99,
                AenderungsArt.AENDERUNG, "Beginn", "08:30", "08:00", "Korrektur"));

        assertThat(service.details(ADMIN, Rolle.ADMIN, AUF_FREMD).historie())
                .singleElement().satisfies(e -> assertThat(e.geaendertVon()).isEqualTo("Unbekannte Person"));
    }

    // --- Rechte je Rolle ---

    @Test
    void mitarbeiterSiehtNurEigeneBuchungen() {
        personen.hinzufuegen(8, "Lena", "Arndt", Rolle.MITARBEITER, true);

        assertThatThrownBy(() -> service.details(8, Rolle.MITARBEITER, GEAENDERT))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Diese Buchung dürfen Sie nicht sehen.");
    }

    @Test
    void projektleitungSiehtFremdeBuchungAufIhrAktivesProjekt() {
        assertThat(service.details(PETRA_PL, Rolle.PROJEKTLEITUNG, GEAENDERT).projektName())
                .isEqualTo("Einbauküche Wagner");
    }

    @Test
    void projektleitungSiehtKeineBuchungAufFremdeOderArchivierteProjekteOderOhneProjekt() {
        for (long id : new long[] {AUF_FREMD, AUF_ARCHIV, OHNE_PROJEKT}) {
            assertThatThrownBy(() -> service.details(PETRA_PL, Rolle.PROJEKTLEITUNG, id))
                    .isInstanceOf(ZugriffVerweigertException.class);
        }
    }

    @Test
    void fremdeProjektleitungSiehtNurBuchungenAufIhrProjekt() {
        assertThat(service.details(OTTO_PL, Rolle.PROJEKTLEITUNG, AUF_FREMD).projektName())
                .isEqualTo("Dachstuhl Meier");
        assertThatThrownBy(() -> service.details(OTTO_PL, Rolle.PROJEKTLEITUNG, GEAENDERT))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungSiehtIhreEigeneBuchungAuchAufFremdemProjekt() {
        buchungen.hinzufuegen(new BuchungInfo(20, PETRA_PL, FREMD, Standort.BUERO,
                instant("2026-10-19T08:00"), instant("2026-10-19T09:00"), 0, 60, false));

        assertThat(service.details(PETRA_PL, Rolle.PROJEKTLEITUNG, 20).projektName()).isEqualTo("Dachstuhl Meier");
    }

    @Test
    void adminSiehtAlleBuchungen() {
        for (long id : new long[] {GEAENDERT, LAEUFT, AUF_FREMD, AUF_ARCHIV, OHNE_PROJEKT}) {
            assertThat(service.details(ADMIN, Rolle.ADMIN, id).zeiteintragId()).isEqualTo(id);
        }
    }

    @Test
    void unbekannteBuchungIstFuerAdminNichtGefundenUndSonstKeinZugriff() {
        assertThatThrownBy(() -> service.details(ADMIN, Rolle.ADMIN, 999))
                .isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> service.details(PETRA_PL, Rolle.PROJEKTLEITUNG, 999))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.details(JONAS, Rolle.MITARBEITER, 999))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void ohneAnmeldungKeineDetails() {
        assertThatThrownBy(() -> service.details(JONAS, null, GEAENDERT))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    private void buchung(long id, Long projektId, String beginn, String ende) {
        buchungen.hinzufuegen(new BuchungInfo(id, JONAS, projektId, Standort.BUERO, instant(beginn), instant(ende),
                0, 60, false));
    }

    private static ProjektInfo projekt(long id, String name, long projektleitung, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, projektleitung, aktiv, BigDecimal.ZERO);
    }
}
