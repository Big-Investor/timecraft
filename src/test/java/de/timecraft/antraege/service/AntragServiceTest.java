package de.timecraft.antraege.service;

import static de.timecraft.antraege.Beispiele.FESTE_UHR;
import static de.timecraft.antraege.Beispiele.buchung;
import static de.timecraft.antraege.Beispiele.mitStatus;
import static de.timecraft.antraege.Beispiele.urlaub;
import static de.timecraft.antraege.Beispiele.zeit;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.ADMIN;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.FELIX;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.JONAS;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.LENA;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.MEHMET;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.PETER;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.SARA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.FesteUhrKonfiguration;
import de.timecraft.antraege.fake.FakeProjektAuskunft;
import de.timecraft.antraege.fake.FakeZeiterfassung;
import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.MaterialanpassungsantragTestersatz;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * AntragService mit echten Transaktionen (Spring-Kontext, Testdatenbank, Fakes), damit
 * "nichts wird gespeichert" und die Zeilensperre echt geprueft werden. Rollen: FA-M5-19, QA-M5-03.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FesteUhrKonfiguration.class)
class AntragServiceTest {

    private static final LocalDate MO = LocalDate.of(2026, 12, 7);
    private static final LocalDate FR = LocalDate.of(2026, 12, 11);
    private static final LocalDate DI_VORWOCHE = LocalDate.of(2026, 11, 24);

    @Autowired
    private AntragService service;

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

    private static AbwesenheitsDaten urlaubsdaten(LocalDate von, LocalDate bis) {
        return new AbwesenheitsDaten(AbwesenheitsArt.URLAUB, Urlaubsart.ERHOLUNGSURLAUB, von, bis, null);
    }

    private ZeitkorrekturDaten korrekturdaten(Instant neuesEnde) {
        return new ZeitkorrekturDaten(buchung.zeiteintragId(), buchung.beginn(), neuesEnde, Standort.UNTERWEGS,
                FakeProjektAuskunft.TREPPE, "Vergessen auszustempeln");
    }

    // ---------- Erstellen ----------

    @Test
    void mitarbeiterBeantragtUrlaub() {
        Abwesenheitsantrag antrag = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER,
                new AbwesenheitsDaten(AbwesenheitsArt.URLAUB, Urlaubsart.ERHOLUNGSURLAUB, MO, FR, "  Familie  "));

