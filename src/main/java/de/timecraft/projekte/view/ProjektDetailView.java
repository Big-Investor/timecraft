package de.timecraft.projekte.view;


import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektSicht;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.service.ProjektService;
import de.timecraft.projekte.service.TeamService;
import jakarta.annotation.security.PermitAll;
import java.time.Clock;
import java.util.NoSuchElementException;

/**
 * Das Detailfenster eines Projekts (FA-M3-05) mit den Reitern "Details", "Team" und "Materialplanung".
 * Welche Reiter und Schaltflächen es gibt, sagt die {@link ProjektSicht} aus dem ProjektService; die View hat
 * keine eigenen Rechteregeln. Darf die Person das Projekt nicht sehen (auch beim Aufruf per Adresse), zeigt
 * sie die Meldung des Service und geht zurück zur Projektliste.
 * <p>
 * Der Reiter "Materialplanung" hat keinen eigenen Inhalt: Er verweist auf die Seite von Modul M4.
 * Die Kopfzeile des Gerüsts zeigt den festen Seitentitel; Projektname und Pfad stehen deshalb im Inhalt.
 */
@Route(value = ProjektDetailView.ROUTE, layout = HauptLayout.class)
@PageTitle("Projekt")
@PermitAll
public class ProjektDetailView extends Div implements BeforeEnterObserver {

    static final String ROUTE = "projekt/:projektId";

    /** Adresse der Materialplanung (Seite von M4), "%d" ist die Projekt-ID. M3 hat keine Abhängigkeit auf M4. */
    static final String MATERIAL_ROUTE = "projekt/%d/material";

    private final ProjektService projektService;
    private final TeamService teamService;
    private final AktuelleSitzung sitzung;
    private final Clock uhr;

    private long projektId;
    private ProjektSicht sicht;
    private final Div inhalt = new Div();

    public ProjektDetailView(ProjektService projektService, TeamService teamService, AktuelleSitzung sitzung,
            Clock uhr) {
        this.projektService = projektService;
        this.teamService = teamService;
        this.sitzung = sitzung;
        this.uhr = uhr;
        getStyle().set("padding", "var(--lumo-space-l)");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent ereignis) {
        try {
            projektId = projektIdAusAdresse(ereignis);
            sichtLaden();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            ereignis.forwardTo(ProjektListeView.class);
        }
    }

    /** Eine ungültige Adresse (keine Zahl) führt wie ein unbekanntes Projekt zurück zur Liste. */
    private long projektIdAusAdresse(BeforeEnterEvent ereignis) {
        try {
            return ereignis.getRouteParameters().getLong("projektId").orElseThrow();
        } catch (IllegalArgumentException | NoSuchElementException fehler) {
            throw new NichtGefundenException("Das Projekt wurde nicht gefunden.");
        }
    }

    private void sichtLaden() {
        sicht = projektService.projektSehen(sitzung.mitarbeiterId(), sitzung.rolle(), projektId);
        aufbauen();
    }

    /** Lädt das Projekt neu und baut das Fenster wieder auf, z. B. nach dem Bearbeiten oder Archivieren. */
    private void neuLaden() {
        try {
            sichtLaden();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            UI.getCurrent().navigate(ProjektListeView.class);
        }
    }

    private void aufbauen() {
        Projekt projekt = sicht.projekt();
        removeAll();
        add(pfad(projekt), new H2(projekt.getName()), kopfdaten(projekt), reiter(), inhalt);
    }

    private Div pfad(Projekt projekt) {
        Div pfad = new Div(ProjektAnzeige.link(ProjektListeView.ROUTE, "Projekte"),
                new Span(" / " + ProjektAnzeige.id(projekt.getProjektId())));
        pfad.getStyle().set("font-size", "var(--lumo-font-size-s)");
        return pfad;
    }

    /** Die Zeile unter dem Titel: Projekt-ID, Zeitraum, Projektleitung und Status. */
    private HorizontalLayout kopfdaten(Projekt projekt) {
        HorizontalLayout zeile = new HorizontalLayout(
                beschriftet("Projekt-ID", ProjektAnzeige.id(projekt.getProjektId())),
                beschriftet("Zeitraum", ProjektAnzeige.zeitraum(projekt.getStartdatum(), projekt.getZieldatum())),
                beschriftet("Projektleitung", sicht.projektleitungName()),
                ProjektAnzeige.statusMarke(projekt.getStatus()));
        zeile.setWrap(true);
        zeile.setAlignItems(FlexComponent.Alignment.CENTER);
        return zeile;
    }

    private Span beschriftet(String beschriftung, String wert) {
        Span titel = new Span(beschriftung);
        titel.getStyle().set("font-weight", "700");
        return new Span(titel, new Span(" " + wert));
    }

    // ---- Reiter ----

