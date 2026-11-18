package de.timecraft.kalender.service;

import static de.timecraft.kalender.service.Testzeit.BERLIN;
import static de.timecraft.kalender.service.Testzeit.instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.fake.FakeBuchungsAenderung;
import de.timecraft.kalender.fake.FakeBuchungsAuskunft;
import de.timecraft.kalender.fake.FakeMitarbeiterAuskunft;
import de.timecraft.kalender.fake.FakeProjektAuskunft;
import de.timecraft.kalender.model.BuchungAenderungEingabe;
import de.timecraft.kalender.model.ProjektAuswahl;
import de.timecraft.kalender.service.UngueltigeAenderungException.Feld;
import de.timecraft.projekte.api.ProjektInfo;
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
 * Aendern einer Buchung je Rolle mit allen Regeln (FA-M6-08, 09, 11, 12, QA-M6-03, QA-A-01,
 * UC-M6-05 inkl. 1a, 1b, 4a–4d, US-M6-05, US-M6-06).
 */
class BuchungAenderungServiceTest {

    // Personen
    private static final long JONAS = 1;
    private static final long PETRA_PL = 2;
    private static final long ADMIN = 3;
    private static final long LENA = 4;
    private static final long KARL_EHEMALIG = 5;
    private static final long OTTO_PL = 7;

    // Projekte
    private static final long KUECHE = 1024;   // Petra, aktiv: Jonas, Lena, Karl, Petra
    private static final long TREPPE = 1031;   // Petra, aktiv: Lena
    private static final long ARCHIV = 1001;   // Petra, archiviert: Jonas
    private static final long FREMD = 2001;    // Otto, aktiv: Jonas

    // Buchungen
    private static final long JONAS_KUECHE = 101;
    private static final long JONAS_FREMD = 102;
    private static final long JONAS_LAEUFT = 103;
    private static final long PETRA_EIGENE = 104;
    private static final long JONAS_ARCHIV = 105;
    private static final long JONAS_OHNE_PROJEKT = 106;
    private static final long KARL_KUECHE = 107;
    private static final long ADMIN_EIGENE = 109;

    private static final String BEGRUENDUNG = "Ausstempeln vergessen";

    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft();
    private final FakeBuchungsAenderung aenderung = new FakeBuchungsAenderung();
    private final FakeProjektAuskunft projekte = new FakeProjektAuskunft();
    private final FakeMitarbeiterAuskunft personen = new FakeMitarbeiterAuskunft();
    // Montag, 02.11.2026, 10:28 Uhr (Winterzeit)
    private final BuchungAenderungService service = new BuchungAenderungService(buchungen, aenderung, projekte,
            personen, Clock.fixed(Instant.parse("2026-11-02T09:28:00Z"), BERLIN));

    BuchungAenderungServiceTest() {
        personen.hinzufuegen(JONAS, "Jonas", "Becker", Rolle.MITARBEITER, true);
        personen.hinzufuegen(PETRA_PL, "Petra", "Schulz", Rolle.PROJEKTLEITUNG, true);
        personen.hinzufuegen(ADMIN, "Anna", "Adler", Rolle.ADMIN, true);
        personen.hinzufuegen(LENA, "Lena", "Arndt", Rolle.MITARBEITER, true);
        personen.hinzufuegen(KARL_EHEMALIG, "Karl", "Engel", Rolle.MITARBEITER, false);
        personen.hinzufuegen(OTTO_PL, "Otto", "Graf", Rolle.PROJEKTLEITUNG, true);

        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", PETRA_PL, true), JONAS, LENA, KARL_EHEMALIG, PETRA_PL);
        projekte.hinzufuegen(projekt(TREPPE, "Treppe Lindenstraße", PETRA_PL, true), LENA);
        projekte.hinzufuegen(projekt(ARCHIV, "Altbau Sanierung", PETRA_PL, false), JONAS);
        projekte.hinzufuegen(projekt(FREMD, "Dachstuhl Meier", OTTO_PL, true), JONAS);

        buchung(JONAS_KUECHE, JONAS, KUECHE, "2026-10-20T07:00", "2026-10-20T15:30");
        buchung(JONAS_FREMD, JONAS, FREMD, "2026-10-21T08:00", "2026-10-21T12:00");
        buchungen.hinzufuegen(new BuchungInfo(JONAS_LAEUFT, JONAS, KUECHE, Standort.BUERO,
                instant("2026-11-02T07:00"), null, 0, 0, false));
        buchung(PETRA_EIGENE, PETRA_PL, KUECHE, "2026-10-20T07:00", "2026-10-20T15:00");
        buchung(JONAS_ARCHIV, JONAS, ARCHIV, "2026-10-19T08:00", "2026-10-19T12:00");
        buchung(JONAS_OHNE_PROJEKT, JONAS, null, "2026-10-19T13:00", "2026-10-19T14:00");
        buchung(KARL_KUECHE, KARL_EHEMALIG, KUECHE, "2026-10-19T08:00", "2026-10-19T12:00");
        buchung(ADMIN_EIGENE, ADMIN, null, "2026-10-19T08:00", "2026-10-19T12:00");
    }