        Abwesenheitsantrag gelesen = (Abwesenheitsantrag) repository.findById(antrag.getId()).orElseThrow();
        assertThat(gelesen.getAntragstellerId()).isEqualTo(JONAS);
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(gelesen.getEingereichtAm()).isEqualTo(FESTE_UHR.instant());
        assertThat(Antragstyp.von(gelesen)).isEqualTo(Antragstyp.URLAUB);
        assertThat(gelesen.getVon()).isEqualTo(MO);
        assertThat(gelesen.getBegruendung()).isEqualTo("Familie");
        assertThat(zeiterfassung.aufrufe()).isEmpty();
    }

    @Test
    void krankheitUndSonstigeAbwesenheitOhneBegruendung() {
        service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER,
                new AbwesenheitsDaten(AbwesenheitsArt.KRANKHEIT, null, MO, MO, " "));
        service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER,
                new AbwesenheitsDaten(AbwesenheitsArt.SONSTIGE, null, FR, FR, null));

        assertThat(service.eigeneAntraege(JONAS, Rolle.MITARBEITER)).extracting(AntragAnsicht::typ)
                .containsExactlyInAnyOrder(Antragstyp.KRANKHEIT, Antragstyp.SONSTIGE_ABWESENHEIT);
        assertThat(repository.findAll()).allMatch(antrag -> antrag.getBegruendung() == null);
    }

    @Test
    void projektleitungStelltEigeneAntraege() {
        service.abwesenheitBeantragen(LENA, Rolle.PROJEKTLEITUNG, urlaubsdaten(MO, FR));

        assertThat(service.eigeneAntraege(LENA, Rolle.PROJEKTLEITUNG)).hasSize(1);
    }

    @Test
    void adminUndNichtAngemeldeteDuerfenNichtsBeantragen() {
        assertThatThrownBy(() -> service.abwesenheitBeantragen(ADMIN, Rolle.ADMIN, urlaubsdaten(MO, FR)))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zeitkorrekturBeantragen(ADMIN, Rolle.ADMIN, korrekturdaten(buchung.ende())))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.abwesenheitBeantragen(JONAS, null, urlaubsdaten(MO, FR)))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void ungueltigerAntragWirdNichtGespeichert() {
        assertThatThrownBy(() -> service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(FR, MO)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Ende darf nicht vor dem Beginn liegen.");
        assertThat(repository.count()).isZero();
    }

    @Test
    void zeitkorrekturSpeichertAlteWerteAusDerZeiterfassung() {
        Zeitkorrekturantrag antrag = service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER,
                korrekturdaten(zeit(DI_VORWOCHE, 16, 15)));

        Zeitkorrekturantrag gelesen = (Zeitkorrekturantrag) repository.findById(antrag.getId()).orElseThrow();
        assertThat(gelesen.getZeiteintragId()).isEqualTo(48213);
        assertThat(gelesen.getAlterBeginn()).isEqualTo(buchung.beginn());
        assertThat(gelesen.getAltesEnde()).isEqualTo(buchung.ende());
        assertThat(gelesen.getAlterStandort()).isEqualTo(Standort.BUERO);
        assertThat(gelesen.getNeuesEnde()).isEqualTo(zeit(DI_VORWOCHE, 16, 15));
        assertThat(gelesen.getNeuerStandort()).isEqualTo(Standort.UNTERWEGS);
        assertThat(gelesen.getNeuesProjektId()).isEqualTo(FakeProjektAuskunft.TREPPE);
        assertThat(zeiterfassung.buchung(48213).orElseThrow()).isEqualTo(buchung);
    }

    @Test
    void zeitkorrekturFremderBuchungWirdAbgewiesen() {
        assertThatThrownBy(() -> service.zeitkorrekturBeantragen(MEHMET, Rolle.MITARBEITER,
                korrekturdaten(zeit(DI_VORWOCHE, 16, 15)))).isInstanceOf(ZugriffVerweigertException.class);
        assertThat(repository.count()).isZero();
    }

    // ---------- Bearbeiten ----------

    @Test
    void eigenenOffenenAntragBearbeiten() {
        Long id = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR)).getId();

        service.abwesenheitBearbeiten(JONAS, Rolle.MITARBEITER, id, new AbwesenheitsDaten(AbwesenheitsArt.URLAUB,
                Urlaubsart.SONDERURLAUB, MO, MO.plusDays(1), "kürzer"));

        Abwesenheitsantrag gelesen = (Abwesenheitsantrag) repository.findById(id).orElseThrow();
        assertThat(gelesen.getBis()).isEqualTo(MO.plusDays(1));
        assertThat(gelesen.getUrlaubsart()).isEqualTo(Urlaubsart.SONDERURLAUB);
        assertThat(gelesen.getBegruendung()).isEqualTo("kürzer");
        assertThat(gelesen.getEingereichtAm()).isEqualTo(FESTE_UHR.instant());
    }

    @Test
    void ungueltigeBearbeitungAendertNichts() {
        Long id = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR)).getId();

        assertThatThrownBy(() -> service.abwesenheitBearbeiten(JONAS, Rolle.MITARBEITER, id, urlaubsdaten(FR, MO)))
                .isInstanceOf(FachlicheException.class);

        Abwesenheitsantrag gelesen = (Abwesenheitsantrag) repository.findById(id).orElseThrow();
        assertThat(gelesen.getVon()).isEqualTo(MO);
        assertThat(gelesen.getBis()).isEqualTo(FR);
    }

    @Test
    void nurEigeneOffeneAntraegeSindAenderbar() {
        Long id = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR)).getId();

        assertThatThrownBy(() -> service.abwesenheitBearbeiten(MEHMET, Rolle.MITARBEITER, id, urlaubsdaten(MO, MO)))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.abwesenheitBearbeiten(LENA, Rolle.PROJEKTLEITUNG, id, urlaubsdaten(MO, MO)))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zurueckziehen(ADMIN, Rolle.ADMIN, id))
                .isInstanceOf(ZugriffVerweigertException.class);

        service.zurueckziehen(JONAS, Rolle.MITARBEITER, id);
        assertThatThrownBy(() -> service.abwesenheitBearbeiten(JONAS, Rolle.MITARBEITER, id, urlaubsdaten(MO, MO)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Nur offene Anträge können bearbeitet oder zurückgezogen werden.");
        assertThatThrownBy(() -> service.zurueckziehen(JONAS, Rolle.MITARBEITER, id))
                .isInstanceOf(FachlicheException.class);
        assertThatThrownBy(() -> service.zurueckziehen(JONAS, Rolle.MITARBEITER, 4711))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void zeitkorrekturBearbeitenAendertNurWunschwerte() {
        Long id = service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, korrekturdaten(zeit(DI_VORWOCHE, 16, 15)))
                .getId();

        service.zeitkorrekturBearbeiten(JONAS, Rolle.MITARBEITER, id, korrekturdaten(zeit(DI_VORWOCHE, 15, 45)));
        Zeitkorrekturantrag gelesen = (Zeitkorrekturantrag) repository.findById(id).orElseThrow();
        assertThat(gelesen.getNeuesEnde()).isEqualTo(zeit(DI_VORWOCHE, 15, 45));
        assertThat(gelesen.getAltesEnde()).isEqualTo(buchung.ende());

        ZeitkorrekturDaten andereBuchung = new ZeitkorrekturDaten(1L, buchung.beginn(), zeit(DI_VORWOCHE, 15, 0),
                Standort.BUERO, null, null);
        assertThatThrownBy(() -> service.zeitkorrekturBearbeiten(JONAS, Rolle.MITARBEITER, id, andereBuchung))
                .isInstanceOf(FachlicheException.class);
        assertThatThrownBy(() -> service.abwesenheitBearbeiten(JONAS, Rolle.MITARBEITER, id, urlaubsdaten(MO, FR)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Dieser Antrag ist keine Abwesenheit.");
    }

    // ---------- Zurueckziehen ----------

    @Test
    void zurueckziehenBehaeltDenAntragUndAendertKeineFachdaten() {
        Long id = service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, korrekturdaten(zeit(DI_VORWOCHE, 16, 15)))
                .getId();

        service.zurueckziehen(JONAS, Rolle.MITARBEITER, id);

        Antrag gelesen = repository.findById(id).orElseThrow();
        assertThat(gelesen.getStatus()).isEqualTo(Antragsstatus.ZURUECKGEZOGEN);
        assertThat(gelesen.getEntscheiderId()).isNull();
        assertThat(zeiterfassung.aufrufe()).isEmpty();
        assertThat(zeiterfassung.buchung(48213).orElseThrow()).isEqualTo(buchung);
    }

    // ---------- Ansehen ----------

    @Test
    void eigeneAntraegeMitEntscheidungUndOhneFremdeOderMaterialantraege() {
        Abwesenheitsantrag abgelehnt = mitStatus(urlaub(JONAS, Urlaubsart.ERHOLUNGSURLAUB, MO, FR),
                Antragsstatus.ABGELEHNT);
        abgelehnt.setEntscheiderId(LENA);
        abgelehnt.setEntschiedenAm(FESTE_UHR.instant());
        abgelehnt.setEntscheidungshinweis("Montage Ladenbau Krause in dieser Woche.");
        repository.save(abgelehnt);
        repository.save(urlaub(MEHMET, Urlaubsart.ERHOLUNGSURLAUB, MO, FR));
        MaterialanpassungsantragTestersatz material = new MaterialanpassungsantragTestersatz();
        material.setAntragstellerId(JONAS);
        material.setEingereichtAm(FESTE_UHR.instant());
        repository.save(material);

        assertThat(service.eigeneAntraege(JONAS, Rolle.MITARBEITER)).singleElement().satisfies(ansicht -> {
            assertThat(ansicht.typ()).isEqualTo(Antragstyp.URLAUB);
            assertThat(ansicht.entscheiderName()).isEqualTo("Lena Schröder");
            assertThat(ansicht.antrag().getEntscheidungshinweis()).isEqualTo("Montage Ladenbau Krause in dieser Woche.");
            assertThat(ansicht.urlaubstage()).isEqualTo(5);
            assertThat(ansicht.abwesendImTeam()).isNull();
        });
        assertThatThrownBy(() -> service.eigeneAntraege(ADMIN, Rolle.ADMIN))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.antrag(JONAS, Rolle.MITARBEITER, material.getId()))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void projektleitungSiehtNurZustaendigeAntraege() {
        service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR));
        service.abwesenheitBeantragen(MEHMET, Rolle.MITARBEITER, urlaubsdaten(MO, FR));
        service.abwesenheitBeantragen(SARA, Rolle.MITARBEITER, urlaubsdaten(MO, FR));
        service.abwesenheitBeantragen(LENA, Rolle.PROJEKTLEITUNG, urlaubsdaten(MO, FR));

        assertThat(service.zustaendigeAntraege(LENA, Rolle.PROJEKTLEITUNG, AntragFilter.alle()))
                .extracting(AntragAnsicht::antragstellerName).containsExactlyInAnyOrder("Jonas Becker", "Mehmet Yılmaz");
        assertThat(service.zustaendigeAntraege(PETER, Rolle.PROJEKTLEITUNG, AntragFilter.alle()))
                .extracting(AntragAnsicht::antragstellerName).containsExactlyInAnyOrder("Lena Schröder", "Sara Klein");
        assertThatThrownBy(() -> service.zustaendigeAntraege(JONAS, Rolle.MITARBEITER, AntragFilter.alle()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zustaendigeAntraege(ADMIN, Rolle.ADMIN, AntragFilter.alle()))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zustaendigeAntraege(LENA, null, AntragFilter.alle()))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void filterNachTypStatusMitarbeiterUndZeitraum() {
        service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR));
        Long krank = service.abwesenheitBeantragen(MEHMET, Rolle.MITARBEITER,
                new AbwesenheitsDaten(AbwesenheitsArt.KRANKHEIT, null, MO, MO, null)).getId();
        service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, korrekturdaten(zeit(DI_VORWOCHE, 16, 15)));
        service.zurueckziehen(MEHMET, Rolle.MITARBEITER, krank);
        assertThat(filter(new AntragFilter(Antragstyp.URLAUB, null, null, null, null))).hasSize(1);
        assertThat(filter(new AntragFilter(null, Antragsstatus.OFFEN, null, null, null))).hasSize(2);
        assertThat(filter(new AntragFilter(null, null, MEHMET, null, null))).hasSize(1);
        assertThat(filter(new AntragFilter(null, null, null, MO, MO))).hasSize(2);
        assertThat(filter(new AntragFilter(null, null, null, FR, null))).hasSize(1);
        assertThat(filter(new AntragFilter(null, null, null, FR.plusDays(1), null))).isEmpty();
        assertThat(filter(new AntragFilter(null, null, null, null, DI_VORWOCHE.minusDays(1)))).isEmpty();
        assertThat(filter(new AntragFilter(Antragstyp.ZEITKORREKTUR, Antragsstatus.OFFEN, JONAS, DI_VORWOCHE,
                DI_VORWOCHE))).hasSize(1);
    }

    @Test
    void weitereRechteUndUngueltigeKorrekturBearbeitung() {
        Long id = service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, korrekturdaten(zeit(DI_VORWOCHE, 16, 15)))
                .getId();

        assertThatThrownBy(() -> service.zeitkorrekturBearbeiten(JONAS, Rolle.MITARBEITER, id,
                korrekturdaten(FESTE_UHR.instant().plusSeconds(3600)))).isInstanceOf(FachlicheException.class);
        assertThat(((Zeitkorrekturantrag) repository.findById(id).orElseThrow()).getNeuesEnde())
                .isEqualTo(zeit(DI_VORWOCHE, 16, 15));
        assertThatThrownBy(() -> service.zeitkorrekturBearbeiten(LENA, Rolle.PROJEKTLEITUNG, id,
                korrekturdaten(zeit(DI_VORWOCHE, 15, 0)))).isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zurueckziehen(LENA, Rolle.PROJEKTLEITUNG, id))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.zurueckziehen(JONAS, null, id)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.eigeneAntraege(JONAS, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.antrag(JONAS, null, id)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.aktiveProjekte(JONAS, null)).isInstanceOf(NichtAngemeldetException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }

    private List<AntragAnsicht> filter(AntragFilter filter) {
        return service.zustaendigeAntraege(LENA, Rolle.PROJEKTLEITUNG, filter);
    }

    @Test
    void einzelnerAntragNurFuerEigentuemerUndZustaendigeProjektleitung() {
        Long id = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR)).getId();
        zeiterfassung.abwesenheitHinzufuegen(900, MEHMET, AbwesenheitsArt.URLAUB, FR, FR);

        assertThat(service.antrag(JONAS, Rolle.MITARBEITER, id).abwesendImTeam()).isNull();
        AntragAnsicht fuerLena = service.antrag(LENA, Rolle.PROJEKTLEITUNG, id);
        assertThat(fuerLena.abwesendImTeam()).isEqualTo(1);
        assertThat(fuerLena.teamgroesse()).isEqualTo(2);
        assertThatThrownBy(() -> service.antrag(PETER, Rolle.PROJEKTLEITUNG, id))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.antrag(MEHMET, Rolle.MITARBEITER, id))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.antrag(ADMIN, Rolle.ADMIN, id))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.antrag(JONAS, Rolle.MITARBEITER, 4711))
                .isInstanceOf(NichtGefundenException.class);
    }

    @Test
    void pruefdatenEinerZeitkorrekturUndEinesUnbezahltenUrlaubs() {
        Long korrektur = service.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER,
                korrekturdaten(zeit(DI_VORWOCHE, 16, 15))).getId();
        Long unbezahlt = service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, new AbwesenheitsDaten(
                AbwesenheitsArt.URLAUB, Urlaubsart.UNBEZAHLTER_URLAUB, MO, FR, null)).getId();

        AntragAnsicht ansicht = service.antrag(LENA, Rolle.PROJEKTLEITUNG, korrektur);
        assertThat(ansicht.buchung()).isEqualTo(buchung);
        assertThat(ansicht.bisherigesProjekt()).isEqualTo("Einbauküche Wagner");
        assertThat(ansicht.gewuenschtesProjekt()).isEqualTo("Treppe Lindenstraße");
        assertThat(ansicht.gewuenschteNettoMinuten()).isEqualTo(9 * 60 + 15 - 30);
        assertThat(service.antrag(LENA, Rolle.PROJEKTLEITUNG, unbezahlt).sondergenehmigung()).isTrue();
    }

    // ---------- Auswahllisten ----------

    @Test
    void auswahllistenFuerDasFormular() {
        zeiterfassung.buchungHinzufuegen(buchung(2, MEHMET, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(9, 0), null));
        zeiterfassung.buchungHinzufuegen(new BuchungInfo(3, JONAS, null, Standort.BUERO,
                zeit(DI_VORWOCHE, 18, 0), null, 0, 0, false));

        assertThat(service.buchungenAmTag(JONAS, Rolle.MITARBEITER, DI_VORWOCHE)).containsExactly(buchung);
        assertThat(service.buchungenAmTag(JONAS, Rolle.MITARBEITER, null)).isEmpty();
        assertThat(service.aktiveProjekte(JONAS, Rolle.MITARBEITER)).hasSize(2);
        assertThat(service.zugeordneteMitarbeiter(LENA, Rolle.PROJEKTLEITUNG)).extracting(MitarbeiterInfo::mitarbeiterId)
                .containsExactly(JONAS, FELIX, MEHMET);
        assertThatThrownBy(() -> service.zugeordneteMitarbeiter(JONAS, Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> service.buchungenAmTag(ADMIN, Rolle.ADMIN, DI_VORWOCHE))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void urlaubsvorschauZeigtTageUndResturlaub() {
        service.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR));

        assertThat(service.urlaubsvorschau(JONAS, Rolle.MITARBEITER, Urlaubsart.ERHOLUNGSURLAUB,
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 25), null, null))
                .isEqualTo(new Urlaubsvorschau(4, 30 - 5 - 4));
        assertThat(service.urlaubsvorschau(JONAS, Rolle.MITARBEITER, Urlaubsart.UNBEZAHLTER_URLAUB, MO, FR, null, null))
                .isEqualTo(new Urlaubsvorschau(5, null));
        assertThat(service.urlaubsvorschau(JONAS, Rolle.MITARBEITER, Urlaubsart.ERHOLUNGSURLAUB, FR, MO, null, null))
                .isNull();
        assertThatThrownBy(() -> service.urlaubsvorschau(ADMIN, Rolle.ADMIN, Urlaubsart.ERHOLUNGSURLAUB, MO, FR,
                null, null)).isInstanceOf(ZugriffVerweigertException.class);
    }
}
