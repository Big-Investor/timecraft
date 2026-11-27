package de.timecraft.antraege.service;

import static de.timecraft.antraege.Beispiele.buchung;
import static de.timecraft.antraege.Beispiele.zeit;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.ADMIN;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.JONAS;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.LENA;
import static de.timecraft.antraege.fake.FakeMitarbeiterAuskunft.MEHMET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.antraege.FesteUhrKonfiguration;
import de.timecraft.antraege.fake.FakeProjektAuskunft;
import de.timecraft.antraege.fake.FakeZeiterfassung;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Aenderung und Storno genehmigter Antraege ueber verknuepfte Folgeantraege (FA-M5-15, FA-M5-16,
 * FA-M5-17, US-M5-08): Das Original bleibt erhalten, Fachdaten aendern sich erst mit der Genehmigung.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FesteUhrKonfiguration.class)
class AenderungUndStornoTest {

    private static final LocalDate MO = LocalDate.of(2026, 12, 7);
    private static final LocalDate FR = LocalDate.of(2026, 12, 11);
    private static final LocalDate DI_VORWOCHE = LocalDate.of(2026, 11, 24);

    @Autowired
    private AntragService antraege;

    @Autowired
    private EntscheidungService entscheidung;

    @Autowired
    private AntragRepository repository;

    @Autowired
    private FakeZeiterfassung zeiterfassung;

    private long urlaub;

