package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Qualifikation;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.service.MitarbeiterService;
import de.timecraft.benutzer.service.MitarbeiterZeile;
import de.timecraft.benutzer.service.TestDaten;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prüft die Mitarbeiterliste (UC-M1-11, FA-M1-15, US-M1-14) mit echtem Service und Testdatenbank:
 * Spalten und Inhalte wie im Mockup, erst aktive, dann inaktive Mitarbeiter, „+ Neuer Mitarbeiter“
 * nur für den Admin, kein Zugriff für Mitarbeitende (QA-A-01).
 *
 * @author Leon-Selim Kannenberg
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MitarbeiterListeViewTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    @Autowired
    private MitarbeiterService mitarbeiterService;

    private Mitarbeiter paul;
    private Mitarbeiter lena;
    private Mitarbeiter jonas;
    private Mitarbeiter sophie;

    @BeforeEach
    void vorbereiten() {
        paul = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "admin", Rolle.ADMIN, "1001");
        lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "projektleitung",
                Rolle.PROJEKTLEITUNG, "1004");
        jonas = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "mitarbeiter",
                Rolle.MITARBEITER, "1012");
        jonas.setVorgesetzter(lena);
        jonas.setQualifikation(Qualifikation.AZUBI);
        mitarbeiterRepository.save(jonas);
        sophie = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "s.kuehn", Rolle.MITARBEITER, "1009");
        sophie.setAktiv(false);
        sophie.setAustrittsdatum(LocalDate.of(2026, 6, 30));
        mitarbeiterRepository.save(sophie);
        ViewTestHilfe.fensterOeffnen();
    }

    @AfterEach
    void aufraeumen() {
        ViewTestHilfe.aufraeumen();
    }

    @Test
    void adminSiehtAlleMitarbeiterWieImMockup() {
        MitarbeiterMenuEintrag eintrag = new MitarbeiterMenuEintrag();
        assertThat(eintrag.bezeichnung()).isEqualTo("Mitarbeiter");
        assertThat(eintrag.route()).isEqualTo(MitarbeiterListeView.class.getAnnotation(Route.class).value());
        assertThat(eintrag.erlaubteRollen()).containsExactlyInAnyOrder(Rolle.PROJEKTLEITUNG, Rolle.ADMIN);
        assertThat(eintrag.reihenfolge()).isBetween(300, 399);
        assertThat(MitarbeiterListeView.class.getAnnotation(Route.class).layout()).isEqualTo(HauptLayout.class);

        ViewTestHilfe.anmelden(paul);
        MitarbeiterListeView liste = new MitarbeiterListeView(mitarbeiterService, new AktuelleSitzung());

        List<String> ueberschriften = new ArrayList<>();
        for (Grid.Column<MitarbeiterZeile> spalte : liste.tabelle.getColumns()) {
            ueberschriften.add(spalte.getHeaderText());
        }
        assertThat(ueberschriften).containsExactly("Pers.-Nr.", "Name", "Qualifikation", "Rolle", "Vorgesetzte/r", "Status");

        // Erst die aktiven, dann die inaktive Sophie (wie im Mockup)
        List<MitarbeiterZeile> zeilen = liste.tabelle.getListDataView().getItems().toList();
        assertThat(zeilen).extracting(MitarbeiterZeile::personalnummer).containsExactly("1001", "1004", "1012", "1009");
        assertThat(liste.fusszeile.getText()).isEqualTo("4 Mitarbeiter · Klick auf eine Zeile öffnet die Bearbeitung");
        assertThat(liste.neuerMitarbeiter.isVisible()).isTrue();
        assertThat(liste.neuerMitarbeiter.getText()).isEqualTo("+ Neuer Mitarbeiter");

        MitarbeiterZeile zeileJonas = zeilen.get(2);
        assertThat(liste.vorgesetzterText(zeileJonas)).isEqualTo(lena.getName());
        assertThat(liste.vorgesetzterText(zeilen.get(0))).isEqualTo("–");
        assertThat(zeileJonas.qualifikation().getAnzeigename()).isEqualTo("Azubi");
        assertThat(liste.nameAnzeige(zeileJonas).getText()).isEqualTo(jonas.getName());

        Span aktiv = liste.statusAnzeige(zeileJonas);
        assertThat(aktiv.getText()).isEqualTo("aktiv");
        assertThat(aktiv.getElement().getThemeList()).containsExactlyInAnyOrder("badge", "success");
        Span inaktiv = liste.statusAnzeige(zeilen.get(3));
        assertThat(inaktiv.getText()).isEqualTo("inaktiv seit 30.06.2026");
        assertThat(inaktiv.getElement().getThemeList()).containsExactlyInAnyOrder("badge", "contrast");
        assertThat(liste.nameAnzeige(zeilen.get(3)).getClassNames()).contains("text-secondary");
    }

    @Test
    void projektleitungOhneNeuAnlegenUndMitarbeiterOhneZugriff() {
        assertThat(MitarbeiterListeView.class.getAnnotation(RolesAllowed.class).value())
                .containsExactlyInAnyOrder("PROJEKTLEITUNG", "ADMIN");

        ViewTestHilfe.anmelden(lena);
        MitarbeiterListeView liste = new MitarbeiterListeView(mitarbeiterService, new AktuelleSitzung());
        assertThat(liste.neuerMitarbeiter.isVisible()).isFalse();
        assertThat(liste.tabelle.getListDataView().getItemCount()).isEqualTo(4);

        // Direkter Aufruf ohne Recht: Der Service verweigert, auch wenn jemand die Seite umgeht
        ViewTestHilfe.anmelden(jonas);
        assertThatThrownBy(() -> new MitarbeiterListeView(mitarbeiterService, new AktuelleSitzung()))
                .isInstanceOf(ZugriffVerweigertException.class);
    }
}
