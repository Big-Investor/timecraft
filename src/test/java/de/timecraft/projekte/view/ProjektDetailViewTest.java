package de.timecraft.projekte.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.RouteParameters;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektSicht;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.service.ProjektService;
import de.timecraft.projekte.service.TeamService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Prüft das Detailfenster ohne Browser: Die View zeigt Reiter und Schaltflächen genau so, wie es die
 * {@link ProjektSicht} aus dem Service sagt, und leitet bei fehlendem Zugriff zur Liste zurück.
 * Was wer darf, prüft ProjektServiceSichtTest. Das Projekt 10 heißt "Website", Projektleitung ist Lena Schröder.
 */
class ProjektDetailViewTest {

    private static final long PROJEKT_ID = 10L;

    // Vaadin merkt sich die aktuelle UI nur schwach: Ohne dieses Feld kann der Müllsammler sie im Test wegräumen.
    private UI ui;

    private final ProjektService projektService = mock(ProjektService.class);
    private final TeamService teamService = mock(TeamService.class);
    private Projekt projekt;

    @BeforeEach
    void bereiteVor() {
        ui = new UI();
        UI.setCurrent(ui);
        // Ein noch nicht gespeichertes Projekt hat keine ID; hier bekommt es die ID 10.
        projekt = spy(new Projekt(2, "Website", "Neuer Auftritt", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 13)));
        doReturn(PROJEKT_ID).when(projekt).getProjektId();
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

    private void sicht(boolean team, boolean vollansicht, boolean beschreibung, boolean admin, boolean material) {
        when(projektService.projektSehen(anyLong(), any(), anyLong())).thenReturn(
                new ProjektSicht(projekt, "Lena Schröder", team, vollansicht, beschreibung, admin, material));
    }

    private BeforeEnterEvent ereignis(String projektId) {
        BeforeEnterEvent ereignis = mock(BeforeEnterEvent.class);
        when(ereignis.getRouteParameters()).thenReturn(new RouteParameters("projektId", projektId));
        return ereignis;
    }

    private ProjektDetailView oeffnen() {
        ProjektDetailView view = new ProjektDetailView(projektService, teamService, new AktuelleSitzung(), FesteUhr.UHR);
        view.beforeEnter(ereignis(String.valueOf(PROJEKT_ID)));
        return view;
    }

    private List<String> reiter(ProjektDetailView view) {
        return Komponenten.alle(view, Tab.class).stream().map(Tab::getLabel).toList();
    }

    private List<String> knoepfe(ProjektDetailView view) {
        return Komponenten.alle(view, Button.class).stream().map(Button::getText).toList();
    }

    // ---- Kopf ----

