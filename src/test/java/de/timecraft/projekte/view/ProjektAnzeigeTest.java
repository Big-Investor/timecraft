package de.timecraft.projekte.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.html.Span;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.model.ProjektStatus;
import java.time.LocalDate;
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
}