    // --- Normalfall ---

    @Test
    void projektleitungAendertBuchungAufIhrProjektUndM2BekommtAlleWerte() {
        BuchungInfo stand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);

        service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, eingabe("2026-10-20T07:30", "2026-10-20T16:00",
                Standort.HOMEOFFICE, KUECHE, "  " + BEGRUENDUNG + "  "));

        assertThat(aenderung.aufrufe()).singleElement().isEqualTo(new FakeBuchungsAenderung.Aufruf(JONAS_KUECHE,
                instant("2026-10-20T07:30"), instant("2026-10-20T16:00"), Standort.HOMEOFFICE, KUECHE,
                AenderungsArt.AENDERUNG, PETRA_PL, BEGRUENDUNG));
    }

    @Test
    void adminAendertBuchungAufFremdesProjektUndBuchtUm() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_FREMD);

        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-10-21T08:00", "2026-10-21T12:00",
                Standort.BUERO, KUECHE, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).singleElement().satisfies(aufruf -> {
            assertThat(aufruf.projektId()).isEqualTo(KUECHE);
            assertThat(aufruf.geaendertVonMitarbeiterId()).isEqualTo(ADMIN);
        });
    }

    @Test
    void adminAendertBuchungEinesEhemaligenMitarbeiters() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, KARL_KUECHE);

        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-10-19T08:00", "2026-10-19T11:00",
                Standort.BUERO, KUECHE, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).hasSize(1);
    }

    @Test
    void adminDarfBuchungOhneProjektOhneProjektLassen() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_OHNE_PROJEKT);

        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-10-19T13:00", "2026-10-19T14:30",
                Standort.BUERO, null, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).singleElement().satisfies(aufruf -> assertThat(aufruf.projektId()).isNull());
    }

    @Test
    void adminDarfBuchungOhneProjektEinemProjektZuordnen() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_OHNE_PROJEKT);

        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-10-19T13:00", "2026-10-19T14:00",
                Standort.BUERO, KUECHE, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).singleElement().satisfies(aufruf -> assertThat(aufruf.projektId()).isEqualTo(KUECHE));
    }

    @Test
    void endeGenauJetztIstErlaubt() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_KUECHE);

        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-11-02T09:00", "2026-11-02T10:28",
                Standort.BUERO, KUECHE, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).hasSize(1);
    }

    @Test
    void ortszeitenUeberDieZeitumstellungWerdenMinutengenauUmgerechnet() {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_KUECHE);

        // Nacht vom 24. auf den 25.10.2026: 03:00 Sommerzeit wird 02:00 Winterzeit, die Nacht hat 9 Stunden
        service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-10-24T22:00", "2026-10-25T06:00",
                Standort.BUERO, KUECHE, BEGRUENDUNG));

        assertThat(aenderung.aufrufe()).singleElement().satisfies(aufruf -> {
            assertThat(aufruf.beginn()).isEqualTo(Instant.parse("2026-10-24T20:00:00Z"));
            assertThat(aufruf.ende()).isEqualTo(Instant.parse("2026-10-25T05:00:00Z"));
        });
    }

    // --- Rechte je Rolle (QA-M6-03, UC-M6-05 1b) ---

    @Test
    void mitarbeiterDarfNichtsAendernAuchNichtPerDirektemAufruf() {
        BuchungInfo stand = buchungen.buchung(JONAS_KUECHE).orElseThrow();

        assertThatThrownBy(() -> service.aendern(LENA, Rolle.MITARBEITER, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen im Kalender keine Buchungen ändern.");
        assertThatThrownBy(() -> service.buchungZumAendern(LENA, Rolle.MITARBEITER, JONAS_KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.waehlbareZielprojekte(LENA, Rolle.MITARBEITER, JONAS_KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(service.darfAendern(LENA, Rolle.MITARBEITER, JONAS_KUECHE)).isFalse();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void mitarbeiterDarfAuchSeineEigeneBuchungNichtAendern() {
        BuchungInfo stand = buchungen.buchung(JONAS_KUECHE).orElseThrow();

        assertThatThrownBy(() -> service.aendern(JONAS, Rolle.MITARBEITER, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void ohneAnmeldungWirdNichtsGeaendert() {
        BuchungInfo stand = buchungen.buchung(JONAS_KUECHE).orElseThrow();

        assertThatThrownBy(() -> service.aendern(PETRA_PL, null, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThat(service.darfAendern(PETRA_PL, null, JONAS_KUECHE)).isFalse();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void projektleitungDarfBuchungAufFremdesProjektNichtAendern() {
        BuchungInfo stand = buchungen.buchung(JONAS_FREMD).orElseThrow();

        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, eingabe("2026-10-21T08:00",
                "2026-10-21T12:00", Standort.BUERO, KUECHE, BEGRUENDUNG)))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen nur Buchungen auf Ihre aktiven Projekte ändern.");
        assertThat(service.darfAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_FREMD)).isFalse();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void nichtVerantwortlicheProjektleitungDarfNichtAendern() {
        BuchungInfo stand = buchungen.buchung(JONAS_KUECHE).orElseThrow();

        assertThatThrownBy(() -> service.aendern(OTTO_PL, Rolle.PROJEKTLEITUNG, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(service.darfAendern(OTTO_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE)).isFalse();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void projektleitungDarfIhreEigeneBuchungNichtAendern() {
        BuchungInfo stand = buchungen.buchung(PETRA_EIGENE).orElseThrow();

        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, eingabe("2026-10-20T07:00",
                "2026-10-20T14:00", Standort.BUERO, KUECHE, BEGRUENDUNG)))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Ihre eigenen Buchungen können Sie im Kalender nicht ändern.");
        assertThat(service.darfAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, PETRA_EIGENE)).isFalse();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void adminDarfSeineEigeneBuchungNichtAendern() {
        assertThatThrownBy(() -> service.buchungZumAendern(ADMIN, Rolle.ADMIN, ADMIN_EIGENE))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(service.darfAendern(ADMIN, Rolle.ADMIN, ADMIN_EIGENE)).isFalse();
    }

    @Test
    void projektleitungDarfBuchungAufArchiviertesProjektNichtAendern() {
        assertThatThrownBy(() -> service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_ARCHIV))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(service.darfAendern(ADMIN, Rolle.ADMIN, JONAS_ARCHIV)).isTrue();
    }

    @Test
    void projektleitungDarfBuchungEinesDeaktiviertenMitarbeitersNichtAendern() {
        assertThatThrownBy(() -> service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, KARL_KUECHE))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungDarfBuchungEinesAusDemProjektEntferntenMitarbeitersNichtAendern() {
        // Lena ist KUECHE zugeordnet, hat dort gebucht und wird dann aus KUECHE entfernt
        buchung(110, LENA, KUECHE, "2026-10-19T08:00", "2026-10-19T12:00");
        projekte.hinzufuegen(projekt(KUECHE, "Einbauküche Wagner", PETRA_PL, true), JONAS, PETRA_PL);

        assertThatThrownBy(() -> service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, 110))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(service.darfAendern(ADMIN, Rolle.ADMIN, 110)).isTrue();
    }

    @Test
    void projektleitungDarfNurAufEinesIhrerProjekteUmbuchen() {
        BuchungInfo stand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);

        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, eingabe("2026-10-20T07:00",
                "2026-10-20T15:30", Standort.BUERO, FREMD, BEGRUENDUNG)))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie können nur auf eines Ihrer Projekte umbuchen.");
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void unbekannteBuchungIstFuerAdminNichtGefundenUndFuerProjektleitungKeinZugriff() {
        assertThatThrownBy(() -> service.buchungZumAendern(ADMIN, Rolle.ADMIN, 999))
                .isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, 999))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void berechtigteBekommenBearbeitenAngeboten() {
        assertThat(service.darfAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE)).isTrue();
        assertThat(service.darfAendern(ADMIN, Rolle.ADMIN, JONAS_FREMD)).isTrue();
        assertThat(service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE))
                .isEqualTo(buchungen.buchung(JONAS_KUECHE).orElseThrow());
    }

    // --- Laufende Buchung (UC-M6-05 1a) ---

    @Test
    void laufendeBuchungIstNichtAenderbar() {
        BuchungInfo stand = buchungen.buchung(JONAS_LAEUFT).orElseThrow();

        assertThat(service.darfAendern(ADMIN, Rolle.ADMIN, JONAS_LAEUFT)).isFalse();
        assertThat(service.darfAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_LAEUFT)).isFalse();
        assertThatThrownBy(() -> service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe("2026-11-02T07:00",
                "2026-11-02T09:00", Standort.BUERO, KUECHE, BEGRUENDUNG)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Buchung läuft noch. Sie ist erst nach dem Ausstempeln änderbar.");
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    // --- Eingaben (UC-M6-05 4a, 4c) ---

    @Test
    void ohneBegruendungWirdNichtsGespeichert() {
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, KUECHE, null),
                Feld.BEGRUENDUNG, "Bitte geben Sie eine Begründung an.");
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, KUECHE, "   "),
                Feld.BEGRUENDUNG, "Bitte geben Sie eine Begründung an.");
    }

    @Test
    void endeVorBeginnWirdAmFeldEndeGemeldet() {
        pruefeFeldfehler(eingabe("2026-10-20T15:00", "2026-10-20T07:00", Standort.BUERO, KUECHE, BEGRUENDUNG),
                Feld.ENDE, "Das Ende muss nach dem Beginn liegen.");
    }

    @Test
    void endeGleichBeginnIstUngueltig() {
        pruefeFeldfehler(eingabe("2026-10-20T15:00", "2026-10-20T15:00", Standort.BUERO, KUECHE, BEGRUENDUNG),
                Feld.ENDE, "Das Ende muss nach dem Beginn liegen.");
    }

    @Test
    void endeInDerZukunftWirdAmFeldEndeGemeldet() {
        pruefeFeldfehler(eingabe("2026-11-02T09:00", "2026-11-02T10:29", Standort.BUERO, KUECHE, BEGRUENDUNG),
                Feld.ENDE, "Das Ende darf nicht in der Zukunft liegen.");
    }

    @Test
    void fehlendeFelderWerdenAmFeldGemeldet() {
        pruefeFeldfehler(eingabe(null, "2026-10-20T15:00", Standort.BUERO, KUECHE, BEGRUENDUNG),
                Feld.BEGINN, "Bitte geben Sie den Beginn an.");
        pruefeFeldfehler(eingabe("2026-10-20T07:00", null, Standort.BUERO, KUECHE, BEGRUENDUNG),
                Feld.ENDE, "Bitte geben Sie das Ende an.");
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", null, KUECHE, BEGRUENDUNG),
                Feld.STANDORT, "Bitte wählen Sie einen Standort.");
    }

    // --- Zielprojekt (FA-M6-09) ---

    @Test
    void zielprojektMussAktivSein() {
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, ARCHIV, BEGRUENDUNG),
                Feld.PROJEKT, "Das Projekt „Altbau Sanierung“ ist nicht aktiv.");
    }

    @Test
    void personMussDemZielprojektZugeordnetSein() {
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, TREPPE, BEGRUENDUNG),
                Feld.PROJEKT, "Die Person ist dem Projekt „Treppe Lindenstraße“ nicht zugeordnet.");
    }

    @Test
    void unbekanntesZielprojektWirdGemeldet() {
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, 4711L, BEGRUENDUNG),
                Feld.PROJEKT, "Dieses Projekt gibt es nicht.");
    }

    @Test
    void projektDarfNichtEntferntWerden() {
        pruefeFeldfehler(eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, null, BEGRUENDUNG),
                Feld.PROJEKT, "Bitte wählen Sie ein Projekt.");
    }

    @Test
    void projektleitungSiehtNurIhreAktivenProjekteDerPersonAlsZiel() {
        assertThat(service.waehlbareZielprojekte(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE))
                .containsExactly(new ProjektAuswahl(KUECHE, "Einbauküche Wagner"));
    }

    @Test
    void adminSiehtAlleAktivenProjekteDerPersonAlsZielSortiertNachName() {
        assertThat(service.waehlbareZielprojekte(ADMIN, Rolle.ADMIN, JONAS_KUECHE)).containsExactly(
                new ProjektAuswahl(FREMD, "Dachstuhl Meier"), new ProjektAuswahl(KUECHE, "Einbauküche Wagner"));
    }

    // --- Fehler von M2 (UC-M6-05 4b) ---

    @Test
    void fachausnahmeVonM2WirdMitIhrerMeldungWeitergegeben() {
        BuchungInfo stand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);
        aenderung.wirftFehler("Die Buchung überschneidet sich mit der Buchung vom 20.10.2026, 15:00–17:00 Uhr.");

        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Buchung überschneidet sich mit der Buchung vom 20.10.2026, 15:00–17:00 Uhr.");
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    // --- Gleichzeitige Aenderung (UC-M6-05 4d) ---

    @Test
    void inzwischenGeaenderteBuchungWirdNichtUeberschriebenUndDerAktuelleStandKommtMit() {
        BuchungInfo stand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);
        BuchungInfo vonAdminGeaendert = new BuchungInfo(JONAS_KUECHE, JONAS, KUECHE, Standort.BUERO,
                instant("2026-10-20T07:00"), instant("2026-10-20T15:00"), 0, 480, true);
        buchungen.ersetzen(vonAdminGeaendert);

        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, stand, gueltigeEingabeFuerKueche()))
                .isInstanceOfSatisfying(BuchungInzwischenGeaendertException.class, fehler -> {
                    assertThat(fehler.aktuellerStand()).isEqualTo(vonAdminGeaendert);
                    assertThat(fehler).hasMessageStartingWith("Buchung wurde inzwischen geändert.");
                });
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void nachDemNeuLadenDesStandsKannGespeichertWerden() {
        BuchungInfo alterStand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);
        buchungen.ersetzen(new BuchungInfo(JONAS_KUECHE, JONAS, KUECHE, Standort.BUERO,
                instant("2026-10-20T07:00"), instant("2026-10-20T15:00"), 0, 480, true));
        assertThatThrownBy(() -> service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, alterStand,
                gueltigeEingabeFuerKueche())).isInstanceOf(BuchungInzwischenGeaendertException.class);
        BuchungInfo neuerStand = service.buchungZumAendern(PETRA_PL, Rolle.PROJEKTLEITUNG, JONAS_KUECHE);

        service.aendern(PETRA_PL, Rolle.PROJEKTLEITUNG, neuerStand, gueltigeEingabeFuerKueche());

        assertThat(aenderung.aufrufe()).hasSize(1);
    }

    // --- Hilfen ---

    /** Admin aendert JONAS_KUECHE mit der Eingabe; erwartet einen Fehler am Feld und dass nichts gespeichert wird. */
    private void pruefeFeldfehler(BuchungAenderungEingabe eingabe, Feld feld, String meldung) {
        BuchungInfo stand = service.buchungZumAendern(ADMIN, Rolle.ADMIN, JONAS_KUECHE);

        assertThatThrownBy(() -> service.aendern(ADMIN, Rolle.ADMIN, stand, eingabe))
                .isInstanceOfSatisfying(UngueltigeAenderungException.class,
                        fehler -> assertThat(fehler.feld()).isEqualTo(feld))
                .hasMessage(meldung);
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    private static BuchungAenderungEingabe gueltigeEingabeFuerKueche() {
        return eingabe("2026-10-20T07:00", "2026-10-20T15:00", Standort.BUERO, KUECHE, BEGRUENDUNG);
    }

    private static BuchungAenderungEingabe eingabe(String beginn, String ende, Standort standort, Long projektId,
            String begruendung) {
        return new BuchungAenderungEingabe(beginn == null ? null : LocalDateTime.parse(beginn),
                ende == null ? null : LocalDateTime.parse(ende), standort, projektId, begruendung);
    }

    private void buchung(long id, long person, Long projektId, String beginn, String ende) {
        buchungen.hinzufuegen(new BuchungInfo(id, person, projektId, Standort.BUERO, instant(beginn), instant(ende),
                0, 0, false));
    }

    private static ProjektInfo projekt(long id, String name, long projektleitung, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, projektleitung, aktiv, BigDecimal.ZERO);
    }
}
