package de.timecraft.antraege.service;

import static de.timecraft.antraege.Beispiele.FESTE_UHR;
import static de.timecraft.antraege.Beispiele.buchung;
import static de.timecraft.antraege.Beispiele.zeit;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.ADMIN;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.JONAS;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.LENA;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.PETER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.FesteUhrKonfiguration;
import de.timecraft.antraege.fake.FakeProjektAuskunft;
import de.timecraft.antraege.fake.FakeZeiterfassung;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.MaterialanpassungsantragTestersatz;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Genehmigen und Ablehnen mit echten Transaktionen gegen die Testdatenbank und den aufzeichnenden
 * M2-Fake (FA-M5-12 bis FA-M5-14, FA-M5-16 bis FA-M5-18, QA-M5-01 bis QA-M5-04).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FesteUhrKonfiguration.class)
class EntscheidungServiceTest {

    private static final LocalDate MO = LocalDate.of(2026, 12, 7);
    private static final LocalDate FR = LocalDate.of(2026, 12, 11);
    private static final LocalDate DI_VORWOCHE = LocalDate.of(2026, 11, 24);

    @Autowired
    private EntscheidungService entscheidung;

    @Autowired
    private AntragService antraege;

    @Autowired
    private AntragRepository repository;

    @Autowired
    private FakeZeiterfassung zeiterfassung;

    private BuchungInfo buchung;

    @BeforeEach
    void buchungAnlegen() {
        buchung = buchung(48213, JONAS, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(14, 30),
                FakeProjektAuskunft.KUECHE);
        zeiterfassung.buchungHinzufuegen(buchung);
    }

    @AfterEach
    void aufraeumen() {
        repository.deleteAll();
        zeiterfassung.leeren();
    }

    private long urlaub(long mitarbeiterId, Rolle rolle) {
        return antraege.abwesenheitBeantragen(mitarbeiterId, rolle,
                new AbwesenheitsDaten(AbwesenheitsArt.URLAUB, Urlaubsart.ERHOLUNGSURLAUB, MO, FR, null)).getId();
    }

