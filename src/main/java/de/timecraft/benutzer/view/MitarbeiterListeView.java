package de.timecraft.benutzer.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.service.MitarbeiterService;
import de.timecraft.benutzer.service.MitarbeiterZeile;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.geruest.layout.Rollenanzeige;
import jakarta.annotation.security.RolesAllowed;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mitarbeiterliste (UC-M1-11, FA-M1-15, US-M1-14) für Projektleitung und Admin, gebaut nach dem
 * Mockup „Mitarbeiterliste“. Ein Klick auf eine Zeile öffnet die Bearbeitung, nur der Admin sieht
 * „+ Neuer Mitarbeiter“. Suche und Filter fehlen, weil FA-M1-K5 zurückgestellt ist.
 *
 * @author Leon-Selim Kannenberg
 */
@Route(value = MitarbeiterListeView.ROUTE, layout = HauptLayout.class)
@PageTitle("Mitarbeiter")
@RolesAllowed({"PROJEKTLEITUNG", "ADMIN"})
public class MitarbeiterListeView extends Div {

    static final String ROUTE = "mitarbeiter";
    static final String FORMULAR = ROUTE + "/formular";

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final Grid<MitarbeiterZeile> tabelle = new Grid<>();
    final Button neuerMitarbeiter = new Button("+ Neuer Mitarbeiter");
    final Span fusszeile = new Span();

    public MitarbeiterListeView(MitarbeiterService mitarbeiterService, AktuelleSitzung sitzung) {
        addClassName(LumoUtility.Padding.LARGE);

        // Nur der Admin legt Mitarbeiter an (UC-M1-04); der Service prüft das zusätzlich.
        neuerMitarbeiter.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        neuerMitarbeiter.setVisible(sitzung.rolle() == Rolle.ADMIN);
        neuerMitarbeiter.addClickListener(ereignis -> oeffnen(FORMULAR));
        HorizontalLayout werkzeugleiste = new HorizontalLayout(neuerMitarbeiter);
        werkzeugleiste.setWidthFull();
        werkzeugleiste.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        spaltenAnlegen();
        tabelle.setAllRowsVisible(true);
        tabelle.addItemClickListener(ereignis -> oeffnen(FORMULAR + "/" + ereignis.getItem().mitarbeiterId()));

        List<MitarbeiterZeile> zeilen = mitarbeiterService.liste(sitzung.rolle());
        tabelle.setItems(zeilen);
        fusszeile.setText(zeilen.size() + " Mitarbeiter · Klick auf eine Zeile öffnet die Bearbeitung");
        fusszeile.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

        Div unten = new Div(fusszeile);
        unten.addClassName(LumoUtility.Margin.Top.MEDIUM);
        add(werkzeugleiste, tabelle, unten);
    }

    /**
     * Spalten wie im Mockup: Pers.-Nr., Name, Qualifikation, Rolle, Vorgesetzte/r, Status.
     * Alle Spalten sind mindestens so breit wie ihr Inhalt; übrigen Platz bekommen Name und Vorgesetzte/r.
     */
    private void spaltenAnlegen() {
        tabelle.addColumn(MitarbeiterZeile::personalnummer).setHeader("Pers.-Nr.").setFlexGrow(0);
        tabelle.addColumn(new ComponentRenderer<>(this::nameAnzeige)).setHeader("Name").setFlexGrow(2);
        tabelle.addColumn(zeile -> zeile.qualifikation().getAnzeigename()).setHeader("Qualifikation").setFlexGrow(0);
        tabelle.addColumn(zeile -> Rollenanzeige.text(zeile.rolle())).setHeader("Rolle").setFlexGrow(0);
        tabelle.addColumn(this::vorgesetzterText).setHeader("Vorgesetzte/r").setFlexGrow(1);
        tabelle.addColumn(new ComponentRenderer<>(this::statusAnzeige)).setHeader("Status").setFlexGrow(0);
        for (Grid.Column<MitarbeiterZeile> spalte : tabelle.getColumns()) {
            spalte.setAutoWidth(true);
        }
    }

    /** Name fett; bei inaktiven Mitarbeitern grau wie im Mockup. */
    Span nameAnzeige(MitarbeiterZeile zeile) {
        Span name = new Span(zeile.name());
        name.addClassName(LumoUtility.FontWeight.SEMIBOLD);
        if (!zeile.aktiv()) {
            name.addClassName(LumoUtility.TextColor.SECONDARY);
        }
        return name;
    }

    String vorgesetzterText(MitarbeiterZeile zeile) {
        if (zeile.vorgesetzterName() == null) {
            return "–";
        }
        return zeile.vorgesetzterName();
    }

    /** Status als Lumo-Badge: grün „aktiv“ oder grau „inaktiv seit TT.MM.JJJJ“. */
    Span statusAnzeige(MitarbeiterZeile zeile) {
        Span status = new Span();
        if (zeile.aktiv()) {
            status.setText("aktiv");
            status.getElement().getThemeList().addAll(List.of("badge", "success"));
        } else {
            String text = "inaktiv";
            if (zeile.austrittsdatum() != null) {
                text = "inaktiv seit " + zeile.austrittsdatum().format(DATUM);
            }
            status.setText(text);
            status.getElement().getThemeList().addAll(List.of("badge", "contrast"));
        }
        return status;
    }

    private void oeffnen(String route) {
        getUI().ifPresent(ui -> ui.navigate(route));
    }
}
