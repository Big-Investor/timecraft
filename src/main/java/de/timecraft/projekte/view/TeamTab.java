package de.timecraft.projekte.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.projekte.model.TeamEintrag;
import de.timecraft.projekte.model.TeamFilter;
import de.timecraft.projekte.service.ProjektService;
import de.timecraft.projekte.service.TeamService;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Clock;
import java.util.List;

/**
 * Der Reiter "Team" im Detailfenster eines Projekts (UC-M3-07, UC-M3-08).
 * <ul>
 *   <li>Vollansicht (verantwortliche Projektleitung): Name, Status, Standort, Beginn der Buchung, Filter nach
 *       Status und Standort, "Aktualisieren", "+ Mitarbeiter zuordnen" und "Entfernen" je Person.</li>
 *   <li>Sonst (zugeordneter Mitarbeiter): nur die Namen des Teams.</li>
 * </ul>
 * Wer was sehen darf, entscheidet der {@link TeamService}; ob die Vollansicht gezeigt wird, sagt die
 * {@code ProjektSicht} aus dem ProjektService. Die Anzeige zeigt den Stand beim Aufruf.
 */
public class TeamTab extends Div {

    private final TeamService teamService;
    private final ProjektService projektService;
    private final AktuelleSitzung sitzung;
    private final Clock uhr;
    private final long projektId;
    private final boolean vollansicht;

    private final Select<Boolean> statusFilter = new Select<>();
    private final Select<Standort> standortFilter = new Select<>();
    private final Grid<TeamEintrag> liste = new Grid<>();
    private final Div hinweis = new Div();

    public TeamTab(TeamService teamService, ProjektService projektService, AktuelleSitzung sitzung, Clock uhr,
            long projektId, boolean vollansicht) {
        this.teamService = teamService;
        this.projektService = projektService;
        this.sitzung = sitzung;
        this.uhr = uhr;
        this.projektId = projektId;
        this.vollansicht = vollansicht;

        if (vollansicht) {
            add(werkzeugleiste());
        }
        add(tabellenKarte());
        aktualisieren();
    }

    private HorizontalLayout werkzeugleiste() {
        statusFilter.setItems(true, false);
        statusFilter.setItemLabelGenerator(wert -> wert == null ? "Alle" : wert ? "eingestempelt" : "nicht eingestempelt");
        standortFilter.setItems(Standort.values());
        standortFilter.setItemLabelGenerator(wert -> wert == null ? "Alle" : ProjektAnzeige.standort(wert));
        for (Select<?> filter : List.of(statusFilter, standortFilter)) {
            filter.setEmptySelectionAllowed(true);
            filter.setEmptySelectionCaption("Alle");
            filter.addValueChangeListener(ereignis -> aktualisieren());
        }
        statusFilter.setAriaLabel("Status");
        standortFilter.setAriaLabel("Standort");

        Button aktualisieren = new Button("Aktualisieren", ereignis -> aktualisieren());
        Button zuordnen = new Button("+ Mitarbeiter zuordnen", ereignis -> zuordnen());
        zuordnen.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout links = new HorizontalLayout(ProjektAnzeige.feldMitTitel("Status", statusFilter),
                ProjektAnzeige.feldMitTitel("Standort", standortFilter), aktualisieren);
        links.setAlignItems(FlexComponent.Alignment.END);
        HorizontalLayout leiste = new HorizontalLayout(links, zuordnen);
        leiste.setWidthFull();
        leiste.setAlignItems(FlexComponent.Alignment.END);
        leiste.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        leiste.getStyle().set("margin-bottom", "var(--lumo-space-m)");
        return leiste;
    }

