package de.timecraft.projekte.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Prüft die kleinen Anzeigehilfen und den Menüpunkt der Projektliste. */
class ProjektAnzeigeTest {

    @Test
    void projektIdWirdMitPraefixAngezeigt() {
        assertThat(ProjektAnzeige.id(1024)).isEqualTo("P-1024");
    }

    @Test
    void datumWirdAlsTagMonatJahrAngezeigt() {
        assertThat(ProjektAnzeige.datum(LocalDate.of(2026, 10, 5))).isEqualTo("05.10.2026");
        assertThat(ProjektAnzeige.datum(null)).isEmpty();
    }

    @Test
    void statusTextIstKleingeschrieben() {
        assertThat(ProjektAnzeige.status(ProjektStatus.AKTIV)).isEqualTo("aktiv");
        assertThat(ProjektAnzeige.status(ProjektStatus.ARCHIVIERT)).isEqualTo("archiviert");
    }

    @Test
    void aktivIstGruenArchiviertIstGrau() {
        Span aktiv = ProjektAnzeige.statusMarke(ProjektStatus.AKTIV);
        Span archiviert = ProjektAnzeige.statusMarke(ProjektStatus.ARCHIVIERT);

        assertThat(aktiv.getText()).isEqualTo("aktiv");
        assertThat(aktiv.getElement().getThemeList()).contains("badge", "success");
        assertThat(archiviert.getText()).isEqualTo("archiviert");
        assertThat(archiviert.getElement().getThemeList()).contains("badge", "contrast").doesNotContain("success");
    }

    @Test
    void menuePunktProjekteIstFuerAlleRollenInDerGruppeBetrieb() {
        ProjektMenuEintrag eintrag = new ProjektMenuEintrag();

        assertThat(eintrag.bezeichnung()).isEqualTo("Projekte");
        assertThat(eintrag.route()).isEqualTo("projekte");
        assertThat(eintrag.erlaubteRollen()).containsExactlyInAnyOrder(Rolle.values());
        assertThat(eintrag.reihenfolge()).isBetween(200, 299);
    }

    @Test
    void zeitraumImSelbenJahrOhneStartjahr() {
        assertThat(ProjektAnzeige.zeitraum(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 13)))
                .isEqualTo("05.10. – 13.11.2026");
    }

    @Test
    void zeitraumUeberDenJahreswechselMitBeidenJahren() {
        assertThat(ProjektAnzeige.zeitraum(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 28)))
                .isEqualTo("01.12.2026 – 28.02.2027");
    }

    @Test
    void standortTexteWieImMockup() {
        assertThat(ProjektAnzeige.standort(Standort.BUERO)).isEqualTo("Büro");
        assertThat(ProjektAnzeige.standort(Standort.HOMEOFFICE)).isEqualTo("Homeoffice");
        assertThat(ProjektAnzeige.standort(Standort.UNTERWEGS)).isEqualTo("unterwegs");
    }

    @Test
    void uhrzeitInDerZeitzoneBerlin() {
        // 06:02 UTC ist im November (Winterzeit) 07:02 in Berlin.
        assertThat(ProjektAnzeige.uhrzeit(Instant.parse("2026-11-18T06:02:00Z"), ZoneId.of("Europe/Berlin")))
                .isEqualTo("07:02");
    }

    @Test
    void eingestempeltMarkeIstGruenNichtEingestempeltGrau() {
        assertThat(ProjektAnzeige.eingestempeltMarke(true).getText()).isEqualTo("eingestempelt");
        assertThat(ProjektAnzeige.eingestempeltMarke(true).getElement().getThemeList()).contains("success");
        assertThat(ProjektAnzeige.eingestempeltMarke(false).getText()).isEqualTo("nicht eingestempelt");
        assertThat(ProjektAnzeige.eingestempeltMarke(false).getElement().getThemeList()).contains("contrast");
    }

    @Test
    void linkNavigiertOhneNeuladenAufDieAdresseDesProjekts() {
        Anchor link = ProjektAnzeige.link(ProjektAnzeige.detailRoute(5), "Küche");

        assertThat(link.getHref()).isEqualTo("projekt/5");
        assertThat(link.getText()).isEqualTo("Küche");
        assertThat(link.getElement().hasAttribute("router-link")).isTrue();
    }
}
