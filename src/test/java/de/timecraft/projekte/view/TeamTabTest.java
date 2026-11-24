package de.timecraft.projekte.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.TeamEintrag;
import de.timecraft.projekte.model.TeamFilter;
import de.timecraft.projekte.service.ProjektService;
import de.timecraft.projekte.service.TeamService;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prüft den Reiter "Team" ohne Browser mit Mockito-Services: Spalten und Schaltflächen je Ansicht, Filter,
 * Leerzustände, Entfernen mit Bestätigung. Wer was sehen darf, prüfen die Service-Tests (TeamServiceTest).
 * Das Projekt 10 gehört der Projektleitung 2.
 *
 * @author Tim Stukenbröker
 */
class TeamTabTest {

    // Vaadin merkt sich die aktuelle UI nur schwach: Ohne dieses Feld kann der Müllsammler sie im Test wegräumen.
    private UI ui;

    private static final long PROJEKT_ID = 10L;

    private final TeamService teamService = mock(TeamService.class);
    private final ProjektService projektService = mock(ProjektService.class);

    private final TeamEintrag jonas = new TeamEintrag(1, "Jonas Becker", true, Standort.UNTERWEGS,
            Instant.parse("2026-11-18T06:02:00Z"));
    private final TeamEintrag anna = new TeamEintrag(6, "Anna Vogt", false, null, null);

