package de.timecraft.projekte.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.service.ProjektService;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Die Projektliste mit Filterleiste (Mockup "Projektliste"). Welche Projekte eine Rolle sehen darf,
 * entscheidet allein der {@link ProjektService}; die View zeigt nur an, was er liefert. Jede Änderung
 * an einem Filterfeld lädt die Liste neu, "Filter zurücksetzen" leert alle Felder.
 */
@Route(value = ProjektListeView.ROUTE, layout = HauptLayout.class)
@PageTitle("Projekte")
@PermitAll
public class ProjektListeView extends Div {

    static final String ROUTE = "projekte";

    private static final String GRAU = "var(--lumo-secondary-text-color)";

    private final ProjektService service;
    private final AktuelleSitzung sitzung;

    private final TextField name = new TextField("Projektname");
    private final Select<ProjektStatus> status = new Select<>();
    private final DatePicker startVon = new DatePicker();
    private final DatePicker startBis = new DatePicker();
    private final DatePicker zielVon = new DatePicker();
    private final DatePicker zielBis = new DatePicker();
    private final Span anzahl = new Span();
    private final Grid<Projekt> liste = new Grid<>();
    private final Div keineTreffer = new Div("Keine Projekte gefunden");

    private Map<Long, String> projektleitungen = Map.of();

    public ProjektListeView(ProjektService service, AktuelleSitzung sitzung) {
        this.service = service;
        this.sitzung = sitzung;
        add(filterKarte(), kopfzeile(), tabellenKarte());
        aktualisieren();
    }

    private Div filterKarte() {
        name.setPlaceholder("z. B. Küche");
        name.setValueChangeMode(ValueChangeMode.LAZY);
        name.setWidth("18rem");
        name.addValueChangeListener(ereignis -> aktualisieren());

        status.setLabel("Status");
        status.setItems(ProjektStatus.AKTIV, ProjektStatus.ARCHIVIERT);
        status.setItemLabelGenerator(wert -> wert == ProjektStatus.AKTIV ? "Aktiv" : "Archiviert");
        status.setEmptySelectionAllowed(true);
        status.setEmptySelectionCaption("Alle");
        status.addValueChangeListener(ereignis -> aktualisieren());

        Button zuruecksetzen = new Button("Filter zurücksetzen", ereignis -> zuruecksetzen());
        zuruecksetzen.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout felder = new HorizontalLayout(name, status,
                zeitraum("Start von – bis", startVon, startBis, "Start"),
                zeitraum("Ziel von – bis", zielVon, zielBis, "Ziel"));
        felder.setAlignItems(FlexComponent.Alignment.END);
        felder.setWrap(true);

        Div karte = new Div(felder, zuruecksetzen);
        karte.addClassName("karte");
        return karte;
    }

    /** Ein Zeitraumfilter: Überschrift und darunter die beiden Datumsfelder "von" und "bis". */
    private Div zeitraum(String ueberschrift, DatePicker von, DatePicker bis, String ariaName) {
        Span beschriftung = new Span(ueberschrift);
        beschriftung.getStyle().set("font-size", "var(--lumo-font-size-s)").set("font-weight", "600");
        for (DatePicker feld : new DatePicker[] {von, bis}) {
            feld.setLocale(Locale.GERMANY);
            feld.setWidth("10rem");
            feld.addValueChangeListener(ereignis -> aktualisieren());
        }
        von.setPlaceholder("von");
        von.setAriaLabel(ariaName + " von");
        bis.setPlaceholder("bis");
        bis.setAriaLabel(ariaName + " bis");
        return new Div(beschriftung, new HorizontalLayout(von, bis));
    }

    private HorizontalLayout kopfzeile() {
        HorizontalLayout zeile = new HorizontalLayout(anzahl);
        zeile.setWidthFull();
        zeile.setAlignItems(FlexComponent.Alignment.CENTER);
        zeile.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        zeile.getStyle().set("padding", "0 1.5rem").set("box-sizing", "border-box");
        return zeile;
    }

    private Div tabellenKarte() {
        liste.addComponentColumn(projekt -> text(ProjektAnzeige.id(projekt.getProjektId()), projekt, false))
                .setHeader("Projekt-ID").setAutoWidth(true).setFlexGrow(0);
        liste.addComponentColumn(projekt -> text(projekt.getName(), projekt, true))
                .setHeader("Name").setFlexGrow(2);
        liste.addComponentColumn(projekt -> text(ProjektAnzeige.datum(projekt.getStartdatum()), projekt, false))
                .setHeader("Start").setAutoWidth(true).setFlexGrow(0);
        liste.addComponentColumn(projekt -> text(ProjektAnzeige.datum(projekt.getZieldatum()), projekt, false))
                .setHeader("Ziel").setAutoWidth(true).setFlexGrow(0);
        liste.addComponentColumn(projekt -> text(
                        projektleitungen.getOrDefault(projekt.getProjektleitungId(), ""), projekt, false))
                .setHeader("Projektleitung").setFlexGrow(1);
        liste.addComponentColumn(projekt -> ProjektAnzeige.statusMarke(projekt.getStatus()))
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        liste.setAllRowsVisible(true);

        keineTreffer.getStyle().set("padding", "1.5rem").set("color", GRAU);
        Div karte = new Div(liste, keineTreffer);
        karte.addClassName("karte");
        karte.getStyle().set("padding", "0").set("overflow", "hidden");
        return karte;
    }

    /** Ein Textfeld der Zeile. Archivierte Projekte erscheinen in Grau, der Name in Fett. */
    private Span text(String inhalt, Projekt projekt, boolean fett) {
        Span zelle = new Span(inhalt);
        if (fett) {
            zelle.getStyle().set("font-weight", "700");
        }
        if (projekt.getStatus() == ProjektStatus.ARCHIVIERT) {
            zelle.getStyle().set("color", GRAU);
        }
        return zelle;
    }

    private void zuruecksetzen() {
        // Jedes Leeren lädt die Liste neu; zum Schluss steht sie ohne Filter da.
        name.clear();
        status.clear();
        startVon.clear();
        startBis.clear();
        zielVon.clear();
        zielBis.clear();
    }

    private ProjektFilter aktuellerFilter() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName(name.getValue());
        filter.setStatus(status.getValue());
        filter.setStartVon(startVon.getValue());
        filter.setStartBis(startBis.getValue());
        filter.setZielVon(zielVon.getValue());
        filter.setZielBis(zielBis.getValue());
        return filter;
    }

    private void aktualisieren() {
        try {
            List<Projekt> projekte = service.projekteSehen(sitzung.mitarbeiterId(), sitzung.rolle(), aktuellerFilter());
            Set<Long> leitungIds = projekte.stream().map(Projekt::getProjektleitungId).collect(Collectors.toSet());
            projektleitungen = service.namenDerPersonen(sitzung.mitarbeiterId(), sitzung.rolle(), leitungIds);
            liste.setItems(projekte);
            anzahl.setText(projekte.size() == 1 ? "1 Projekt" : projekte.size() + " Projekte");
            liste.setVisible(!projekte.isEmpty());
            keineTreffer.setVisible(projekte.isEmpty());
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
