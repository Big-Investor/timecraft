package de.timecraft.projekte.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.service.ProjektService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prüft die Projektliste ohne Browser mit einem Mockito-Service. Die View hat keine Fachregeln: Getestet wird nur,
 * dass sie die angemeldete Person und die Filter an den Service gibt und sein Ergebnis anzeigt.
 * Was welche Rolle sehen darf, prüfen die Service-Tests (ProjektServiceListeTest).
 *
 * @author Tim Stukenbröker
 */
class ProjektListeViewTest {

    // Vaadin merkt sich die aktuelle UI nur schwach: Ohne dieses Feld kann der Müllsammler sie im Test wegräumen.
    private final UI ui = new UI();

    private final ProjektService service = mock(ProjektService.class);

    @BeforeEach
    void meldeProjektleitungAn() {
        anmelden(2, "projektleitung", Rolle.PROJEKTLEITUNG);
        when(service.namenDerPersonen(anyLong(), any(), anyCollection())).thenReturn(Map.of(2L, "Lena Schröder"));
    }

    @AfterEach
    void sitzungLeeren() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
    }

    private void anmelden(long id, String name, Rolle rolle) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(id, name, "hash", rolle);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }

    private Projekt projekt(String name, ProjektStatus status) {
        Projekt projekt = new Projekt(2, name, null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 13));
        projekt.setStatus(status);
        return projekt;
    }

    private ProjektListeView offnen(List<Projekt> projekte) {
        when(service.projekteSehen(anyLong(), any(), any())).thenReturn(projekte);
        return new ProjektListeView(service, new AktuelleSitzung());
    }

    @SuppressWarnings("unchecked")
    private List<Projekt> angezeigteProjekte(ProjektListeView view) {
        Grid<Projekt> grid = Komponenten.alle(view, Grid.class).get(0);
        return grid.getListDataView().getItems().toList();
    }

    private ProjektFilter letzterFilter() {
        ArgumentCaptor<ProjektFilter> filter = ArgumentCaptor.forClass(ProjektFilter.class);
        verify(service, atLeastOnce()).projekteSehen(anyLong(), any(), filter.capture());
        return filter.getValue();
    }

    @Test
    void zeigtDieProjekteDesServicesMitAnzahl() {
        ProjektListeView view = offnen(List.of(projekt("Küche", ProjektStatus.AKTIV), projekt("Treppe", ProjektStatus.AKTIV)));

        assertThat(angezeigteProjekte(view)).extracting(Projekt::getName).containsExactly("Küche", "Treppe");
        assertThat(view.getElement().getTextRecursively()).contains("2 Projekte");
    }

    @Test
    void einzelnesProjektHeisstEinProjekt() {
        ProjektListeView view = offnen(List.of(projekt("Küche", ProjektStatus.AKTIV)));

        assertThat(view.getElement().getTextRecursively()).contains("1 Projekt").doesNotContain("1 Projekte");
    }

    @Test
    void ohneTrefferErscheintDerHinweis() {
        ProjektListeView view = offnen(List.of());

        assertThat(view.getElement().getTextRecursively()).contains("0 Projekte", "Keine Projekte gefunden");
        assertThat(Komponenten.alle(view, Grid.class).get(0).isVisible()).isFalse();
    }

    @Test
    void mitTreffernIstDerHinweisVersteckt() {
        ProjektListeView view = offnen(List.of(projekt("Küche", ProjektStatus.AKTIV)));

        assertThat(Komponenten.alle(view, Grid.class).get(0).isVisible()).isTrue();
        assertThat(Komponenten.alle(view, com.vaadin.flow.component.html.Div.class).stream()
                .filter(div -> "Keine Projekte gefunden".equals(div.getText()))
                .allMatch(div -> !div.isVisible())).isTrue();
    }

    @Test
    void gibtDieAngemeldetePersonAnDenServiceWeiter() {
        offnen(List.of());

        verify(service).projekteSehen(eq(2L), eq(Rolle.PROJEKTLEITUNG), any(ProjektFilter.class));
    }

    @Test
    void ohneEingabeIstDerFilterLeer() {
        offnen(List.of());

        ProjektFilter filter = letzterFilter();
        assertThat(filter.getName()).isNull();
        assertThat(filter.getStatus()).isNull();
        assertThat(filter.getStartVon()).isNull();
        assertThat(filter.getZielBis()).isNull();
    }

    @Test
    void alleFilterfelderWirkenGemeinsamImServiceAufruf() {
        ProjektListeView view = offnen(List.of());

        Komponenten.mitBeschriftung(view, TextField.class, "Projektname").setValue("küche");
        Select<ProjektStatus> status = Komponenten.alle(view, Select.class).get(0);
        status.setValue(ProjektStatus.ARCHIVIERT);
        List<DatePicker> daten = Komponenten.alle(view, DatePicker.class);
        daten.get(0).setValue(LocalDate.of(2026, 1, 1));
        daten.get(1).setValue(LocalDate.of(2026, 12, 31));
        daten.get(2).setValue(LocalDate.of(2026, 2, 1));
        daten.get(3).setValue(LocalDate.of(2026, 11, 30));

        ProjektFilter filter = letzterFilter();
        assertThat(filter.getName()).isEqualTo("küche");
        assertThat(filter.getStatus()).isEqualTo(ProjektStatus.ARCHIVIERT);
        assertThat(filter.getStartVon()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filter.getStartBis()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(filter.getZielVon()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(filter.getZielBis()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void statusFilterHeisstOhneAuswahlAlle() {
        ProjektListeView view = offnen(List.of());
        Select<ProjektStatus> status = Komponenten.alle(view, Select.class).get(0);

        assertThat(status.getItemLabelGenerator().apply(null)).isEqualTo("Alle");
        assertThat(status.getItemLabelGenerator().apply(ProjektStatus.AKTIV)).isEqualTo("Aktiv");
        assertThat(status.getItemLabelGenerator().apply(ProjektStatus.ARCHIVIERT)).isEqualTo("Archiviert");
    }

    @Test
    void zuruecksetzenLeertAlleFelderUndLaedtOhneFilter() {
        ProjektListeView view = offnen(List.of());
        TextField name = Komponenten.mitBeschriftung(view, TextField.class, "Projektname");
        Select<ProjektStatus> status = Komponenten.alle(view, Select.class).get(0);
        name.setValue("küche");
        status.setValue(ProjektStatus.AKTIV);
        Komponenten.alle(view, DatePicker.class).get(0).setValue(LocalDate.of(2026, 1, 1));

        Komponenten.alle(view, Button.class).stream()
                .filter(knopf -> "Filter zurücksetzen".equals(knopf.getText()))
                .findFirst().orElseThrow().click();

        assertThat(name.isEmpty()).isTrue();
        assertThat(status.isEmpty()).isTrue();
        assertThat(Komponenten.alle(view, DatePicker.class)).allMatch(DatePicker::isEmpty);
        ProjektFilter filter = letzterFilter();
        assertThat(filter.getName()).isNull();
        assertThat(filter.getStatus()).isNull();
        assertThat(filter.getStartVon()).isNull();
    }

    @Test
    void nurDerAdminSiehtDenKnopfNeuesProjekt() {
        assertThat(knoepfe(offnen(List.of()))).doesNotContain("+ Neues Projekt");

        anmelden(3, "admin", Rolle.ADMIN);

        assertThat(knoepfe(offnen(List.of()))).contains("+ Neues Projekt");
    }

    @Test
    void neuesProjektOeffnetDasFormular() {
        anmelden(3, "admin", Rolle.ADMIN);
        UI.setCurrent(ui);
        ProjektListeView view = offnen(List.of());

        Komponenten.alle(view, Button.class).stream().filter(k -> "+ Neues Projekt".equals(k.getText()))
                .findFirst().orElseThrow().click();

        verify(service).projektleitungenZurAuswahl(3L, Rolle.ADMIN);
    }

    private List<String> knoepfe(ProjektListeView view) {
        return Komponenten.alle(view, Button.class).stream().map(Button::getText).toList();
    }
}