    private Div tabellenKarte() {
        liste.addComponentColumn(this::namensZelle).setHeader("Name").setFlexGrow(2);
        if (vollansicht) {
            liste.addComponentColumn(eintrag -> ProjektAnzeige.eingestempeltMarke(Boolean.TRUE.equals(eintrag.eingestempelt())))
                    .setHeader("Status").setFlexGrow(2);
            liste.addColumn(eintrag -> eintrag.standort() == null ? "–" : ProjektAnzeige.standort(eintrag.standort()))
                    .setHeader("Standort").setFlexGrow(1);
            liste.addColumn(eintrag -> eintrag.beginn() == null ? "–" : ProjektAnzeige.uhrzeit(eintrag.beginn(), uhr.getZone()))
                    .setHeader("Beginn der Buchung").setFlexGrow(1);
            liste.addComponentColumn(this::entfernenKnopf).setHeader("").setFlexGrow(0);
        }
        liste.setAllRowsVisible(true);
        liste.getStyle().set("margin-top", "0");

        hinweis.getStyle().set("padding", "1.5rem").set("color", "var(--lumo-secondary-text-color)");
        Div karte = new Div(liste, hinweis);
        karte.addClassName("karte");
        karte.getStyle().set("margin", "0").set("padding", "0").set("overflow", "hidden");
        return karte;
    }

    /** Der Name steht in Fett, wie im Mockup. */
    private Span namensZelle(TeamEintrag eintrag) {
        Span name = new Span(eintrag.name());
        name.getStyle().set("font-weight", "700");
        return name;
    }

    private Button entfernenKnopf(TeamEintrag eintrag) {
        Button knopf = new Button("Entfernen", ereignis -> entfernenBestaetigen(eintrag));
        knopf.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return knopf;
    }

    /** Lädt das Team neu (Stand jetzt). Auch für "Aktualisieren" und nach dem Zuordnen oder Entfernen. */
    public void aktualisieren() {
        try {
            TeamFilter filter = aktuellerFilter();
            List<TeamEintrag> team = teamService.teamAnsehen(sitzung.mitarbeiterId(), sitzung.rolle(), projektId, filter);
            liste.setItems(team);
            liste.setVisible(!team.isEmpty());
            hinweis.setVisible(team.isEmpty());
            boolean gefiltert = filter.getEingestempelt() != null || filter.getStandort() != null;
            hinweis.setText(gefiltert ? "Keine Person gefunden"
                    : "Dem Projekt sind noch keine Mitarbeiter zugeordnet");
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private TeamFilter aktuellerFilter() {
        TeamFilter filter = new TeamFilter();
        filter.setEingestempelt(statusFilter.getValue());
        filter.setStandort(standortFilter.getValue());
        return filter;
    }

    private void zuordnen() {
        new ZuordnungsDialog(projektService, sitzung, projektId, this::aktualisieren).open();
    }

    private void entfernenBestaetigen(TeamEintrag eintrag) {
        try {
            entfernenDialog(eintrag).open();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /**
     * Baut die Rückfrage vor dem Entfernen. Ist die Person gerade eingestempelt, steht ein Hinweis dabei
     * (US-M3-11). Erst "Entfernen" im Dialog entfernt wirklich.
     */
    ConfirmDialog entfernenDialog(TeamEintrag eintrag) {
        boolean eingestempelt = teamService.istEingestempelt(
                sitzung.mitarbeiterId(), sitzung.rolle(), projektId, eintrag.mitarbeiterId());
        String vorname = eintrag.name().split(" ")[0];
        String text = eingestempelt
                ? vorname + " ist gerade auf dieses Projekt eingestempelt. Die laufende Buchung bleibt bis zum "
                        + "Ausstempeln gültig, bisherige Buchungen bleiben erhalten."
                : "Bisherige Buchungen bleiben erhalten.";

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(eintrag.name() + " aus dem Projekt entfernen?");
        dialog.setText(text);
        dialog.setCancelable(true);
        dialog.setCancelText("Abbrechen");
        dialog.setConfirmText("Entfernen");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(ereignis -> entfernen(eintrag));
        return dialog;
    }

    private void entfernen(TeamEintrag eintrag) {
        try {
            projektService.entfernen(sitzung.mitarbeiterId(), sitzung.rolle(), projektId, eintrag.mitarbeiterId());
            Meldungen.erfolg(eintrag.name() + " wurde aus dem Projekt entfernt.");
            aktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