    private Tabs reiter() {
        Tab details = new Tab("Details");
        Tabs reiter = new Tabs(details);
        Tab team = sicht.teamSichtbar() ? new Tab("Team") : null;
        Tab material = sicht.materialplanungSichtbar() ? new Tab("Materialplanung") : null;
        if (team != null) {
            reiter.add(team);
        }
        if (material != null) {
            reiter.add(material);
        }
        reiter.addSelectedChangeListener(ereignis -> {
            Tab gewaehlt = reiter.getSelectedTab();
            if (gewaehlt == material) {
                UI.getCurrent().navigate(String.format(MATERIAL_ROUTE, projektId));
            } else if (gewaehlt == team) {
                zeige(new TeamTab(teamService, projektService, sitzung, uhr, projektId, sicht.teamVollansicht()));
            } else {
                zeige(detailsInhalt());
            }
        });
        zeige(detailsInhalt());
        return reiter;
    }

    private void zeige(Component neuerInhalt) {
        inhalt.removeAll();
        inhalt.add(neuerInhalt);
    }

    // ---- Reiter "Details" ----

    private Div detailsInhalt() {
        Projekt projekt = sicht.projekt();
        FormLayout felder = new FormLayout();
        felder.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        felder.addFormItem(new Span(ProjektAnzeige.id(projekt.getProjektId())), "Projekt-ID");
        felder.addFormItem(new Span(projekt.getName()), "Name");
        felder.addFormItem(beschreibungsFeld(projekt), "Beschreibung");
        felder.addFormItem(new Span(ProjektAnzeige.datum(projekt.getStartdatum())), "Startdatum");
        felder.addFormItem(new Span(ProjektAnzeige.datum(projekt.getZieldatum())), "Zieldatum");
        felder.addFormItem(ProjektAnzeige.statusMarke(projekt.getStatus()), "Status");
        felder.addFormItem(new Span(sicht.projektleitungName()), "Projektleitung");

        Div karte = new Div(felder);
        karte.addClassName("karte");
        karte.getStyle().set("margin", "var(--lumo-space-m) 0 0");
        if (sicht.verwaltbar()) {
            karte.add(adminKnoepfe(projekt));
        }
        return karte;
    }

    /** Die Beschreibung: für die verantwortliche Projektleitung ein Eingabefeld mit Speichern, sonst nur Text. */
    private Component beschreibungsFeld(Projekt projekt) {
        String text = projekt.getBeschreibung() == null ? "" : projekt.getBeschreibung();
        if (!sicht.beschreibungAenderbar()) {
            Span anzeige = new Span(text.isEmpty() ? "–" : text);
            anzeige.getStyle().set("white-space", "pre-wrap");
            return anzeige;
        }
        TextArea eingabe = new TextArea();
        eingabe.setValue(text);
        eingabe.setWidthFull();
        eingabe.setMinHeight("8rem");
        Button speichern = new Button("Beschreibung speichern", ereignis -> beschreibungSpeichern(eingabe.getValue()));
        speichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return new Div(eingabe, speichern);
    }

    private void beschreibungSpeichern(String beschreibung) {
        try {
            projektService.beschreibungAendern(sitzung.mitarbeiterId(), sitzung.rolle(), projektId, beschreibung);
            Meldungen.erfolg("Die Beschreibung wurde gespeichert.");
            neuLaden();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    // ---- Admin: bearbeiten, archivieren, reaktivieren ----

    private HorizontalLayout adminKnoepfe(Projekt projekt) {
        Button bearbeiten = new Button("Bearbeiten",
                ereignis -> new ProjektFormular(projektService, sitzung, projekt, gespeichert -> neuLaden()).open());
        bearbeiten.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button statusWechsel = projekt.getStatus() == ProjektStatus.AKTIV
                ? new Button("Projekt archivieren", ereignis -> archivierenBestaetigen())
                : new Button("Projekt reaktivieren", ereignis -> reaktivieren());
        return new HorizontalLayout(bearbeiten, statusWechsel);
    }

    /** Fragt vor dem Archivieren nach und weist auf laufende Buchungen hin (US-M3-04). */
    private void archivierenBestaetigen() {
        try {
            int laufend = projektService.laufendeBuchungenAnzahl(sitzung.mitarbeiterId(), sitzung.rolle(), projektId);
            String text = "Auf ein archiviertes Projekt kann nicht mehr eingestempelt werden. "
                    + "Zuordnungen und bisherige Buchungen bleiben erhalten.";
            if (laufend == 1) {
                text = "Achtung: Auf dieses Projekt läuft gerade 1 Buchung. " + text;
            } else if (laufend > 1) {
                text = "Achtung: Auf dieses Projekt laufen gerade " + laufend + " Buchungen. " + text;
            }
            ConfirmDialog dialog = new ConfirmDialog();
            dialog.setHeader("Projekt archivieren?");
            dialog.setText(text);
            dialog.setCancelable(true);
            dialog.setCancelText("Abbrechen");
            dialog.setConfirmText("Archivieren");
            dialog.addConfirmListener(ereignis -> statusWechseln(true));
            dialog.open();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void reaktivieren() {
        statusWechseln(false);
    }

    private void statusWechseln(boolean archivieren) {
        try {
            if (archivieren) {
                projektService.archivieren(sitzung.mitarbeiterId(), sitzung.rolle(), projektId);
                Meldungen.erfolg("Das Projekt wurde archiviert.");
            } else {
                projektService.reaktivieren(sitzung.mitarbeiterId(), sitzung.rolle(), projektId);
                Meldungen.erfolg("Das Projekt ist wieder aktiv.");
            }
            neuLaden();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
