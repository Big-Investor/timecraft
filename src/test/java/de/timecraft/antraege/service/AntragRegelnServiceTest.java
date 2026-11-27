package de.timecraft.antraege.service;

import static de.timecraft.antraege.Beispiele.FESTE_UHR;
import static de.timecraft.antraege.Beispiele.abwesenheit;
import static de.timecraft.antraege.Beispiele.buchung;
import static de.timecraft.antraege.Beispiele.mitStatus;
import static de.timecraft.antraege.Beispiele.storno;
import static de.timecraft.antraege.Beispiele.urlaub;
import static de.timecraft.antraege.Beispiele.zeit;
import static de.timecraft.antraege.Beispiele.zeitkorrektur;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.JONAS;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.LENA;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.MEHMET;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.PETER;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.SARA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.fake.FakeMitarbeiterAuskunft;
import de.timecraft.antraege.fake.FakeProjektAuskunft;
import de.timecraft.antraege.fake.FakeZeiterfassung;
import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.MaterialanpassungsantragTestersatz;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** Fachregeln (FA-M5-07, QA-M5-06) gegen die echte Testdatenbank und die Fakes, ohne Oberflaeche. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AntragRegelnServiceTest {

    private static final LocalDate MO = LocalDate.of(2026, 12, 7);
    private static final LocalDate FR = LocalDate.of(2026, 12, 11);
    private static final LocalDate DI_VORWOCHE = LocalDate.of(2026, 11, 24);

    @Autowired
    private AntragRepository repository;

    private FakeMitarbeiterAuskunft mitarbeiter;
    private FakeZeiterfassung zeiterfassung;
    private AntragRegelnService regeln;

    @BeforeEach
    void aufbauen() {
        mitarbeiter = FakeMitarbeiterAuskunft.beispiel();
        zeiterfassung = new FakeZeiterfassung();
        regeln = new AntragRegelnService(repository, mitarbeiter, zeiterfassung, zeiterfassung,
                FakeProjektAuskunft.beispiel(), FESTE_UHR);
    }

    private <T extends Antrag> T gespeichert(T antrag) {
        return repository.save(antrag);
    }

    private void fehler(Antrag antrag, String meldung) {
        assertThatThrownBy(() -> regeln.pruefe(antrag)).isInstanceOf(FachlicheException.class).hasMessage(meldung);
    }

    @Nested
    class Allgemein {

        @Test
        void antragstellerOhneVorgesetztenWirdAbgewiesen() {
            fehler(urlaub(PETER, Urlaubsart.ERHOLUNGSURLAUB, MO, FR),
                    "Ihnen ist keine zuständige Projektleitung zugeordnet. Bitte wenden Sie sich an die Verwaltung.");
        }

        @Test
        void unbekannterAntragstellerWirdAbgewiesen() {
            fehler(urlaub(99, Urlaubsart.ERHOLUNGSURLAUB, MO, FR), "Der Antragsteller ist nicht bekannt.");
        }

        @Test
        void begruendungHoechstens255Zeichen() {
            Abwesenheitsantrag grenze = urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR);
            grenze.setBegruendung("x".repeat(255));
            assertThatCode(() -> regeln.pruefe(grenze)).doesNotThrowAnyException();

            Abwesenheitsantrag zuLang = urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR);
            zuLang.setBegruendung("x".repeat(256));
            fehler(zuLang, "Die Begründung darf höchstens 255 Zeichen lang sein.");
        }

        @Test
        void antraegeAndererModuleWerdenNichtGeprueft() {
            MaterialanpassungsantragTestersatz material = new MaterialanpassungsantragTestersatz();
            material.setAntragstellerId(JONAS);
            fehler(material, "Dieser Antragstyp wird hier nicht bearbeitet.");
        }

        @Test
        void zustaendigIstDerVorgesetzte() {
            assertThat(regeln.istZustaendig(LENA, JONAS)).isTrue();
            assertThat(regeln.istZustaendig(LENA, MEHMET)).isTrue();
            assertThat(regeln.istZustaendig(PETER, JONAS)).isFalse();
            assertThat(regeln.istZustaendig(LENA, SARA)).isFalse();
            assertThat(regeln.istZustaendig(PETER, LENA)).isTrue();
            assertThat(regeln.istZustaendig(LENA, 99)).isFalse();
        }
    }

    @Nested
    class Abwesenheit {

        @Test
        void gueltigeAntraegeAllerArtenBestehen() {
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> regeln.pruefe(abwesenheit(JONAS, AbwesenheitsArt.KRANKHEIT, MO, MO)))
                    .doesNotThrowAnyException();
            assertThatCode(() -> regeln.pruefe(abwesenheit(JONAS, AbwesenheitsArt.SONSTIGE, MO, FR)))
                    .doesNotThrowAnyException();
        }

        @Test
        void pflichtfelder() {
            fehler(abwesenheit(JONAS, null, MO, FR), "Bitte wählen Sie die Art der Abwesenheit.");
            fehler(urlaub(JONAS, null, MO, FR), "Bitte wählen Sie die Urlaubsart.");
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, null, FR),
                    "Bitte geben Sie Beginn und Ende des Zeitraums an.");
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, null),
                    "Bitte geben Sie Beginn und Ende des Zeitraums an.");
        }

        @Test
        void urlaubsartNurBeiUrlaub() {
            Abwesenheitsantrag krank = abwesenheit(JONAS, AbwesenheitsArt.KRANKHEIT, MO, FR);
            krank.setUrlaubsart(Urlaubsart.SONDERURLAUB);
            fehler(krank, "Eine Urlaubsart gibt es nur bei Urlaub.");
        }

        @Test
        void endeNichtVorBeginn() {
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, FR, MO), "Das Ende darf nicht vor dem Beginn liegen.");
        }

        @Test
        void ueberschneidungMitEigenemOffenenOderGenehmigtenAntrag() {
            gespeichert(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, MO.plusDays(1)));
            fehler(abwesenheit(JONAS, AbwesenheitsArt.KRANKHEIT, MO.plusDays(1), FR),
                    "Der Zeitraum überschneidet sich mit einem anderen Antrag von Ihnen.");

            gespeichert(mitStatus(urlaub(MEHMET, Urlaubsart.ERHOLUNGSURLAUB, MO, MO), Antragsstatus.GENEHMIGT));
            fehler(urlaub(MEHMET, Urlaubsart.ERHOLUNGSURLAUB, MO, FR),
                    "Der Zeitraum überschneidet sich mit einem anderen Antrag von Ihnen.");
        }

        @Test
        void keineUeberschneidungMitAbgelehntenZurueckgezogenenOderFremdenAntraegen() {
            gespeichert(mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR), Antragsstatus.ABGELEHNT));
            gespeichert(mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR), Antragsstatus.ZURUECKGEZOGEN));
            gespeichert(urlaub(MEHMET, Urlaubsart.ERHOLUNGSURLAUB, MO, FR));
            gespeichert(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, FR.plusDays(3), FR.plusDays(3)));

            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR)))
                    .doesNotThrowAnyException();
        }

        @Test
        void stornierterAntragZaehltNichtAlsUeberschneidung() {
            Abwesenheitsantrag alt = gespeichert(mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR),
                    Antragsstatus.GENEHMIGT));
            gespeichert(mitStatus(storno(JONAS, alt.getId(), "faellt aus"), Antragsstatus.GENEHMIGT));

            assertThat(regeln.istAbgeloest(alt)).isTrue();
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR)))
                    .doesNotThrowAnyException();
        }

        @Test
        void ueberschneidungMitAbwesenheitInZeiterfassung() {
            zeiterfassung.abwesenheitHinzufuegen(500, JONAS, AbwesenheitsArt.KRANKHEIT, FR, FR);
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR), "Im Zeitraum ist bereits eine Abwesenheit eingetragen.");
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR.minusDays(1))))
                    .doesNotThrowAnyException();
        }

        @Test
        void urlaubOhneArbeitstagWirdAbgewiesen() {
            LocalDate samstag = LocalDate.of(2026, 12, 12);
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, samstag, samstag.plusDays(1)),
                    "Im gewählten Zeitraum liegt kein Arbeitstag.");
            assertThatCode(() -> regeln.pruefe(abwesenheit(JONAS, AbwesenheitsArt.KRANKHEIT, samstag, samstag)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Resturlaub {

        @BeforeEach
        void wenigAnspruch() {
            mitarbeiter.setzeUrlaubsdaten(JONAS, new UrlaubsDaten(2026, 6, 2));
            gespeichert(mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 3)), Antragsstatus.GENEHMIGT));
            gespeichert(urlaub(JONAS, Urlaubsart.SONDERURLAUB, LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 8)));
            gespeichert(urlaub(JONAS, Urlaubsart.UNBEZAHLTER_URLAUB, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10)));
        }

        @Test
        void resturlaubZiehtErholungsUndSonderurlaubAbAberNichtUnbezahlten() {
            assertThat(regeln.resturlaub(JONAS, 2026, List.of())).isEqualTo(8 - 3 - 1);
        }

        @Test
        void genauDerResturlaubIstErlaubt() {
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, MO.plusDays(3))))
                    .doesNotThrowAnyException();
        }

        @Test
        void mehrAlsDerResturlaubWirdAbgewiesen() {
            fehler(urlaub(JONAS, Urlaubsart.SONDERURLAUB, MO, FR),
                    "Der Urlaub übersteigt Ihren Resturlaub für 2026 (noch 4 Tage).");
        }

        @Test
        void unbezahlterUrlaubZaehltNichtGegenDenAnspruch() {
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.UNBEZAHLTER_URLAUB, MO, FR)))
                    .doesNotThrowAnyException();
        }

        @Test
        void ueberDenJahreswechselZaehltJedesJahrFuerSich() {
            Abwesenheitsantrag jahreswechsel = urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB,
                    LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 8));
            mitarbeiter.setzeUrlaubsdaten(JONAS, new UrlaubsDaten(2027, 3, 0));
            fehler(jahreswechsel, "Der Urlaub übersteigt Ihren Resturlaub für 2027 (noch 3 Tage).");
            mitarbeiter.setzeUrlaubsdaten(JONAS, new UrlaubsDaten(2027, 30, 0));
            assertThatCode(() -> regeln.pruefe(jahreswechsel)).doesNotThrowAnyException();
            assertThat(regeln.urlaubstageImJahr(JONAS, jahreswechsel.getVon(), jahreswechsel.getBis(), 2026))
                    .isEqualTo(4);
        }

        @Test
        void ohneHinterlegtenAnspruchKeinUrlaub() {
            assertThatThrownBy(() -> regeln.resturlaub(99, 2026, List.of()))
                    .isInstanceOf(FachlicheException.class)
                    .hasMessage("Für 2026 ist kein Urlaubsanspruch hinterlegt.");
        }
    }

    @Nested
    class Zeitkorrektur {

        private BuchungInfo buchung;

        @BeforeEach
        void buchungAnlegen() {
            buchung = buchung(48213, JONAS, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(14, 30),
                    FakeProjektAuskunft.KUECHE);
            zeiterfassung.buchungHinzufuegen(buchung);
        }

        private Zeitkorrekturantrag korrektur() {
            return zeitkorrektur(buchung, zeit(DI_VORWOCHE, 16, 15));
        }

        @Test
        void gueltigeKorrekturBesteht() {
            assertThatCode(() -> regeln.pruefe(korrektur())).doesNotThrowAnyException();
        }

        @Test
        void buchungMussGewaehltSeinUndExistieren() {
            assertThatThrownBy(() -> regeln.korrigierbareBuchung(JONAS, null))
                    .hasMessage("Bitte wählen Sie die betroffene Buchung aus.");
            Zeitkorrekturantrag unbekannt = korrektur();
            unbekannt.setZeiteintragId(1);
            assertThatThrownBy(() -> regeln.pruefe(unbekannt)).isInstanceOf(NichtGefundenException.class)
                    .hasMessage("Die Buchung wurde nicht gefunden.");
        }

        @Test
        void korrekturAufOhneProjekt() {
            Zeitkorrekturantrag ohneProjekt = zeitkorrektur(buchung, buchung.ende());
            ohneProjekt.setNeuesProjektId(null);
            assertThatCode(() -> regeln.pruefe(ohneProjekt)).doesNotThrowAnyException();
        }

        @Test
        void nurEigeneBuchungen() {
            Zeitkorrekturantrag fremd = korrektur();
            fremd.setAntragstellerId(MEHMET);
            assertThatThrownBy(() -> regeln.pruefe(fremd)).isInstanceOf(ZugriffVerweigertException.class)
                    .hasMessage("Sie können nur eigene Buchungen korrigieren lassen.");
        }

        @Test
        void keineLaufendeBuchung() {
            zeiterfassung.buchungHinzufuegen(new BuchungInfo(7, JONAS, null, Standort.BUERO,
                    zeit(LocalDate.of(2026, 11, 27), 7, 0), null, 0, 0, false));
            assertThatThrownBy(() -> regeln.korrigierbareBuchung(JONAS, 7L))
                    .hasMessage("Laufende Buchungen können nicht korrigiert werden.");
        }

        @Test
        void neueWertePflichtUndEndeNachBeginn() {
            Zeitkorrekturantrag ohneStandort = korrektur();
            ohneStandort.setNeuerStandort(null);
            fehler(ohneStandort, "Bitte geben Sie Beginn, Ende und Standort an.");

            Zeitkorrekturantrag gleichzeitig = korrektur();
            gleichzeitig.setNeuesEnde(gleichzeitig.getNeuerBeginn());
            fehler(gleichzeitig, "Das Ende muss nach dem Beginn liegen.");
        }

        @Test
        void nichtInDerZukunft() {
            Zeitkorrekturantrag zukunft = korrektur();
            zukunft.setNeuesEnde(FESTE_UHR.instant().plusSeconds(60));
            fehler(zukunft, "Die gewünschten Zeiten dürfen nicht in der Zukunft liegen.");
        }

        @Test
        void mindestensEinWertWeichtAb() {
            fehler(zeitkorrektur(buchung, buchung.ende()), "Mindestens ein Wert muss sich von der Buchung unterscheiden.");

            Zeitkorrekturantrag nurStandort = zeitkorrektur(buchung, buchung.ende());
            nurStandort.setNeuerStandort(Standort.HOMEOFFICE);
            assertThatCode(() -> regeln.pruefe(nurStandort)).doesNotThrowAnyException();
        }

        @Test
        void inzwischenGeaenderteBuchung() {
            Zeitkorrekturantrag antrag = korrektur();
            antrag.setAltesEnde(zeit(DI_VORWOCHE, 14, 0));
            fehler(antrag, "Die Buchung wurde inzwischen geändert. Bitte ziehen Sie den Antrag zurück und stellen Sie ihn neu.");
        }

        @Test
        void zielprojektMussAktivUndZugeordnetSein() {
            Zeitkorrekturantrag treppe = korrektur();
            treppe.setNeuesProjektId(FakeProjektAuskunft.TREPPE);
            assertThatCode(() -> regeln.pruefe(treppe)).doesNotThrowAnyException();

            Zeitkorrekturantrag archiviert = korrektur();
            archiviert.setNeuesProjektId(FakeProjektAuskunft.ARCHIVIERT);
            fehler(archiviert, "Das gewünschte Projekt ist nicht aktiv.");

            Zeitkorrekturantrag unbekannt = korrektur();
            unbekannt.setNeuesProjektId(4711L);
            fehler(unbekannt, "Das gewünschte Projekt ist nicht aktiv.");
        }

        @Test
        void nichtZugeordnetesProjekt() {
            BuchungInfo saras = buchung(9, SARA, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(12, 0), null);
            zeiterfassung.buchungHinzufuegen(saras);
            Zeitkorrekturantrag antrag = zeitkorrektur(saras, saras.ende());
            antrag.setNeuesProjektId(FakeProjektAuskunft.TREPPE);
            fehler(antrag, "Sie sind dem gewünschten Projekt nicht zugeordnet.");
        }

        @Test
        void ueberschneidungMitAndererBuchung() {
            zeiterfassung.buchungHinzufuegen(buchung(2, JONAS, DI_VORWOCHE, LocalTime.of(16, 0), LocalTime.of(18, 0), null));
            fehler(korrektur(), "Die gewünschte Zeit überschneidet sich mit einer anderen Buchung.");
        }

        @Test
        void anschliessendeBuchungIstKeineUeberschneidung() {
            zeiterfassung.buchungHinzufuegen(buchung(2, JONAS, DI_VORWOCHE, LocalTime.of(16, 15), LocalTime.of(18, 0), null));
            assertThatCode(() -> regeln.pruefe(korrektur())).doesNotThrowAnyException();
        }

        @Test
        void nurEinOffenerAntragJeBuchung() {
            Zeitkorrekturantrag erster = gespeichert(korrektur());
            fehler(korrektur(), "Für diese Buchung gibt es bereits einen offenen Antrag.");
            assertThatCode(() -> regeln.pruefe(erster)).doesNotThrowAnyException();
        }

        @Test
        void gueltigeAenderungEinerGenehmigtenKorrektur() {
            Zeitkorrekturantrag genehmigt = gespeichert(mitStatus(zeitkorrektur(buchung, zeit(DI_VORWOCHE, 15, 0)),
                    Antragsstatus.GENEHMIGT));
            Zeitkorrekturantrag aenderung = korrektur();
            aenderung.setBezugsantragId(genehmigt.getId());
            assertThatCode(() -> regeln.pruefe(aenderung)).doesNotThrowAnyException();
        }

        @Test
        void aenderungMussDieselbeBuchungBetreffen() {
            Zeitkorrekturantrag genehmigt = gespeichert(mitStatus(korrektur(), Antragsstatus.GENEHMIGT));
            BuchungInfo andere = buchung(3, JONAS, DI_VORWOCHE.minusDays(1), LocalTime.of(7, 0), LocalTime.of(12, 0), null);
            zeiterfassung.buchungHinzufuegen(andere);
            Zeitkorrekturantrag aenderung = zeitkorrektur(andere, zeit(DI_VORWOCHE.minusDays(1), 13, 0));
            aenderung.setBezugsantragId(genehmigt.getId());
            fehler(aenderung, "Die Änderung muss dieselbe Buchung betreffen.");
        }
    }

    @Nested
    class AenderungUndStorno {

        private Abwesenheitsantrag genehmigt;

        @BeforeEach
        void genehmigterUrlaub() {
            genehmigt = gespeichert(mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR), Antragsstatus.GENEHMIGT));
            zeiterfassung.abwesenheitHinzufuegen(genehmigt.getId(), JONAS, AbwesenheitsArt.URLAUB, MO, FR);
        }

        private Abwesenheitsantrag aenderung(LocalDate von, LocalDate bis) {
            Abwesenheitsantrag antrag = urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, von, bis);
            antrag.setBezugsantragId(genehmigt.getId());
            return antrag;
        }

        @Test
        void aenderungDarfDenZeitraumDesBezugsUeberlappen() {
            assertThatCode(() -> regeln.pruefe(aenderung(MO.plusDays(2), FR.plusDays(3)))).doesNotThrowAnyException();
        }

        @Test
        void aenderungPrueftAbwesenheitenAusserhalbDesBezugs() {
            zeiterfassung.abwesenheitHinzufuegen(600, JONAS, AbwesenheitsArt.KRANKHEIT, FR.plusDays(3), FR.plusDays(3));
            fehler(aenderung(MO, FR.plusDays(3)), "Im Zeitraum ist bereits eine Abwesenheit eingetragen.");
            zeiterfassung.abwesenheitHinzufuegen(601, JONAS, AbwesenheitsArt.KRANKHEIT, MO.minusDays(3), MO.minusDays(3));
            fehler(aenderung(MO.minusDays(3), FR), "Im Zeitraum ist bereits eine Abwesenheit eingetragen.");
        }

        @Test
        void aenderungMitAndererArtOderAnderemTyp() {
            Abwesenheitsantrag krank = abwesenheit(JONAS, AbwesenheitsArt.KRANKHEIT, MO, MO);
            krank.setBezugsantragId(genehmigt.getId());
            fehler(krank, "Eine Änderung muss dieselbe Art der Abwesenheit wie der ursprüngliche Antrag haben.");

            BuchungInfo buchung = buchung(4, JONAS, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(12, 0), null);
            zeiterfassung.buchungHinzufuegen(buchung);
            Zeitkorrekturantrag korrektur = zeitkorrektur(buchung, zeit(DI_VORWOCHE, 13, 0));
            korrektur.setBezugsantragId(genehmigt.getId());
            fehler(korrektur, "Eine Änderung muss denselben Antragstyp wie der ursprüngliche Antrag haben.");
        }

        @Test
        void beiKnappemAnspruchZaehltDerBezugEinerAenderungNichtMit() {
            mitarbeiter.setzeUrlaubsdaten(JONAS, new UrlaubsDaten(2026, 5, 0));
            assertThat(regeln.resturlaub(JONAS, 2026, List.of())).isZero();
            assertThatCode(() -> regeln.pruefe(aenderung(MO.plusDays(1), FR.plusDays(3)))).doesNotThrowAnyException();
            fehler(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, FR.plusDays(3), FR.plusDays(3)),
                    "Der Urlaub übersteigt Ihren Resturlaub für 2026 (noch 0 Tage).");
        }

        @Test
        void nachGenehmigtemStornoIstDerUrlaubWiederVerfuegbar() {
            mitarbeiter.setzeUrlaubsdaten(JONAS, new UrlaubsDaten(2026, 5, 0));
            gespeichert(mitStatus(storno(JONAS, genehmigt.getId(), "faellt aus"), Antragsstatus.GENEHMIGT));
            zeiterfassung.aufheben(zeiterfassung.abwesenheitZuAntrag(genehmigt.getId()).orElseThrow());

            assertThat(regeln.resturlaub(JONAS, 2026, List.of())).isEqualTo(5);
            assertThatCode(() -> regeln.pruefe(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR)))
                    .doesNotThrowAnyException();
        }

        @Test
        void gueltigesStorno() {
            assertThatCode(() -> regeln.pruefe(storno(JONAS, genehmigt.getId(), "Reise fällt aus.")))
                    .doesNotThrowAnyException();
        }

        @Test
        void stornoBrauchtBezugUndGrund() {
            fehler(storno(JONAS, null, "Grund"), "Bitte wählen Sie den Antrag aus, den Sie stornieren möchten.");
            fehler(storno(JONAS, genehmigt.getId(), "  "), "Bitte geben Sie einen Grund für die Stornierung an.");
            assertThatThrownBy(() -> regeln.pruefe(storno(JONAS, 4711L, "Grund")))
                    .isInstanceOf(NichtGefundenException.class)
                    .hasMessage("Der ursprüngliche Antrag wurde nicht gefunden.");
        }

        @Test
        void nurEigeneGenehmigteAbwesenheitenStornieren() {
            Abwesenheitsantrag offen = gespeichert(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, FR.plusDays(10), FR.plusDays(10)));
            fehler(storno(JONAS, offen.getId(), "Grund"), "Ändern oder stornieren können Sie nur genehmigte Anträge.");

            assertThatThrownBy(() -> regeln.pruefe(storno(MEHMET, genehmigt.getId(), "Grund")))
                    .isInstanceOf(ZugriffVerweigertException.class)
                    .hasMessage("Sie können nur eigene Anträge ändern oder stornieren.");

            BuchungInfo buchung = buchung(5, JONAS, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(12, 0), null);
            zeiterfassung.buchungHinzufuegen(buchung);
            Zeitkorrekturantrag korrektur = gespeichert(mitStatus(zeitkorrektur(buchung, zeit(DI_VORWOCHE, 13, 0)),
                    Antragsstatus.GENEHMIGT));
            fehler(storno(JONAS, korrektur.getId(), "Grund"), "Stornieren können Sie nur genehmigte Abwesenheiten.");
        }

        @Test
        void keinZweiterFolgeantrag() {
            Antrag offenesStorno = gespeichert(storno(JONAS, genehmigt.getId(), "erst"));
            fehler(storno(JONAS, genehmigt.getId(), "zweit"),
                    "Zu diesem Antrag gibt es bereits einen offenen Änderungs- oder Stornoantrag.");
            assertThatCode(() -> regeln.pruefe(offenesStorno)).doesNotThrowAnyException();

            offenesStorno.setStatus(Antragsstatus.GENEHMIGT);
            fehler(aenderung(MO, FR.minusDays(1)), "Dieser Antrag wurde bereits geändert oder storniert.");
        }
    }
}
