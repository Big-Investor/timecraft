package de.timecraft.material.view;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.material.model.Material;
import de.timecraft.material.service.MaterialDaten;
import de.timecraft.material.service.MaterialService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/** Materialstamm (FA-M4-01): Liste mit Preisen, anlegen, bearbeiten, deaktivieren und wieder aktivieren. */
@Route(value = MaterialListeView.ROUTE, layout = HauptLayout.class)
@PageTitle("Materialstamm")
@RolesAllowed("ADMIN")
public class MaterialListeView extends Div {

    static final String ROUTE = "material/stamm";

    private final MaterialService service;
    private final AktuelleSitzung sitzung;
    private final Grid<Material> liste = new Grid<>();

    public MaterialListeView(MaterialService service, AktuelleSitzung sitzung) {
        this.service = service;
        this.sitzung = sitzung;
        addClassName("karte");
        Button neu = new Button("Neues Material", ereignis -> formularOeffnen(null));
        neu.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        spaltenEinrichten();
        add(neu, liste);
        aktualisieren();
    }

    private void spaltenEinrichten() {
        liste.addColumn(Material::getArtikelnummer).setHeader("Artikelnummer").setAutoWidth(true);
        liste.addColumn(Material::getBezeichnung).setHeader("Bezeichnung").setFlexGrow(1).setWidth("16rem");
        liste.addColumn(Material::getKategorie).setHeader("Kategorie").setAutoWidth(true);
        liste.addColumn(m -> m.getEinheit().kuerzel()).setHeader("Einheit").setAutoWidth(true);
        liste.addColumn(m -> Formate.euro(m.getEinkaufspreis())).setHeader("EK").setAutoWidth(true);
        liste.addColumn(m -> Formate.euro(m.getVerkaufspreis())).setHeader("VK").setAutoWidth(true);
        liste.addComponentColumn(m -> new Span(m.isAktiv() ? "aktiv" : "deaktiviert")).setHeader("Status")
                .setAutoWidth(true);
        liste.addComponentColumn(this::aktionen).setAutoWidth(true);
        liste.setAllRowsVisible(true);
    }

    private HorizontalLayout aktionen(Material material) {
        Button bearbeiten = new Button("Bearbeiten", ereignis -> formularOeffnen(material));
        bearbeiten.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button umschalten = material.isAktiv()
                ? new Button("Deaktivieren", ereignis -> ausfuehren(() -> service.deaktivieren(sitzung.rolle(),
                        material.getId()), "Material deaktiviert."))
                : new Button("Aktivieren", ereignis -> ausfuehren(() -> service.aktivieren(sitzung.rolle(),
                        material.getId()), "Material aktiviert."));
        umschalten.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return new HorizontalLayout(bearbeiten, umschalten);
    }

    private void formularOeffnen(Material material) {
        new MaterialFormular(material, daten -> speichern(material, daten)).open();
    }

    private boolean speichern(Material material, MaterialDaten daten) {
        try {
            if (material == null) {
                service.anlegen(sitzung.rolle(), daten);
            } else {
                service.bearbeiten(sitzung.rolle(), material.getId(), daten);
            }
            aktualisieren();
            Meldungen.erfolg("Material gespeichert.");
            return true;
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            return false;
        }
    }

    private void ausfuehren(Runnable aktion, String erfolg) {
        try {
            aktion.run();
            aktualisieren();
            Meldungen.erfolg(erfolg);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void aktualisieren() {
        try {
            liste.setItems(service.alle(sitzung.rolle()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