    @BeforeEach
    void genehmigterUrlaub() {
        urlaub = antraege.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(MO, FR)).getId();
        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, urlaub, null);
    }

    @AfterEach
    void aufraeumen() {
        repository.deleteAll();
        zeiterfassung.leeren();
    }

    private static AbwesenheitsDaten urlaubsdaten(LocalDate von, LocalDate bis) {
        return new AbwesenheitsDaten(AbwesenheitsArt.URLAUB, Urlaubsart.ERHOLUNGSURLAUB, von, bis, null);
    }

    private Antrag gelesen(long id) {
        return repository.findById(id).orElseThrow();
    }

    @Test
    void stornoHebtDieAbwesenheitErstNachGenehmigungAuf() {
        long storno = antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "Reise fällt aus.").getId();

        assertThat(gelesen(storno).getBezugsantragId()).isEqualTo(urlaub);
        assertThat(gelesen(storno).getBegruendung()).isEqualTo("Reise fällt aus.");
        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isPresent();

        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, storno, null);

        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isEmpty();
        assertThat(gelesen(urlaub).getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(gelesen(urlaub).getEntscheiderId()).isEqualTo(LENA);
        assertThat(gelesen(storno).getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER)).isEmpty();
        assertThat(antraege.antrag(JONAS, Rolle.MITARBEITER, storno).bezugsantrag().getId()).isEqualTo(urlaub);
    }

    @Test
    void abgelehntesStornoLaesstDenUrlaubBestehen() {
        long storno = antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "doch nicht").getId();

        entscheidung.ablehnen(LENA, Rolle.PROJEKTLEITUNG, storno, "Urlaub ist eingeplant.");

        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isPresent();
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER))
                .extracting(ansicht -> ansicht.antrag().getId()).containsExactly(urlaub);
    }

    @Test
    void stornoWirdNachDemZeitraumDerStorniertenAbwesenheitGefiltert() {
        antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "Grund");

        assertThat(antraege.zustaendigeAntraege(LENA, Rolle.PROJEKTLEITUNG,
                new AntragFilter(Antragstyp.STORNO, null, null, FR, FR))).hasSize(1);
        assertThat(antraege.zustaendigeAntraege(LENA, Rolle.PROJEKTLEITUNG,
                new AntragFilter(Antragstyp.STORNO, null, null, FR.plusDays(1), null))).isEmpty();
    }

    @Test
    void aenderungErsetztDieAbwesenheitBeiGenehmigung() {
        long aenderung = antraege.abwesenheitAendern(JONAS, Rolle.MITARBEITER, urlaub,
                urlaubsdaten(MO.plusDays(1), FR.plusDays(3))).getId();
        long alteAbwesenheit = zeiterfassung.abwesenheitZuAntrag(urlaub).orElseThrow();

        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isPresent();
        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, aenderung, null);

        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isEmpty();
        assertThat(zeiterfassung.aufrufe()).endsWith("aufheben " + alteAbwesenheit, "uebernehmen " + aenderung);
        assertThat(zeiterfassung.abwesenheiten()).singleElement().satisfies(abwesenheit -> {
            assertThat(abwesenheit.antragId()).isEqualTo(aenderung);
            assertThat(abwesenheit.von()).isEqualTo(MO.plusDays(1));
            assertThat(abwesenheit.bis()).isEqualTo(FR.plusDays(3));
        });
        assertThat(gelesen(urlaub).getStatus()).isEqualTo(Antragsstatus.GENEHMIGT);
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER))
                .extracting(ansicht -> ansicht.antrag().getId()).containsExactly(aenderung);
    }

    @Test
    void aenderungEinerZeitkorrekturGiltFuerDieselbeBuchung() {
        BuchungInfo buchung = buchung(48213, JONAS, DI_VORWOCHE, LocalTime.of(7, 0), LocalTime.of(14, 30),
                FakeProjektAuskunft.KUECHE);
        zeiterfassung.buchungHinzufuegen(buchung);
        long korrektur = antraege.zeitkorrekturBeantragen(JONAS, Rolle.MITARBEITER, new ZeitkorrekturDaten(48213L,
                buchung.beginn(), zeit(DI_VORWOCHE, 16, 15), Standort.BUERO, FakeProjektAuskunft.KUECHE, null)).getId();
        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, korrektur, null);

        long aenderung = antraege.zeitkorrekturAendern(JONAS, Rolle.MITARBEITER, korrektur, new ZeitkorrekturDaten(
                48213L, buchung.beginn(), zeit(DI_VORWOCHE, 15, 45), Standort.BUERO, FakeProjektAuskunft.KUECHE,
                "Doch nur bis 15:45")).getId();
        Zeitkorrekturantrag gespeichert = (Zeitkorrekturantrag) gelesen(aenderung);
        assertThat(gespeichert.getAltesEnde()).isEqualTo(zeit(DI_VORWOCHE, 16, 15));
        assertThat(zeiterfassung.buchung(48213).orElseThrow().ende()).isEqualTo(zeit(DI_VORWOCHE, 16, 15));

        entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, aenderung, null);

        assertThat(zeiterfassung.buchung(48213).orElseThrow().ende()).isEqualTo(zeit(DI_VORWOCHE, 15, 45));
        assertThat(((Zeitkorrekturantrag) gelesen(korrektur)).getNeuesEnde()).isEqualTo(zeit(DI_VORWOCHE, 16, 15));
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER)).extracting(AntragAnsicht::typ)
                .containsExactlyInAnyOrder(Antragstyp.URLAUB, Antragstyp.ZEITKORREKTUR);
    }

    @Test
    void stornoGenehmigungMitFehlerInDerZeiterfassungRolltZurueck() {
        long storno = antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "Grund").getId();
        zeiterfassung.setzeFehlermodus(true);

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, storno, null))
                .isInstanceOf(FachlicheException.class);

        assertThat(gelesen(storno).getStatus()).isEqualTo(Antragsstatus.OFFEN);
        assertThat(zeiterfassung.abwesenheitZuAntrag(urlaub)).isPresent();
    }

    @Test
    void fehlendeAbwesenheitInDerZeiterfassungVerhindertDasStorno() {
        long storno = antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "Grund").getId();
        zeiterfassung.aufheben(zeiterfassung.abwesenheitZuAntrag(urlaub).orElseThrow());

        assertThatThrownBy(() -> entscheidung.genehmigen(LENA, Rolle.PROJEKTLEITUNG, storno, null))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die genehmigte Abwesenheit wurde in der Zeiterfassung nicht gefunden.");
        assertThat(gelesen(storno).getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }

    @Test
    void widerspruechlicheFolgeantraegeWerdenVerhindert() {
        long storno = antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, urlaub, "Grund").getId();

        assertThatThrownBy(() -> antraege.abwesenheitAendern(JONAS, Rolle.MITARBEITER, urlaub, urlaubsdaten(MO, MO)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Zu diesem Antrag gibt es bereits einen offenen Änderungs- oder Stornoantrag.");
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER)).isEmpty();

        antraege.stornoBearbeiten(JONAS, Rolle.MITARBEITER, storno, "Neuer Grund");
        assertThat(gelesen(storno).getBegruendung()).isEqualTo("Neuer Grund");
        assertThatThrownBy(() -> antraege.stornoBearbeiten(JONAS, Rolle.MITARBEITER, storno, " "))
                .isInstanceOf(FachlicheException.class);
        assertThat(gelesen(storno).getBegruendung()).isEqualTo("Neuer Grund");

        antraege.zurueckziehen(JONAS, Rolle.MITARBEITER, storno);
        assertThat(antraege.aenderbareAntraege(JONAS, Rolle.MITARBEITER)).hasSize(1);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void nurEigeneGenehmigteAntraegeUndNurMitPassenderRolle() {
        long offen = antraege.abwesenheitBeantragen(JONAS, Rolle.MITARBEITER, urlaubsdaten(FR.plusDays(10),
                FR.plusDays(10))).getId();

        assertThatThrownBy(() -> antraege.stornoBeantragen(JONAS, Rolle.MITARBEITER, offen, "Grund"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Ändern oder stornieren können Sie nur genehmigte Anträge.");
        assertThatThrownBy(() -> antraege.stornoBeantragen(MEHMET, Rolle.MITARBEITER, urlaub, "Grund"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> antraege.stornoBeantragen(ADMIN, Rolle.ADMIN, urlaub, "Grund"))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> antraege.abwesenheitAendern(JONAS, null, urlaub, urlaubsdaten(MO, MO)))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> antraege.aenderbareAntraege(ADMIN, Rolle.ADMIN))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThat(antraege.aenderbareAntraege(MEHMET, Rolle.MITARBEITER)).isEmpty();
        assertThat(repository.count()).isEqualTo(2);
    }
}