    @Test
    void zeigtPfadTitelUndKopfdatenWieImMockup() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);

        String text = oeffnen().getElement().getTextRecursively();

        assertThat(text).contains("Projekte", "P-10", "Website", "Projekt-ID", "Zeitraum", "05.10. – 13.11.2026",
                "Projektleitung", "Lena Schröder", "aktiv");
    }

    // ---- Reiter je Sicht ----

    @Test
    void verantwortlicheProjektleitungHatDetailsTeamUndMaterialplanung() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);

        assertThat(reiter(oeffnen())).containsExactly("Details", "Team", "Materialplanung");
    }

    @Test
    void zugeordneterMitarbeiterHatNurDetailsUndTeam() {
        anmelden(1, Rolle.MITARBEITER);
        sicht(true, false, false, false, false);

        assertThat(reiter(oeffnen())).containsExactly("Details", "Team");
    }

    @Test
    void adminHatKeinenReiterTeamAberMaterialplanung() {
        anmelden(3, Rolle.ADMIN);
        sicht(false, false, false, true, true);

        assertThat(reiter(oeffnen())).containsExactly("Details", "Materialplanung");
    }

    @Test
    void fremdeProjektleitungHatKeinenReiterTeam() {
        anmelden(7, Rolle.PROJEKTLEITUNG);
        sicht(false, false, false, false, true);

        assertThat(reiter(oeffnen())).containsExactly("Details", "Materialplanung");
    }

    @Test
    void materialplanungVerweistAufDieSeiteVonM4() {
        assertThat(String.format(ProjektDetailView.MATERIAL_ROUTE, 5)).isEqualTo("projekt/5/material");
    }

    @Test
    void einKlickAufTeamZeigtDenReiterTeam() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);
        ProjektDetailView view = oeffnen();
        Tabs tabs = Komponenten.alle(view, Tabs.class).get(0);

        tabs.setSelectedTab(Komponenten.alle(view, Tab.class).get(1));

        assertThat(Komponenten.alle(view, TeamTab.class)).hasSize(1);
    }

    @Test
    void imReiterDetailsGibtEsKeinTeamTab() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);

        assertThat(Komponenten.alle(oeffnen(), TeamTab.class)).isEmpty();
    }

    // ---- Reiter Details ----

    @Test
    void detailsZeigenAlleFelderDesProjekts() {
        anmelden(1, Rolle.MITARBEITER);
        sicht(true, false, false, false, false);

        String text = oeffnen().getElement().getTextRecursively();

        assertThat(text).contains("Projekt-ID", "Name", "Beschreibung", "Neuer Auftritt", "Startdatum", "05.10.2026",
                "Zieldatum", "13.11.2026", "Status", "Projektleitung");
        assertThat(Komponenten.alle(oeffnen(), TextArea.class)).isEmpty();
        assertThat(knoepfe(oeffnen())).isEmpty();
    }

    @Test
    void verantwortlicheProjektleitungBearbeitetNurDieBeschreibung() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);
        ProjektDetailView view = oeffnen();

        assertThat(Komponenten.alle(view, TextArea.class)).hasSize(1);
        assertThat(knoepfe(view)).containsExactly("Beschreibung speichern");
    }

    @Test
    void beschreibungSpeichernRuftDenServiceAuf() {
        anmelden(2, Rolle.PROJEKTLEITUNG);
        sicht(true, true, true, false, true);
        ProjektDetailView view = oeffnen();
        Komponenten.alle(view, TextArea.class).get(0).setValue("Neue Beschreibung");

        Komponenten.alle(view, Button.class).get(0).click();

        verify(projektService).beschreibungAendern(2, Rolle.PROJEKTLEITUNG, PROJEKT_ID, "Neue Beschreibung");
    }

    @Test
    void adminSiehtBearbeitenUndArchivierenBeiAktivemProjekt() {
        anmelden(3, Rolle.ADMIN);
        sicht(false, false, false, true, true);

        assertThat(knoepfe(oeffnen())).containsExactly("Bearbeiten", "Projekt archivieren");
    }

    @Test
    void adminSiehtBeiArchiviertemProjektReaktivieren() {
        anmelden(3, Rolle.ADMIN);
        projekt.setStatus(ProjektStatus.ARCHIVIERT);
        sicht(false, false, false, true, true);

        assertThat(knoepfe(oeffnen())).containsExactly("Bearbeiten", "Projekt reaktivieren");
    }

    @Test
    void reaktivierenRuftDenServiceAufUndLaedtNeu() {
        anmelden(3, Rolle.ADMIN);
        projekt.setStatus(ProjektStatus.ARCHIVIERT);
        sicht(false, false, false, true, true);
        ProjektDetailView view = oeffnen();

        Komponenten.alle(view, Button.class).stream().filter(k -> "Projekt reaktivieren".equals(k.getText()))
                .findFirst().orElseThrow().click();

        verify(projektService).reaktivieren(3, Rolle.ADMIN, PROJEKT_ID);
        verify(projektService, org.mockito.Mockito.times(2)).projektSehen(3, Rolle.ADMIN, PROJEKT_ID);
    }

    @Test
    void archivierenWirdErstNachRueckfrageAusgefuehrt() {
        anmelden(3, Rolle.ADMIN);
        sicht(false, false, false, true, true);
        ProjektDetailView view = oeffnen();

        Komponenten.alle(view, Button.class).stream().filter(k -> "Projekt archivieren".equals(k.getText()))
                .findFirst().orElseThrow().click();

        verify(projektService).laufendeBuchungenAnzahl(3, Rolle.ADMIN, PROJEKT_ID);
        verify(projektService, never()).archivieren(anyLong(), any(), anyLong());
    }

    // ---- Kein Zugriff und ungültige Adresse ----

    @Test
    void ohneZugriffGehtEsZurueckZurProjektliste() {
        anmelden(5, Rolle.MITARBEITER);
        when(projektService.projektSehen(anyLong(), any(), anyLong()))
                .thenThrow(new ZugriffVerweigertException("Sie haben keinen Zugriff auf dieses Projekt."));
        BeforeEnterEvent ereignis = ereignis(String.valueOf(PROJEKT_ID));

        new ProjektDetailView(projektService, teamService, new AktuelleSitzung(), FesteUhr.UHR).beforeEnter(ereignis);

        verify(ereignis).forwardTo(ProjektListeView.class);
    }

    @Test
    void ungueltigeAdresseGehtZurueckZurProjektlisteOhneServiceAufruf() {
        anmelden(3, Rolle.ADMIN);
        BeforeEnterEvent ereignis = ereignis("abc");

        new ProjektDetailView(projektService, teamService, new AktuelleSitzung(), FesteUhr.UHR).beforeEnter(ereignis);

        verify(ereignis).forwardTo(ProjektListeView.class);
        verify(projektService, never()).projektSehen(anyLong(), any(), anyLong());
    }
}