    private long zeitkorrektur() {
        return antraege.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, new ZeitkorrekturDaten(48213L,
                buchung.beginn(), zeit(DI_VORWOCHE, 16, 15), Standort.UNTERWEGS, FakeProjektAuskunft.TREPPE,
                "Vergessen auszustempeln")).getId();
    }

    private Antrag gelesen(long id) {
        return repository.findById(id).orElseThrow();
    }

    private void bleibtOffen(long id) {
        Antrag antrag = gelesen(id);
        assertThat(antrag.getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(antrag.getEntscheiderId()).isNull();
        assertThat(antrag.getEntschiedenAm()).isNull();
    }

    @Test
    void genehmigterUrlaubWirdInDieZeiterfassungUebernommen() {
        long id = urlaub(JONAS, Rolle.MITARBEITER);

        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, "  Viel Erholung  ");

        Antrag antrag = gelesen(id);
        assertThat(antrag.getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(antrag.getEntscheiderId()).isEqualTo(LENA);
        assertThat(antrag.getEntschiedenAm()).isEqualTo(FESTE_UHR.instant());
        assertThat(antrag.getEntscheidungshinweis()).isEqualTo("Viel Erholung");
        assertThat(zeiterfassung.abwesenheiten()).singleElement().satisfies(abwesenheit -> {
            assertThat(abwesenheit.antragId()).isEqualTo(id);
            assertThat(abwesenheit.mitarbeiterId()).isEqualTo(JONAS);
            assertThat(abwesenheit.art()).isEqualTo(AbwesenheitsArt.URLAUB);
            assertThat(abwesenheit.von()).isEqualTo(MO);
            assertThat(abwesenheit.bis()).isEqualTo(FR);
        });
    }

    @Test
    void genehmigteZeitkorrekturAendertDieBuchungUndBehaeltDieOriginalwerte() {
        long id = zeitkorrektur();
        assertThat(zeiterfassung.buchung(48213).orElseThrow()).isEqualTo(buchung);

        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null);

        BuchungInfo geaendert = zeiterfassung.buchung(48213).orElseThrow();
        assertThat(geaendert.ende()).isEqualTo(zeit(DI_VORWOCHE, 16, 15));
        assertThat(geaendert.standort()).isEqualTo(Standort.UNTERWEGS);
        assertThat(geaendert.projektId()).isEqualTo(FakeProjektAuskunft.TREPPE);
        assertThat(geaendert.geaendert()).isTrue();
        Zeitkorrekturantrag antrag = (Zeitkorrekturantrag) gelesen(id);
        assertThat(antrag.getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(antrag.getAltesEnde()).isEqualTo(buchung.ende());
        assertThat(antrag.getAlterStandort()).isEqualTo(Standort.BUERO);
        assertThat(antrag.getEntscheidungshinweis()).isNull();
    }

    @Test
    void ablehnungLaesstFachdatenUnveraendertUndZeigtDenHinweis() {
        long urlaub = urlaub(JONAS, Rolle.MITARBEITER);
        long korrektur = zeitkorrektur();

        entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, urlaub, "Montage Ladenbau Krause in dieser Woche.");
        entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, korrektur, null);

        assertThat(zeiterfassung.aufrufe()).isEmpty();
        assertThat(zeiterfassung.buchung(48213).orElseThrow()).isEqualTo(buchung);
        assertThat(gelesen(urlaub).getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
        assertThat(gelesen(urlaub).getEntschiedenAm()).isEqualTo(FESTE_UHR.instant());
        assertThat(antraege.eigeneAntraege(JONAS, Rolle.MITARBEITER))
                .filteredOn(ansicht -> ansicht.antrag().getId() == urlaub).singleElement()
                .satisfies(ansicht -> {
                    assertThat(ansicht.antrag().getEntscheidungshinweis())
                            .isEqualTo("Montage Ladenbau Krause in dieser Woche.");
                    assertThat(ansicht.entscheiderName()).isEqualTo("Lena Schröder");
                });
    }

    @Test
    void einAntragWirdNurEinmalEntschieden() {
        long id = urlaub(JONAS, Rolle.MITARBEITER);
        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null);

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null))
                .isInstanceOf(FachlicheException.class).hasMessage("Der Antrag wurde bereits entschieden.");
        assertThatThrownBy(() -> entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, id, "doch nicht"))
                .isInstanceOf(FachlicheException.class);
        assertThat(gelesen(id).getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(gelesen(id).getEntscheidungshinweis()).isNull();
        assertThat(zeiterfassung.aufrufe()).hasSize(1);
    }

    @Test
    void gleichzeitigeEntscheidungenWerdenNurEinmalAusgefuehrt() throws Exception {
        long id = urlaub(JONAS, Rolle.MITARBEITER);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> genehmigen = () -> {
            start.await();
            try {
                entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null);
                return true;
            } catch (RuntimeException abgewiesen) {
                return false;
            }
        };
        ExecutorService ausfuehrung = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> ergebnisse = new ArrayList<>();
            ergebnisse.add(ausfuehrung.submit(genehmigen));
            ergebnisse.add(ausfuehrung.submit(genehmigen));
            start.countDown();
            int erfolgreich = 0;
            for (Future<Boolean> ergebnis : ergebnisse) {
                erfolgreich += ergebnis.get() ? 1 : 0;
            }
            assertThat(erfolgreich).isEqualTo(1);
        } finally {
            ausfuehrung.shutdown();
        }
        assertThat(zeiterfassung.aufrufe()).containsExactly("uebernehmen " + id);
    }

    @Test
    void zurueckgezogenerAntragIstNichtEntscheidbar() {
        long id = urlaub(JONAS, Rolle.MITARBEITER);
        antraege.zurueckziehen(JONAS, Rolle.MITARBEITER, id);

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Antrag wurde zurückgezogen und kann nicht mehr entschieden werden.");
        assertThat(zeiterfassung.aufrufe()).isEmpty();
    }

    @Test
    void fehlerInDerZeiterfassungRolltAllesZurueck() {
        long urlaub = urlaub(JONAS, Rolle.MITARBEITER);
        long korrektur = zeitkorrektur();
        zeiterfassung.setzeFehlermodus(true);

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, urlaub, "ok"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Daten konnten in der Zeiterfassung nicht gespeichert werden.");
        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, korrektur, "ok"))
                .isInstanceOf(FachlicheException.class);

        bleibtOffen(urlaub);
        bleibtOffen(korrektur);
        assertThat(zeiterfassung.abwesenheiten()).isEmpty();
        assertThat(zeiterfassung.buchung(48213).orElseThrow()).isEqualTo(buchung);

        zeiterfassung.setzeFehlermodus(false);
        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, urlaub, "ok");
        assertThat(gelesen(urlaub).getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
    }

    @Test
    void fachregelnWerdenBeiDerGenehmigungErneutGeprueft() {
        long id = urlaub(JONAS, Rolle.MITARBEITER);
        zeiterfassung.abwesenheitHinzufuegen(900, JONAS, AbwesenheitsArt.KRANKHEIT, FR, FR);

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, id, null))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Im Zeitraum ist bereits eine Abwesenheit eingetragen.");
        bleibtOffen(id);
        entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, id, "Krank gemeldet");
        assertThat(gelesen(id).getStatus()).isEqualTo(Antragsstatus.ABGELEHNT);
    }

    @Test
    void nurDieZustaendigeProjektleitungEntscheidet() {
        long jonas = urlaub(JONAS, Rolle.MITARBEITER);
        long lena = urlaub(LENA, Rolle.PROJEKTLEITUNG);

        assertThatThrownBy(() -> entscheidung.genehmigen(PETER, Rolle.PROJEKTLEITUNG, jonas, null))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie sind für diesen Antrag nicht zuständig.");
        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, lena, null))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Eigene Anträge können Sie nicht selbst entscheiden.");
        assertThatThrownBy(() -> entscheidung.genehmigen(JONAS, Rolle.MITARBEITER, jonas, null))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> entscheidung.ablehnen(ADMIN, Rolle.ADMIN, jonas, null))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, null, jonas, null))
                .isInstanceOf(NichtAngemeldetException.class);
        bleibtOffen(jonas);

        entscheidung.genehmigen(PETER, Rolle.PROJEKTLEITUNG, lena, null);
        assertThat(gelesen(lena).getEntscheiderId()).isEqualTo(PETER);
    }

    @Test
    void ungueltigerHinweisUndUnbekannteAntraege() {
        long id = urlaub(JONAS, Rolle.MITARBEITER);
        MaterialanpassungsantragTestersatz material = new MaterialanpassungsantragTestersatz();
        material.setAntragstellerId(JONAS);
        material.setEingereichtAm(FESTE_UHR.instant());
        long materialId = repository.save(material).getId();

        assertThatThrownBy(() -> entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, id, "x".repeat(256)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Entscheidungshinweis darf höchstens 255 Zeichen lang sein.");
        bleibtOffen(id);
        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, 4711, null))
                .isInstanceOf(NichtGefundenException.class);
        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, materialId, null))
                .isInstanceOf(NichtGefundenException.class);
        assertThat(gelesen(materialId).getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }
}