    @BeforeEach
    void meldeProjektleitungAn() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        ui = new UI();
        UI.setCurrent(ui);
        when(teamService.teamAnsehen(anyLong(), any(), anyLong(), any())).thenReturn(List.of(jonas, anna));
    }

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
    }

    private void anmelden(long id, Rolle rolle) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(id, "benutzer", "hash", rolle);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }

    private TeamTab tab(boolean vollansicht) {
        return new TeamTab(teamService, projektService, new AktuelleSitzung(), FesteUhr.UHR, PROJEKT_ID, vollansicht);
    }

    @SuppressWarnings("unchecked")
    private Grid<TeamEintrag> grid(TeamTab tab) {
        return Komponenten.alle(tab, Grid.class).get(0);
    }

    private List<String> spaltenTitel(TeamTab tab) {
        return grid(tab).getColumns().stream().map(spalte -> spalte.getHeaderText()).toList();
    }

    private Button knopf(TeamTab tab, String text) {
        return Komponenten.alle(tab, Button.class).stream().filter(k -> text.equals(k.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("Kein Knopf " + text));
    }

    private TeamFilter letzterFilter() {
        ArgumentCaptor<TeamFilter> filter = ArgumentCaptor.forClass(TeamFilter.class);
        verify(teamService, atLeastOnce()).teamAnsehen(anyLong(), any(), anyLong(), filter.capture());
        return filter.getValue();
    }

    // ---- Vollansicht der verantwortlichen Projektleitung ----

    @Test
    void vollansichtZeigtNameStatusStandortBeginnUndEntfernen() {
        TeamTab tab = tab(true);

        assertThat(spaltenTitel(tab)).containsExactly("Name", "Status", "Standort", "Beginn der Buchung", "");
        assertThat(grid(tab).getListDataView().getItems().toList()).containsExactly(jonas, anna);
    }

    @Test
    void vollansichtHatFilterAktualisierenUndZuordnen() {
        TeamTab tab = tab(true);

        assertThat(Komponenten.alle(tab, Select.class)).hasSize(2);
        assertThat(knopf(tab, "Aktualisieren")).isNotNull();
        assertThat(knopf(tab, "+ Mitarbeiter zuordnen")).isNotNull();
    }

    @Test
    void gibtDiePersonUndDasProjektAnDenServiceWeiter() {
        tab(true);

        verify(teamService).teamAnsehen(eq(2L), eq(Rolle.PROJEKTLEITUNG), eq(PROJEKT_ID), any(TeamFilter.class));
    }

    @Test
    void statusUndStandortFilterWirkenGemeinsam() {
        TeamTab tab = tab(true);
        List<Select> filter = Komponenten.alle(tab, Select.class);

        filter.get(0).setValue(true);
        filter.get(1).setValue(Standort.UNTERWEGS);

        assertThat(letzterFilter().getEingestempelt()).isTrue();
        assertThat(letzterFilter().getStandort()).isEqualTo(Standort.UNTERWEGS);
    }

    @Test
    void aktualisierenLaedtDenServiceNochEinmal() {
        TeamTab tab = tab(true);

        knopf(tab, "Aktualisieren").click();

        verify(teamService, times(2)).teamAnsehen(anyLong(), any(), anyLong(), any());
    }

    @Test
    void leeresTeamZeigtDenHinweisAusDemDokument() {
        when(teamService.teamAnsehen(anyLong(), any(), anyLong(), any())).thenReturn(List.of());

        TeamTab tab = tab(true);

        assertThat(tab.getElement().getTextRecursively()).contains("Dem Projekt sind noch keine Mitarbeiter zugeordnet");
        assertThat(grid(tab).isVisible()).isFalse();
    }

    @Test
    void keinTrefferBeimFilternZeigtKeinePersonGefunden() {
        TeamTab tab = tab(true);
        when(teamService.teamAnsehen(anyLong(), any(), anyLong(), any())).thenReturn(List.of());

        Komponenten.alle(tab, Select.class).get(1).setValue(Standort.BUERO);

        assertThat(tab.getElement().getTextRecursively()).contains("Keine Person gefunden");
    }

    // ---- Entfernen ----

    @Test
    void jedeZeileHatEineEntfernenSchaltflaeche() {
        TeamTab tab = tab(true);

        @SuppressWarnings("unchecked")
        ComponentRenderer<Button, TeamEintrag> renderer =
                (ComponentRenderer<Button, TeamEintrag>) grid(tab).getColumns().get(4).getRenderer();

        assertThat(renderer.createComponent(anna).getText()).isEqualTo("Entfernen");
    }

    @Test
    void entfernenFragtNachUndWarntBeiEingestempeltenPersonen() {
        when(teamService.istEingestempelt(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID, 1)).thenReturn(true);
        TeamTab tab = tab(true);

        ConfirmDialog dialog = tab.entfernenDialog(jonas);

        assertThat(dialog.getElement().getProperty("header")).isEqualTo("Jonas Becker aus dem Projekt entfernen?");
        assertThat(dialog.getElement().getProperty("message"))
                .contains("Jonas ist gerade auf dieses Projekt eingestempelt")
                .contains("bisherige Buchungen bleiben erhalten");
        verify(projektService, never()).entfernen(anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    void entfernenOhneLaufendeBuchungHatKeinenEingestempeltHinweis() {
        TeamTab tab = tab(true);

        assertThat(tab.entfernenDialog(anna).getElement().getProperty("message"))
                .isEqualTo("Bisherige Buchungen bleiben erhalten.");
    }

    @Test
    void erstNachDerBestaetigungWirdEntfernt() {
        TeamTab tab = tab(true);
        ConfirmDialog dialog = tab.entfernenDialog(anna);

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.ConfirmEvent(dialog, false));

        verify(projektService).entfernen(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID, 6);
        verify(teamService, times(2)).teamAnsehen(anyLong(), any(), anyLong(), any());
    }

    @Test
    void abbrechenEntferntNichts() {
        TeamTab tab = tab(true);
        ConfirmDialog dialog = tab.entfernenDialog(anna);

        ComponentUtil.fireEvent(dialog, new ConfirmDialog.CancelEvent(dialog, false));

        verify(projektService, never()).entfernen(anyLong(), any(), anyLong(), anyLong());
    }

    // ---- Ansicht des zugeordneten Mitarbeiters ----

    @Test
    void mitarbeiterSiehtNurDieNamenOhneFilterUndSchaltflaechen() {
        anmelden(1, Rolle.MITARBEITER);
        when(teamService.teamAnsehen(anyLong(), any(), anyLong(), any()))
                .thenReturn(List.of(new TeamEintrag(1, "Jonas Becker", null, null, null)));

        TeamTab tab = tab(false);

        assertThat(spaltenTitel(tab)).containsExactly("Name");
        assertThat(Komponenten.alle(tab, Select.class)).isEmpty();
        assertThat(Komponenten.alle(tab, Button.class)).isEmpty();
    }

    // ---- Fehler ----

    @Test
    void verweigertDerServiceDenZugriffStuerztDieAnzeigeNichtAb() {
        when(teamService.teamAnsehen(anyLong(), any(), anyLong(), any()))
                .thenThrow(new ZugriffVerweigertException("Kein Zugriff."));

        TeamTab tab = tab(true);

        // Es stürzt nichts ab, und es steht kein falscher Hinweis "leeres Team" in der Anzeige.
        assertThat(tab.getElement().getTextRecursively()).doesNotContain("Dem Projekt sind noch keine Mitarbeiter");
    }
}
