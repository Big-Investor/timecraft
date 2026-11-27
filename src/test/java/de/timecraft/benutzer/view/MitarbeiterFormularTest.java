package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.router.Route;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.fake.TestMailService;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.benutzer.service.MitarbeiterService;
import de.timecraft.benutzer.service.TestDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft das Mitarbeiterformular (UC-M1-04, -05, -06, -09, -10) mit echtem Service und Testdatenbank.
 * Mails gehen an den {@link TestMailService}. Die feste Uhr steht auf dem 14.11.2026.
 * Ein Test prüft den Normalfall des Admins, der zweite Fehlerfälle und die Rollen (QA-A-01, QA-M1-07).
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MitarbeiterFormularTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private ArbeitszeitmodellRepository arbeitszeitmodellRepository;

    @Autowired
    private ZeitkontoRepository zeitkontoRepository;

    @Autowired
    private UrlaubskontoRepository urlaubskontoRepository;

    private final TestMailService mailService = new TestMailService();
    private MitarbeiterService mitarbeiterService;
    private Mitarbeiter paul;
    private Mitarbeiter lena;
    private Mitarbeiter jonas;

    @BeforeEach
    void vorbereiten() {
        mitarbeiterService = TestDaten.mitarbeiterService(benutzerRepository, mitarbeiterRepository,
                arbeitszeitmodellRepository, zeitkontoRepository, urlaubskontoRepository, mailService);
        paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "admin", Rolle.ADMIN, "1001");
        lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "projektleitung",
                Rolle.PROJEKTLEITUNG, "1004");
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "mitarbeiter",
                Rolle.MITARBEITER, "1012");
        jonas.setVorgesetzter(lena);
        mitarbeiterRepository.save(jonas);
        ViewTestHilfe.fensterOeffnen();
    }

    @AfterEach
    void aufraeumen() {
        ViewTestHilfe.aufraeumen();
    }

    @Test
    void adminLegtAnBearbeitetSperrtUndDeaktiviert() {
        assertThat(MitarbeiterFormular.class.getAnnotation(Route.class).value()).isEqualTo(MitarbeiterListeView.FORMULAR);
        assertThat(MitarbeiterFormular.class.getAnnotation(Route.class).layout()).isEqualTo(HauptLayout.class);

        // Neues Formular wie im Mockup „Mitarbeiter anlegen“
        ViewTestHilfe.anmelden(paul);
        MitarbeiterFormular formular = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        formular.formularOeffnen(null);
        assertThat(formular.pfad.getText()).isEqualTo(" / Neu");
        assertThat(formular.ueberschrift.getText()).isEqualTo("Neuer Mitarbeiter");
        assertThat(formular.speichern.getText()).isEqualTo("Speichern & Einmalpasswort senden");
        assertThat(formular.personalnummer.getLabel()).isEqualTo("Personalnummer *");
        assertThat(formular.urlaubsanspruch.getLabel()).isEqualTo("Urlaubsanspruch (Tage/Jahr) *");
        assertThat(formular.kontoAktionen.isVisible()).isFalse();
        assertThat(formular.startsaldo.getValue()).isEqualTo("0:00");
        assertThat(formular.rolle.getValue()).isEqualTo(Rolle.MITARBEITER);
        assertThat(formular.vorgesetzter.getListDataView().getItems().toList()).containsExactly(lena.getMitarbeiterId());
        assertThat(formular.vorgesetzter.getItemLabelGenerator().apply(lena.getMitarbeiterId()))
                .isEqualTo(lena.getName() + " (Projektleitung)");

        formular.personalnummer.setValue("1024");
        formular.vorname.setValue("Lukas");
        formular.nachname.setValue("Meyer");
        formular.email.setValue("l.meyer@tischlerei-hartmann.example");
        formular.telefon.setValue("0521 123 45 67");
        formular.geburtsdatum.setValue(LocalDate.of(2009, 3, 14));
        formular.eintrittsdatum.setValue(LocalDate.of(2026, 8, 1));
        formular.qualifikation.setValue(Qualifikation.AZUBI);
        formular.wochenstunden.setValue(new BigDecimal("40.0"));
        formular.arbeitstage.setValue(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        formular.startsaldo.setValue("-1:30");
        formular.urlaubsanspruch.setValue(30);
        formular.vorgesetzter.setValue(lena.getMitarbeiterId());
        formular.benutzername.setValue("l.meyer");
        assertThat(formular.jugendHinweis.isVisible()).isTrue();
        assertThat(formular.jugendHinweis.getText()).isEqualTo("Unter 18: Es gelten die Regeln des JArbSchG.");
        assertThat(formular.tagesSoll.getText()).isEqualTo("Tages-Soll: 8:00 h (40,0 h ÷ 5 Arbeitstage)");
        assertThat(formular.kontoHinweis.getText()).isEqualTo("Nach dem Speichern erhält Lukas ein Einmalpasswort "
                + "an l.meyer@tischlerei-hartmann.example (24 Stunden gültig).");

        formular.speichern();
        Benutzer konto = benutzerRepository.findByBenutzername("l.meyer").orElseThrow();
        assertThat(konto.isPasswortAenderungNoetig()).isTrue();
        assertThat(mailService.gesendeteMails()).hasSize(1);
        assertThat(mailService.gesendeteMails().get(0).empfaenger()).isEqualTo("l.meyer@tischlerei-hartmann.example");
        Mitarbeiter lukas = mitarbeiterRepository.findByBenutzer(konto).orElseThrow();
        assertThat(zeitkontoRepository.findByMitarbeiter(lukas).orElseThrow().getAnfangssaldoMin()).isEqualTo(-90);

        // Bearbeiten: gespeicherte Werte im Formular, Konto-Bereich mit allen Knöpfen
        MitarbeiterFormular bearbeiten = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        bearbeiten.formularOeffnen(lukas.getMitarbeiterId());
        assertThat(bearbeiten.ueberschrift.getText()).isEqualTo("Lukas Meyer");
        assertThat(bearbeiten.speichern.getText()).isEqualTo("Speichern");
        assertThat(bearbeiten.wochenstunden.getValue()).isEqualTo(new BigDecimal("40.0"));
        assertThat(bearbeiten.startsaldo.getValue()).isEqualTo("-1:30");
        assertThat(bearbeiten.vorgesetzter.getValue()).isEqualTo(lena.getMitarbeiterId());
        assertThat(bearbeiten.kontoAktionen.isVisible()).isTrue();
        assertThat(bearbeiten.kontoHinweis.getText()).isEqualTo("Konto aktiv.");
        assertThat(bearbeiten.sperren.getText()).isEqualTo("Konto sperren");
        bearbeiten.nachname.setValue("Meier");
        bearbeiten.arbeitstage.setValue(Set.of(DayOfWeek.MONDAY));
        bearbeiten.wochenstunden.setValue(new BigDecimal("8"));
        assertThat(bearbeiten.tagesSoll.getText()).isEqualTo("Tages-Soll: 8:00 h (8,0 h ÷ 1 Arbeitstag)");
        bearbeiten.speichern();
        assertThat(mitarbeiterRepository.findById(lukas.getMitarbeiterId()).orElseThrow().getNachname())
                .isEqualTo("Meier");

        // Konto sperren und entsperren (UC-M1-09), Einmalpasswort senden (UC-M1-10), deaktivieren (UC-M1-06)
        bearbeiten.sperrenOderEntsperren();
        assertThat(benutzerRepository.findByBenutzername("l.meyer").orElseThrow().isGesperrt()).isTrue();
        assertThat(bearbeiten.sperren.getText()).isEqualTo("Konto entsperren");
        bearbeiten.sperrenOderEntsperren();
        assertThat(benutzerRepository.findByBenutzername("l.meyer").orElseThrow().isGesperrt()).isFalse();
        bearbeiten.einmalpasswortSenden();
        assertThat(mailService.gesendeteMails()).hasSize(2);
        DatePicker austritt = Eingabefelder.datumFeld("Austrittsdatum *");
        austritt.setValue(LocalDate.of(2026, 12, 31));
        assertThat(bearbeiten.deaktivieren(austritt)).isTrue();
        assertThat(mitarbeiterRepository.findById(lukas.getMitarbeiterId()).orElseThrow().isAktiv()).isFalse();

        // Umrechnung Startsaldo Stunden:Minuten ↔ Minuten
        assertThat(MitarbeiterFormular.stundenText(0)).isEqualTo("0:00");
        assertThat(MitarbeiterFormular.stundenText(605)).isEqualTo("10:05");
        assertThat(MitarbeiterFormular.minutenAus(" -4:00 ")).isEqualTo(-240);
        assertThat(MitarbeiterFormular.minutenAus("12:30")).isEqualTo(750);
        assertThat(MitarbeiterFormular.minutenAus("12:60")).isNull();
        assertThat(MitarbeiterFormular.minutenAus("12")).isNull();
    }

    @Test
    void fehlerAmFeldUndRechteJeRolle() {
        assertThat(MitarbeiterFormular.class.getAnnotation(RolesAllowed.class).value())
                .containsExactlyInAnyOrder("PROJEKTLEITUNG", "ADMIN");
        long anzahlKonten = benutzerRepository.count();

        // Leeres Formular: Meldungen am Feld, nichts gespeichert (UC-M1-04, 5a)
        ViewTestHilfe.anmelden(paul);
        MitarbeiterFormular formular = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        formular.formularOeffnen(null);
        formular.speichern();
        assertThat(formular.personalnummer.isInvalid()).isTrue();
        assertThat(formular.personalnummer.getErrorMessage()).isEqualTo("Bitte geben Sie die Personalnummer ein.");
        assertThat(formular.arbeitstage.isInvalid()).isTrue();
        assertThat(formular.arbeitstage.getErrorMessage()).isEqualTo("Bitte wählen Sie mindestens einen Arbeitstag.");
        assertThat(formular.vorgesetzter.isInvalid()).isTrue();
        assertThat(formular.telefon.isInvalid()).isFalse();
        assertThat(benutzerRepository.count()).isEqualTo(anzahlKonten);

        // Falsche Form: Startsaldo und Datum werden schon im Formular abgewiesen
        formular.startsaldo.setValue("zwei Stunden");
        formular.geburtsdatum.setValue(LocalDate.of(1850, 1, 1));
        formular.speichern();
        assertThat(formular.startsaldo.getErrorMessage()).isEqualTo(MitarbeiterFormular.FALSCHER_STARTSALDO);
        assertThat(formular.geburtsdatum.getErrorMessage()).isEqualTo(MitarbeiterFormular.FALSCHES_DATUM);
        assertThat(formular.personalnummer.isInvalid()).isFalse();

        // Personalnummer schon vergeben (5b); Versandfehler (7a): trotzdem gespeichert
        formular.startsaldo.setValue("");
        formular.geburtsdatum.setValue(LocalDate.of(1990, 5, 1));
        formular.personalnummer.setValue("1012");
        formular.vorname.setValue("Lukas");
        formular.nachname.setValue("Meyer");
        formular.email.setValue("l.meyer@tischlerei-hartmann.example");
        formular.eintrittsdatum.setValue(LocalDate.of(2026, 8, 1));
        formular.qualifikation.setValue(Qualifikation.GESELLE);
        formular.wochenstunden.setValue(new BigDecimal("40"));
        formular.arbeitstage.setValue(Set.of(DayOfWeek.MONDAY));
        formular.urlaubsanspruch.setValue(30);
        formular.vorgesetzter.setValue(lena.getMitarbeiterId());
        formular.benutzername.setValue("l.meyer");
        assertThat(formular.jugendHinweis.isVisible()).isFalse();
        formular.speichern();
        assertThat(formular.personalnummer.getErrorMessage()).isEqualTo("Die Personalnummer 1012 ist bereits vergeben.");
        assertThat(formular.startsaldo.isInvalid()).isFalse();
        assertThat(benutzerRepository.count()).isEqualTo(anzahlKonten);
        mailService.versandSchlaegtFehl();
        formular.personalnummer.setValue("1024");
        formular.speichern();
        assertThat(benutzerRepository.findByBenutzername("l.meyer")).isPresent();
        assertThat(mailService.gesendeteMails()).isEmpty();

        // Versandfehler beim Zurücksetzen: altes Passwort bleibt (QA-M1-11); Deaktivieren ohne Datum: Meldung am Feld
        MitarbeiterFormular jonasFormular = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        jonasFormular.formularOeffnen(jonas.getMitarbeiterId());
        String alterHash = benutzerRepository.findByBenutzername("mitarbeiter").orElseThrow().getPasswortHash();
        jonasFormular.einmalpasswortSenden();
        assertThat(benutzerRepository.findByBenutzername("mitarbeiter").orElseThrow().getPasswortHash()).isEqualTo(alterHash);
        DatePicker ohneDatum = Eingabefelder.datumFeld("Austrittsdatum *");
        assertThat(jonasFormular.deaktivieren(ohneDatum)).isFalse();
        assertThat(ohneDatum.getErrorMessage()).isEqualTo("Bitte geben Sie das Austrittsdatum an.");
        assertThat(mitarbeiterRepository.findById(jonas.getMitarbeiterId()).orElseThrow().isAktiv()).isTrue();
        // Der Admin sperrt sich nicht selbst aus
        MitarbeiterFormular eigenesFormular = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        eigenesFormular.formularOeffnen(paul.getMitarbeiterId());
        eigenesFormular.sperrenOderEntsperren();
        assertThat(benutzerRepository.findByBenutzername("admin").orElseThrow().isGesperrt()).isFalse();
        assertThat(eigenesFormular.sperren.getText()).isEqualTo("Konto sperren");

        // Projektleitung: Urlaub, Startsaldo, Vorgesetzte/r und Konto nur lesen, keine Konto-Knöpfe (FA-M1-11)
        ViewTestHilfe.anmelden(lena);
        MitarbeiterFormular vonLeitung = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        vonLeitung.formularOeffnen(jonas.getMitarbeiterId());
        assertThat(vonLeitung.urlaubsanspruch.isReadOnly()).isTrue();
        assertThat(vonLeitung.urlaubsuebertrag.isReadOnly()).isTrue();
        assertThat(vonLeitung.startsaldo.isReadOnly()).isTrue();
        assertThat(vonLeitung.vorgesetzter.isReadOnly()).isTrue();
        assertThat(vonLeitung.benutzername.isReadOnly()).isTrue();
        assertThat(vonLeitung.rolle.isReadOnly()).isTrue();
        assertThat(vonLeitung.nachname.isReadOnly()).isFalse();
        assertThat(vonLeitung.wochenstunden.isReadOnly()).isFalse();
        assertThat(vonLeitung.kontoAktionen.isVisible()).isFalse();
        vonLeitung.telefon.setValue("0521 999");
        vonLeitung.wochenstunden.setValue(new BigDecimal("30"));
        vonLeitung.arbeitstage.setValue(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
        vonLeitung.speichern();
        Mitarbeiter jonasNeu = mitarbeiterRepository.findById(jonas.getMitarbeiterId()).orElseThrow();
        assertThat(jonasNeu.getTelefon()).isEqualTo("0521 999");
        assertThat(arbeitszeitmodellRepository.findByMitarbeiter(jonasNeu).orElseThrow().getArbeitstage()).hasSize(3);
        assertThat(vonLeitung.speichern.isVisible()).isTrue();
        // Den Admin sieht die Projektleitung nur an: alles schreibgeschützt, kein „Speichern“
        MitarbeiterFormular adminVonLeitung = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        adminVonLeitung.formularOeffnen(paul.getMitarbeiterId());
        assertThat(adminVonLeitung.email.isReadOnly()).isTrue();
        assertThat(adminVonLeitung.nachname.isReadOnly()).isTrue();
        assertThat(adminVonLeitung.wochenstunden.isReadOnly()).isTrue();
        assertThat(adminVonLeitung.arbeitstage.isReadOnly()).isTrue();
        assertThat(adminVonLeitung.speichern.isVisible()).isFalse();
        // Direkter Aufruf von „Neu“ durch die Projektleitung: der Service verweigert, nichts gespeichert (1a)
        long kontenVorher = benutzerRepository.count();
        MitarbeiterFormular neuVonLeitung = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        neuVonLeitung.formularOeffnen(null);
        neuVonLeitung.personalnummer.setValue("1030");
        neuVonLeitung.benutzername.setValue("n.krueger");
        neuVonLeitung.speichern();
        assertThat(benutzerRepository.count()).isEqualTo(kontenVorher);

        // Mitarbeiter ohne Zugriff, unbekannte ID: Meldung statt Absturz, Formular bleibt leer
        ViewTestHilfe.anmelden(jonas);
        MitarbeiterFormular vonMitarbeiter = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        vonMitarbeiter.formularOeffnen(paul.getMitarbeiterId());
        assertThat(vonMitarbeiter.personalnummer.getValue()).isEmpty();
        ViewTestHilfe.anmelden(paul);
        MitarbeiterFormular unbekannt = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        assertThat(unbekannt.formularOeffnen(999_999L)).isFalse();
        assertThat(unbekannt.ueberschrift.getText()).isEmpty();

        // Dieselbe Seite erst zum Bearbeiten, dann für „Neu“ geöffnet: keine alten Werte, keine alten Meldungen
        MitarbeiterFormular wiederverwendet = new MitarbeiterFormular(mitarbeiterService, new AktuelleSitzung());
        assertThat(wiederverwendet.formularOeffnen(jonas.getMitarbeiterId())).isTrue();
        Eingabefelder.fehlerZeigen(wiederverwendet.nachname, "alte Meldung");
        assertThat(wiederverwendet.formularOeffnen(null)).isTrue();
        assertThat(wiederverwendet.personalnummer.getValue()).isEmpty();
        assertThat(wiederverwendet.benutzername.getValue()).isEmpty();
        assertThat(wiederverwendet.geburtsdatum.getValue()).isNull();
        assertThat(wiederverwendet.vorgesetzter.getValue()).isNull();
        assertThat(wiederverwendet.nachname.isInvalid()).isFalse();
        assertThat(wiederverwendet.kontoAktionen.isVisible()).isFalse();
        assertThat(wiederverwendet.startsaldo.getValue()).isEqualTo("0:00");
    }
}
