package de.timecraft.material.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.material.service.Projektkopf;
import de.timecraft.material.service.VorkalkulationService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.RouteParameters;
import jakarta.annotation.security.RolesAllowed;

/**
 * Einstieg "Material" fuer Projektleitung (verantwortete Projekte) und Admin (alle Projekte):
 * fuehrt zur Materialplanung je Projekt. Die Projektleitung kann von hier auch selbst Mehrbedarf anfordern.
 *
 * @author Tharsan Arumuganathar
 */
@Route(value = MaterialUebersichtView.ROUTE, layout = HauptLayout.class)
@PageTitle("Material")
@RolesAllowed({"PROJEKTLEITUNG", "ADMIN"})
public class MaterialUebersichtView extends Div {

    static final String ROUTE = "material";

    /**
     * @param service Projektliste
     * @param sitzung angemeldete Person
     */
    public MaterialUebersichtView(VorkalkulationService service, AktuelleSitzung sitzung) {
        addClassName("karte");
        H2 titel = new H2(sitzung.rolle() == Rolle.ADMIN ? "Alle Projekte" : "Meine Projekte");
        Grid<Projektkopf> liste = new Grid<>();
        liste.addColumn(Projektkopf::name).setHeader("Projekt").setFlexGrow(1);
        liste.addColumn(p -> p.aktiv() ? "aktiv" : "abgeschlossen").setHeader("Status").setAutoWidth(true);
        liste.addComponentColumn(p -> new RouterLink("Materialplanung öffnen", MaterialbedarfView.class,
                new RouteParameters(MaterialbedarfView.PARAMETER, String.valueOf(p.projektId()))))
                .setAutoWidth(true);
        liste.setAllRowsVisible(true);
        add(titel, liste);
        if (sitzung.rolle() == Rolle.PROJEKTLEITUNG) {
            Button mehrbedarf = new Button("Mehrbedarf anfordern",
                    ereignis -> getUI().ifPresent(ui -> ui.navigate(AnpassungsantragView.class)));
            mehrbedarf.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            add(mehrbedarf);
        }
        try {
            liste.setItems(service.projekte(sitzung.mitarbeiterId(), sitzung.rolle()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
