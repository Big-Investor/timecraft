package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.fake.TestMailService;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft den {@link MitarbeiterService} mit echter Testdatenbank (H2): Anlegen (UC-M1-04),
 * Laden und Bearbeiten (UC-M1-05), Deaktivieren (UC-M1-06) und die Liste (UC-M1-11).
 * Ein Test prüft den Normalfall, der zweite alle Fehlerfälle und Rollen (QA-A-01).
 * Die feste Uhr steht auf dem 14.11.2026, 10:00 Uhr.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MitarbeiterServiceTest {

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

    private final PasswortService passwortService = TestDaten.passwortService();
    private final TestMailService mailService = new TestMailService();

    private MitarbeiterService mitarbeiterService;
    private Mitarbeiter paul;
    private Mitarbeiter lena;
    private Mitarbeiter jonas;

    @BeforeEach
    void vorbereiten() {
        MitarbeiterPruefung pruefung = new MitarbeiterPruefung(benutzerRepository, mitarbeiterRepository);
        EinmalpasswortVersand versand = new EinmalpasswortVersand(passwortService, mailService, benutzerRepository);
        mitarbeiterService = new MitarbeiterService(mitarbeiterRepository, benutzerRepository,
                arbeitszeitmodellRepository, zeitkontoRepository, urlaubskontoRepository, pruefung, versand,
                TestDaten.FESTE_UHR);

        paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "p.hartmann", Rolle.ADMIN, "1001");
        lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "l.schroeder",
                Rolle.PROJEKTLEITUNG, "1004");
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "j.becker",
                Rolle.MITARBEITER, "1012");
        jonas.setVorgesetzter(lena);
        mitarbeiterRepository.save(jonas);
    }

    @Test
    void anlegenBearbeitenDeaktivierenUndListeImNormalfall() {
        // Anlegen (UC-M1-04): alles gespeichert, Einmalpasswort an die hinterlegte Adresse, 24 Stunden gültig
        MitarbeiterDaten neu = TestDaten.formular("1024", "l.meyer", Rolle.MITARBEITER, lena.getMitarbeiterId());
        neu.setVorname("  Lukas ");
        neu.setStartsaldoMinuten(-90);
        neu.setUrlaubsuebertrag(null);
        AnlegeErgebnis ergebnis = mitarbeiterService.anlegen(neu, Rolle.ADMIN);

        assertThat(ergebnis.mailVersendet()).isTrue();
        assertThat(ergebnis.meldung()).isEqualTo("Mitarbeiter gespeichert. E-Mail wurde zugestellt.");
        Mitarbeiter lukas = mitarbeiterRepository.findById(ergebnis.mitarbeiterId()).orElseThrow();
        assertThat(lukas.getName()).isEqualTo("Lukas Meyer");
        assertThat(lukas.isAktiv()).isTrue();
        assertThat(lukas.getVorgesetzter().getMitarbeiterId()).isEqualTo(lena.getMitarbeiterId());
        Benutzer konto = lukas.getBenutzer();
        assertThat(konto.getRolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(mailService.gesendeteMails().get(0).empfaenger()).isEqualTo("l.meyer@tischlerei-hartmann.example");
        assertThat(passwortService.passt(mailService.letztesEinmalpasswort(), konto.getEinmalpasswortHash())).isTrue();
        assertThat(konto.getEinmalpasswortGueltigBis()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
        assertThat(konto.isPasswortAenderungNoetig()).isTrue();
        Arbeitszeitmodell modell = arbeitszeitmodellRepository.findByMitarbeiter(lukas).orElseThrow();
        assertThat(modell.getArbeitstage()).hasSize(5);
        // Eintritt 01.08.2026 liegt vor heute: Das Zeitkonto beginnt mit der Einführung (heute), Startsaldo darf negativ sein.
        Zeitkonto zeitkonto = zeitkontoRepository.findByMitarbeiter(lukas).orElseThrow();
        assertThat(zeitkonto.getStartdatum()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(zeitkonto.getAnfangssaldoMin()).isEqualTo(-90);
        Urlaubskonto urlaub = urlaubskontoRepository.findByMitarbeiterAndJahr(lukas, (short) 2026).orElseThrow();
        assertThat(urlaub.getAnspruchTage()).isEqualTo(30);
        assertThat(urlaub.getUebertragTage()).isZero();

        // Laden und Bearbeiten durch den Admin (alle Daten)
        MitarbeiterDaten geladen = mitarbeiterService.laden(lukas.getMitarbeiterId(), Rolle.ADMIN);
        assertThat(geladen.getBenutzername()).isEqualTo("l.meyer");
        assertThat(geladen.getStartsaldoMinuten()).isEqualTo(-90);
        geladen.setUrlaubsanspruch(28);
        geladen.setRolle(Rolle.PROJEKTLEITUNG);
        geladen.setVorgesetzterId(null);
        geladen.setEintrittsdatum(LocalDate.of(2027, 3, 1));
        mitarbeiterService.bearbeiten(lukas.getMitarbeiterId(), geladen, paul.getMitarbeiterId(), Rolle.ADMIN);
        assertThat(benutzerRepository.findByBenutzername("l.meyer").orElseThrow().getRolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
        // Startwerte gelten ab dem Anlegen: Ein geänderter Eintritt verschiebt weder Zeitkonto noch Urlaubsjahr
        assertThat(urlaubskontoRepository.findByMitarbeiterAndJahr(lukas, (short) 2026).orElseThrow().getAnspruchTage())
                .isEqualTo(28);
        assertThat(urlaubskontoRepository.findByMitarbeiterAndJahr(lukas, (short) 2027)).isEmpty();
        assertThat(zeitkontoRepository.findByMitarbeiter(lukas).orElseThrow().getStartdatum())
                .isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(mitarbeiterService.laden(lukas.getMitarbeiterId(), Rolle.ADMIN).getUrlaubsanspruch()).isEqualTo(28);

        // Bearbeiten durch die Projektleitung: nur Stammdaten und Arbeitszeit werden übernommen (FA-M1-11)
        MitarbeiterDaten vonLeitung = mitarbeiterService.laden(jonas.getMitarbeiterId(), Rolle.PROJEKTLEITUNG);
        vonLeitung.setNachname("Becker");
        vonLeitung.setWochenstunden(new BigDecimal("30"));
        vonLeitung.setArbeitstage(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        vonLeitung.setRolle(Rolle.ADMIN);
        vonLeitung.setBenutzername("anders");
        vonLeitung.setVorgesetzterId(null);
        vonLeitung.setUrlaubsanspruch(99);
        vonLeitung.setStartsaldoMinuten(600);
        mitarbeiterService.bearbeiten(jonas.getMitarbeiterId(), vonLeitung, lena.getMitarbeiterId(),
                Rolle.PROJEKTLEITUNG);
        Mitarbeiter jonasNeu = mitarbeiterRepository.findById(jonas.getMitarbeiterId()).orElseThrow();
        assertThat(jonasNeu.getNachname()).isEqualTo("Becker");
        assertThat(arbeitszeitmodellRepository.findByMitarbeiter(jonasNeu).orElseThrow().getWochenstunden())
                .isEqualByComparingTo("30");
        assertThat(jonasNeu.getBenutzer().getRolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(jonasNeu.getBenutzer().getBenutzername()).isEqualTo("j.becker");
        assertThat(jonasNeu.getVorgesetzter().getMitarbeiterId()).isEqualTo(lena.getMitarbeiterId());
        assertThat(urlaubskontoRepository.findAll()).extracting(Urlaubskonto::getMitarbeiter)
                .extracting(Mitarbeiter::getMitarbeiterId).doesNotContain(jonas.getMitarbeiterId());
        assertThat(zeitkontoRepository.findByMitarbeiter(jonasNeu)).isEmpty();

        // Deaktivieren statt löschen (UC-M1-06)
        mitarbeiterService.deaktivieren(jonas.getMitarbeiterId(), LocalDate.of(2026, 12, 31),
                paul.getMitarbeiterId(), Rolle.ADMIN);
        Mitarbeiter ausgetreten = mitarbeiterRepository.findById(jonas.getMitarbeiterId()).orElseThrow();
        assertThat(ausgetreten.isAktiv()).isFalse();
        assertThat(ausgetreten.getAustrittsdatum()).isEqualTo(LocalDate.of(2026, 12, 31));

        // Liste (erst aktive, dann Ehemalige, jeweils nach Personalnummer) und Auswahl der Vorgesetzten (nur aktive Projektleitungen)
        Mitarbeiter ehemaligeLeitung = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "t.krause",
                Rolle.PROJEKTLEITUNG, "1006");
        mitarbeiterService.deaktivieren(ehemaligeLeitung.getMitarbeiterId(), LocalDate.of(2026, 12, 31),
                paul.getMitarbeiterId(), Rolle.ADMIN);
        assertThat(mitarbeiterService.liste(Rolle.PROJEKTLEITUNG))
                .extracting(MitarbeiterZeile::personalnummer).containsExactly("1001", "1004", "1024", "1006", "1012");
        assertThat(mitarbeiterService.vorgesetzteAuswahl(Rolle.ADMIN))
                .extracting(MitarbeiterZeile::personalnummer).containsExactly("1004", "1024");

        // Eintritt in der Zukunft: Zeitkonto beginnt am Eintritt, Urlaubskonto gehört zu diesem Jahr
        MitarbeiterDaten kuenftig = TestDaten.formular("1030", "n.krueger", Rolle.MITARBEITER, lena.getMitarbeiterId());
        kuenftig.setEintrittsdatum(LocalDate.of(2027, 1, 1));
        long kuenftigId = mitarbeiterService.anlegen(kuenftig, Rolle.ADMIN).mitarbeiterId();
        Mitarbeiter neuerKollege = mitarbeiterRepository.findById(kuenftigId).orElseThrow();
        assertThat(zeitkontoRepository.findByMitarbeiter(neuerKollege).orElseThrow().getStartdatum())
                .isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(urlaubskontoRepository.findByMitarbeiterAndJahr(neuerKollege, (short) 2027)).isPresent();
        assertThat(urlaubskontoRepository.findByMitarbeiterAndJahr(neuerKollege, (short) 2026)).isEmpty();

        // Anzeigehilfen fürs Formular: unter 18 (heute 14.11.2026) und Tages-Soll in Minuten
        assertThat(mitarbeiterService.istUnter18(LocalDate.of(2008, 11, 15))).isTrue();
        assertThat(mitarbeiterService.istUnter18(LocalDate.of(2008, 11, 14))).isFalse();
        assertThat(mitarbeiterService.istUnter18(null)).isFalse();
        assertThat(MitarbeiterService.tagesSollMinuten(new BigDecimal("40"), 5)).isEqualTo(480);
        assertThat(MitarbeiterService.tagesSollMinuten(new BigDecimal("38.5"), 5)).isEqualTo(462);
        assertThat(MitarbeiterService.tagesSollMinuten(new BigDecimal("20"), 3)).isEqualTo(400);
    }

    @Test
    void fehlerfaelleUndRechteJeRolle() {
        long jonasId = jonas.getMitarbeiterId();
        long paulId = paul.getMitarbeiterId();
        LocalDate austritt = LocalDate.of(2026, 12, 31);
        MitarbeiterDaten neu = TestDaten.formular("1024", "l.meyer", Rolle.MITARBEITER, lena.getMitarbeiterId());

        // Mitarbeiter: kein Zugriff; Projektleitung: nicht anlegen, nicht deaktivieren; ohne Anmeldung: nichts
        assertThatThrownBy(() -> mitarbeiterService.liste(Rolle.MITARBEITER)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.laden(paulId, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        MitarbeiterDaten vonMitarbeiter = TestDaten.formular("1012", "j.becker", Rolle.ADMIN, null);
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(jonasId, vonMitarbeiter, jonasId, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.vorgesetzteAuswahl(Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.liste(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> mitarbeiterService.anlegen(neu, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(jonasId, austritt, lena.getMitarbeiterId(),
                Rolle.PROJEKTLEITUNG)).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> mitarbeiterService.anlegen(neu, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThat(benutzerRepository.findByBenutzername("l.meyer")).isEmpty();
        assertThatThrownBy(() -> mitarbeiterService.laden(999_999, Rolle.ADMIN))
                .isInstanceOf(NichtGefundenException.class).hasMessage("Der Mitarbeiter wurde nicht gefunden.");

        // Ungültige Eingabe beim Bearbeiten: nichts wird gespeichert; Admin ändert nicht die eigene Rolle
        MitarbeiterDaten ungueltig = mitarbeiterService.laden(jonasId, Rolle.ADMIN);
        ungueltig.setUrlaubsanspruch(19);
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(jonasId, ungueltig, paulId, Rolle.ADMIN))
                .isInstanceOf(EingabeFehlerException.class);
        assertThat(urlaubskontoRepository.findAll()).extracting(Urlaubskonto::getMitarbeiter)
                .extracting(Mitarbeiter::getMitarbeiterId).doesNotContain(jonasId);
        // Für die Projektleitung gelten dieselben Prüfungen bei Stammdaten und Arbeitszeit (US-M1-11)
        MitarbeiterDaten ungueltigVonLeitung = mitarbeiterService.laden(jonasId, Rolle.PROJEKTLEITUNG);
        ungueltigVonLeitung.setNachname(" ");
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(jonasId, ungueltigVonLeitung, lena.getMitarbeiterId(),
                Rolle.PROJEKTLEITUNG)).isInstanceOf(EingabeFehlerException.class);
        assertThat(mitarbeiterRepository.findById(jonasId).orElseThrow().getNachname()).isEqualTo("Person 1012");
        // Die Projektleitung bearbeitet nur die Rolle Mitarbeiter: nicht den Admin, nicht sich selbst
        long lenaId = lena.getMitarbeiterId();
        assertThat(mitarbeiterService.laden(jonasId, Rolle.PROJEKTLEITUNG).isBearbeitbar()).isTrue();
        assertThat(mitarbeiterService.laden(paulId, Rolle.ADMIN).isBearbeitbar()).isTrue();
        MitarbeiterDaten adminVonLeitung = mitarbeiterService.laden(paulId, Rolle.PROJEKTLEITUNG);
        assertThat(adminVonLeitung.isBearbeitbar()).isFalse();
        adminVonLeitung.setEmail("lena@tischlerei-hartmann.example");
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(paulId, adminVonLeitung, lenaId, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Die Projektleitung darf nur Personen mit der Rolle „Mitarbeiter“ bearbeiten.");
        assertThat(mitarbeiterRepository.findById(paulId).orElseThrow().getEmail()).isNotEqualTo(adminVonLeitung.getEmail());
        MitarbeiterDaten selbstVonLeitung = mitarbeiterService.laden(lenaId, Rolle.PROJEKTLEITUNG);
        assertThat(selbstVonLeitung.isBearbeitbar()).isFalse();
        selbstVonLeitung.setWochenstunden(new BigDecimal("20"));
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(lenaId, selbstVonLeitung, lenaId, Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
        MitarbeiterDaten eigeneDaten = mitarbeiterService.laden(paulId, Rolle.ADMIN);
        eigeneDaten.setRolle(Rolle.MITARBEITER);
        assertThatThrownBy(() -> mitarbeiterService.bearbeiten(paulId, eigeneDaten, paulId, Rolle.ADMIN))
                .hasMessage("Sie können Ihre eigene Rolle nicht ändern.");
        assertThat(benutzerRepository.findByBenutzername("p.hartmann").orElseThrow().getRolle()).isEqualTo(Rolle.ADMIN);

        // Versandfehler beim Anlegen (7a): Mitarbeiter und Konto bleiben gespeichert, Hinweis zum erneuten Senden
        mailService.versandSchlaegtFehl();
        AnlegeErgebnis ergebnis = mitarbeiterService.anlegen(neu, Rolle.ADMIN);
        assertThat(ergebnis.mailVersendet()).isFalse();
        assertThat(ergebnis.meldung()).startsWith("Mitarbeiter gespeichert.").contains(TestMailService.FEHLERTEXT);
        assertThat(benutzerRepository.findByBenutzername("l.meyer").orElseThrow().getEinmalpasswortHash()).isNull();

        // Deaktivieren: Datum Pflicht und nach dem Eintritt (01.01.2020), nicht sich selbst,
        // Vorgesetzte erst nach Neuzuordnung ihrer Mitarbeiter (QA-M1-06)
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(jonasId, null, paulId, Rolle.ADMIN))
                .isInstanceOf(FachlicheException.class).hasMessage("Bitte geben Sie das Austrittsdatum an.");
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(jonasId, LocalDate.of(2020, 1, 1), paulId, Rolle.ADMIN))
                .hasMessage("Das Austrittsdatum muss nach dem Eintrittsdatum liegen.");
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(paulId, austritt, paulId, Rolle.ADMIN))
                .hasMessage("Sie können sich nicht selbst deaktivieren.");
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(lena.getMitarbeiterId(), austritt, paulId, Rolle.ADMIN))
                .hasMessageContaining("ist noch Vorgesetzte/r von");
        assertThat(mitarbeiterRepository.findById(lena.getMitarbeiterId()).orElseThrow().isAktiv()).isTrue();
        // Ebenso verliert eine Vorgesetzte ihre Rolle Projektleitung erst nach der Neuzuordnung
        MitarbeiterDaten lenaAlsAdmin = mitarbeiterService.laden(lena.getMitarbeiterId(), Rolle.ADMIN);
        lenaAlsAdmin.setRolle(Rolle.ADMIN);
        // Die Testperson hat noch kein Arbeitszeitmodell und kein Urlaubskonto, daher gültige Werte setzen
        lenaAlsAdmin.setWochenstunden(new BigDecimal("40"));
        lenaAlsAdmin.setArbeitstage(Set.of(DayOfWeek.MONDAY));
        lenaAlsAdmin.setUrlaubsanspruch(30);
        EingabeFehlerException nochVorgesetzte = catchThrowableOfType(EingabeFehlerException.class,
                () -> mitarbeiterService.bearbeiten(lena.getMitarbeiterId(), lenaAlsAdmin, paulId, Rolle.ADMIN));
        assertThat(nochVorgesetzte.meldungZu(MitarbeiterDaten.ROLLE)).isEqualTo("Test Person 1004 ist noch "
                + "Vorgesetzte/r von Test Person 1012, Lukas Meyer. Bitte ordnen Sie diesen Mitarbeitern zuerst "
                + "eine andere Projektleitung zu.");
        assertThat(benutzerRepository.findByBenutzername("l.schroeder").orElseThrow().getRolle())
                .isEqualTo(Rolle.PROJEKTLEITUNG);
        mitarbeiterService.deaktivieren(jonasId, austritt, paulId, Rolle.ADMIN);
        mitarbeiterService.deaktivieren(ergebnis.mitarbeiterId(), austritt, paulId, Rolle.ADMIN);
        mitarbeiterService.bearbeiten(lena.getMitarbeiterId(), lenaAlsAdmin, paulId, Rolle.ADMIN);
        assertThat(benutzerRepository.findByBenutzername("l.schroeder").orElseThrow().getRolle())
                .isEqualTo(Rolle.ADMIN);
        assertThatThrownBy(() -> mitarbeiterService.deaktivieren(jonasId, austritt, paulId, Rolle.ADMIN))
                .hasMessageContaining("ist bereits deaktiviert");

        // Auch beim Bearbeiten liegt der Austritt nach dem Eintritt (QA-M1-07)
        MitarbeiterDaten ehemaliger = mitarbeiterService.laden(jonasId, Rolle.ADMIN);
        ehemaliger.setEintrittsdatum(LocalDate.of(2027, 1, 1));
        EingabeFehlerException eintrittZuSpaet = catchThrowableOfType(EingabeFehlerException.class,
                () -> mitarbeiterService.bearbeiten(jonasId, ehemaliger, paulId, Rolle.ADMIN));
        assertThat(eintrittZuSpaet.meldungZu(MitarbeiterDaten.EINTRITTSDATUM))
                .isEqualTo("Das Eintrittsdatum muss vor dem Austrittsdatum liegen.");
    }
}
