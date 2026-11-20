package de.timecraft.material.view;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.material.model.Kostendeckung;
import de.timecraft.material.model.Kostenuebersicht;
import de.timecraft.material.model.Material;
import de.timecraft.material.service.AnpassungsantragService;
import de.timecraft.material.service.AntragZurPruefung;
import de.timecraft.material.service.HistorienEintrag;
import de.timecraft.material.service.Kalkulationszeile;
import de.timecraft.material.service.KostenService;
import de.timecraft.material.service.MaterialService;
import de.timecraft.material.service.Projektkopf;
import de.timecraft.material.service.Vorkalkulation;
import de.timecraft.material.service.VorkalkulationService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.FooterRow;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.util.List;

/**
 * Materialplanung &amp; Vorkalkulation eines Projekts (Mockup "Material · Materialplanung &amp; Vorkalkulation"):
 * Warnbanner offener Mehrbedarf, Materialtabelle mit Vorkalkulation, Position hinzufuegen, Auftragskosten
 * mit Angebotspreis und Unterdeckungswarnung, Projekthistorie. M3 verlinkt diese Route aus dem Projektdetail.
 */
@Route(value = MaterialbedarfView.ROUTE, layout = HauptLayout.class)
@PageTitle("Materialplanung & Vorkalkulation")
@RolesAllowed({"PROJEKTLEITUNG", "ADMIN"})
public class MaterialbedarfView extends Div implements BeforeEnterObserver {

    static final String PARAMETER = "projektId";
    static final String ROUTE = "projekt/:" + PARAMETER + "/material";

    private final VorkalkulationService vorkalkulationService;
    private final KostenService kostenService;
    private final AnpassungsantragService antragService;
    private final MaterialService materialService;
    private final AktuelleSitzung sitzung;
    private long projektId;

    public MaterialbedarfView(VorkalkulationService vorkalkulationService, KostenService kostenService,
            AnpassungsantragService antragService, MaterialService materialService, AktuelleSitzung sitzung) {
        this.vorkalkulationService = vorkalkulationService;
        this.kostenService = kostenService;
        this.antragService = antragService;
        this.materialService = materialService;
        this.sitzung = sitzung;
        getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "1rem").set("padding", "1.5rem");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent ereignis) {
        projektId = ereignis.getRouteParameters().getLong(PARAMETER).orElse(-1L);
        laden();
    }

    private long id() {
        return sitzung.mitarbeiterId();
    }

    private void laden() {
        removeAll();
        try {
            Projektkopf projekt = vorkalkulationService.projektkopf(id(), sitzung.rolle(), projektId);
            add(pfad(projekt));
            List<AntragZurPruefung> offene = antragService.offeneAntraege(id(), sitzung.rolle(), projektId);
            if (!offene.isEmpty()) {
                add(banner(offene, projekt.darfPlanen()));
            }
            Div rechts = new Div(auftragskosten(projekt), historie());
            rechts.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "1rem")
                    .set("flex", "0 0 18rem").set("min-width", "0");
            HorizontalLayout spalten = new HorizontalLayout(materialtabelle(projekt), rechts);
            spalten.setWidthFull();
            spalten.setAlignItems(FlexComponent.Alignment.START);
            add(spalten);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private Component pfad(Projektkopf projekt) {
        Div pfad = new Div(new RouterLink(projekt.name(), MaterialUebersichtView.class), new Span(" / Materialplanung"));
        pfad.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--tc-text-gedaempft)");
        return pfad;
    }

    private Component banner(List<AntragZurPruefung> offene, boolean darfEntscheiden) {
        AntragZurPruefung erster = offene.get(0);
        Span anzahl = new Span(offene.size() == 1 ? "1 offener Mehrbedarf: " : offene.size() + " offene Mehrbedarfe: ");
        anzahl.getStyle().set("font-weight", "700");
        Span text = new Span(erster.antragsteller() + " · " + erster.bezeichnung() + " +"
                + Formate.menge(erster.zusatzmenge(), erster.einheit()) + (offene.size() > 1 ? " und weitere" : ""));
        Div banner = new Div(new Span(anzahl, text));
        if (darfEntscheiden) {
            Button pruefen = new Button("Jetzt prüfen", ereignis -> new AntragPruefenDialog(offene,
                    this::freigeben, this::ablehnen).open());
            pruefen.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            pruefen.getStyle().set("color", "var(--tc-fehler-text)").set("font-weight", "700")
                    .set("text-decoration", "underline");
            banner.add(pruefen);
        }
        banner.getStyle().set("display", "flex").set("justify-content", "space-between").set("align-items", "center")
                .set("padding", "0.75rem 1rem").set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--tc-fehler-hintergrund)").set("border", "1px solid var(--tc-fehler-rahmen)")
                .set("color", "var(--tc-fehler-text)");
        return banner;
    }

    private Component materialtabelle(Projektkopf projekt) {
        Vorkalkulation kalkulation = vorkalkulationService.vorkalkulation(id(), sitzung.rolle(), projektId);
        Grid<Kalkulationszeile> tabelle = new Grid<>();
        Grid.Column<Kalkulationszeile> material = tabelle.addComponentColumn(this::materialzelle)
                .setHeader("Material").setFlexGrow(1).setWidth("9rem");
        tabelle.addColumn(z -> Formate.menge(z.sollMenge(), z.einheit())).setHeader(kopf("Soll-Menge"))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("5.5rem").setFlexGrow(0);
        tabelle.addColumn(z -> Formate.euro(z.einkaufspreis())).setHeader(kopf("EK / Einh."))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("5.3rem").setFlexGrow(0);
        tabelle.addColumn(z -> Formate.euro(z.verkaufspreis())).setHeader(kopf("VK / Einh."))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("5.6rem").setFlexGrow(0);
        Grid.Column<Kalkulationszeile> kosten = tabelle.addColumn(z -> Formate.euro(z.kosten())).setHeader(kopf("Kosten (EK)"))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("6rem").setFlexGrow(0);
        Grid.Column<Kalkulationszeile> verkauf = tabelle.addColumn(z -> Formate.euro(z.verkauf()))
                .setHeader(kopf("Verkauf (VK)"))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("6rem").setFlexGrow(0);
        Grid.Column<Kalkulationszeile> db = tabelle.addColumn(z -> Formate.euro(z.deckungsbeitrag())).setHeader(kopf("DB"))
                .setTextAlign(ColumnTextAlign.END)
                .setWidth("5.9rem").setFlexGrow(0);
        tabelle.setItems(kalkulation.zeilen());
        tabelle.setAllRowsVisible(true);
        FooterRow summe = tabelle.appendFooterRow();
        summe.getCell(material).setText("Summe Material");
        summe.getCell(kosten).setText(Formate.euro(kalkulation.summeKosten()));
        summe.getCell(verkauf).setText(Formate.euro(kalkulation.summeVerkauf()));
        summe.getCell(db).setText(Formate.euro(kalkulation.summeDeckungsbeitrag()));

        tabelle.getStyle().set("margin-top", "0").set("font-size", "var(--lumo-font-size-s)");
        Div karte = new Div(tabelle);
        karte.addClassName("karte");
        karte.getStyle().set("flex", "1 1 auto").set("min-width", "0").set("margin", "0").set("padding", "0 0 1rem");
        if (projekt.darfPlanen()) {
            karte.add(positionHinzufuegen());
        } else if (!projekt.aktiv()) {
            Paragraph hinweis = new Paragraph("Das Projekt ist abgeschlossen. Es können keine Positionen mehr hinzugefügt werden.");
            hinweis.getStyle().set("padding", "1rem 1rem 0");
            karte.add(hinweis);
        }
        return karte;
    }

    /** Spaltenkopf, der wie im Mockup umbrechen darf. */
    private static Span kopf(String text) {
        Span kopf = new Span(text);
        kopf.getStyle().set("white-space", "normal").set("line-height", "1.2");
        return kopf;
    }

    private Component materialzelle(Kalkulationszeile zeile) {
        Span name = new Span(zeile.bezeichnung());
        name.getStyle().set("font-weight", "600").set("white-space", "normal");
        Span nummer = new Span(zeile.artikelnummer());
        nummer.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--tc-text-gedaempft)");
        Div zelle = new Div(name, nummer);
        zelle.getStyle().set("display", "flex").set("flex-direction", "column");
        return zelle;
    }

    private Component positionHinzufuegen() {
        ComboBox<Material> katalog = new ComboBox<>("Material aus Katalog");
        katalog.setItems(materialService.aktive(sitzung.rolle()));
        katalog.setItemLabelGenerator(m -> m.getArtikelnummer() + " · " + m.getBezeichnung()
                + " (" + m.getEinheit().kuerzel() + ")");
        katalog.setWidth("22rem");
        BigDecimalField menge = new BigDecimalField("Soll-Menge");
        Button hinzufuegen = new Button("Position hinzufügen", ereignis -> {
            try {
                Material gewaehlt = katalog.getValue();
                if (gewaehlt == null) {
                    katalog.setInvalid(true);
                    katalog.setErrorMessage("Bitte wählen Sie ein Material.");
                    return;
                }
                vorkalkulationService.positionHinzufuegen(id(), sitzung.rolle(), projektId, gewaehlt.getId(),
                        menge.getValue());
                Meldungen.erfolg("Position hinzugefügt.");
                laden();
            } catch (RuntimeException fehler) {
                Meldungen.fehler(fehler);
            }
        });
        hinzufuegen.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout zeile = new HorizontalLayout(katalog, menge, hinzufuegen);
        zeile.setAlignItems(FlexComponent.Alignment.BASELINE);
        zeile.getStyle().set("margin", "1rem 1rem 0");
        return zeile;
    }

    private Component auftragskosten(Projektkopf projekt) {
        Kostenuebersicht kosten = kostenService.kosten(id(), sitzung.rolle(), projektId);
        Div karte = new Div(new H3("Auftragskosten"),
                zeile("Material (EK)", Formate.euro(kosten.materialkosten())),
                zeile("Personal (" + Formate.stunden(kosten.personalMinuten()) + ")", Formate.euro(kosten.personalkosten())));
        Div gesamt = zeile("Gesamtkosten", Formate.euro(kosten.gesamtkosten()));
        gesamt.getStyle().set("font-weight", "700").set("border-top", "1px solid var(--tc-rahmen)")
                .set("padding-top", "0.5rem");
        karte.add(gesamt);
        karte.add(projekt.darfPlanen() ? angebotspreisFeld(kosten.angebotspreis())
                : zeile("Angebotspreis (netto)", kosten.angebotspreis() == null ? "fehlt" : Formate.euro(kosten.angebotspreis())));
        karte.add(deckung(kosten));
        if (!kosten.ohneStundensatz().isEmpty()) {
            Paragraph hinweis = new Paragraph("Stundensatz fehlt für: " + String.join(", ", kosten.ohneStundensatz())
                    + " (mit 0 € gerechnet).");
            hinweis.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--tc-fehler-text)");
            karte.add(hinweis);
        }
        karte.addClassName("karte");
        karte.getStyle().set("margin", "0");
        return karte;
    }

    private Component angebotspreisFeld(BigDecimal angebotspreis) {
        BigDecimalField feld = new BigDecimalField("Angebotspreis (netto)");
        feld.setValue(angebotspreis);
        feld.getStyle().set("flex", "1").set("min-width", "0");
        Button speichern = new Button("Übernehmen", ereignis -> {
            try {
                kostenService.setzeAngebotspreis(id(), sitzung.rolle(), projektId, feld.getValue());
                Meldungen.erfolg("Angebotspreis gespeichert.");
                laden();
            } catch (RuntimeException fehler) {
                Meldungen.fehler(fehler);
            }
        });
        HorizontalLayout zeile = new HorizontalLayout(feld, speichern);
        zeile.setAlignItems(FlexComponent.Alignment.BASELINE);
        zeile.setWidthFull();
        return zeile;
    }

    private Component deckung(Kostenuebersicht kosten) {
        String text;
        String hintergrund;
        String farbe;
        if (kosten.kostendeckung() == Kostendeckung.GEDECKT) {
            text = "Kosten gedeckt";
            hintergrund = "var(--lumo-success-color-10pct)";
            farbe = "var(--lumo-success-text-color)";
        } else if (kosten.kostendeckung() == Kostendeckung.UNTERDECKUNG) {
            text = "Unterdeckung";
            hintergrund = "var(--lumo-error-color-10pct)";
            farbe = "var(--lumo-error-text-color)";
        } else {
            text = "Angebotspreis fehlt";
            hintergrund = "var(--lumo-contrast-5pct)";
            farbe = "var(--tc-text-gedaempft)";
        }
        Span wert = new Span(kosten.differenz() == null ? "" : Formate.euroMitVorzeichen(kosten.differenz()));
        wert.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "700");
        Div box = new Div(new Span(text), wert);
        box.getStyle().set("display", "flex").set("justify-content", "space-between").set("align-items", "center")
                .set("margin-top", "0.75rem").set("padding", "0.75rem").set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", hintergrund).set("color", farbe).set("font-weight", "600");
        return box;
    }

    private Component historie() {
        Div karte = new Div(new H3("Projekthistorie Material"));
        karte.addClassName("karte");
        karte.getStyle().set("margin", "0");
        List<HistorienEintrag> eintraege = antragService.projekthistorie(id(), sitzung.rolle(), projektId);
        if (eintraege.isEmpty()) {
            karte.add(new Paragraph("Noch keine Einträge."));
        }
        for (HistorienEintrag eintrag : eintraege) {
            Span tag = new Span(Formate.tagMonat(eintrag.zeitpunkt()) + " ");
            tag.getStyle().set("font-weight", "700");
            String text = eintrag.art() == HistorienEintrag.Art.GENEHMIGT
                    ? eintrag.bezeichnung() + " +" + Formate.menge(eintrag.menge(), eintrag.einheit())
                            + " genehmigt · „" + eintrag.begruendung() + "“ · " + eintrag.person()
                    : eintrag.bezeichnung() + " " + Formate.menge(eintrag.menge(), eintrag.einheit())
                            + " eingeplant · " + eintrag.person();
            Paragraph absatz = new Paragraph(tag, new Span(text));
            absatz.getStyle().set("font-size", "var(--lumo-font-size-s)").set("margin", "0.5rem 0");
            karte.add(absatz);
        }
        return karte;
    }

    private static Div zeile(String bezeichnung, String wert) {
        Div zeile = new Div(new Span(bezeichnung), new Span(wert));
        zeile.getStyle().set("display", "flex").set("justify-content", "space-between").set("gap", "1rem")
                .set("padding", "0.25rem 0");
        return zeile;
    }

    private boolean freigeben(long antragId) {
        try {
            antragService.freigeben(id(), sitzung.rolle(), antragId);
            Meldungen.erfolg("Mehrbedarf freigegeben.");
            laden();
            return true;
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            return false;
        }
    }

    private boolean ablehnen(long antragId, String kommentar) {
        try {
            antragService.ablehnen(id(), sitzung.rolle(), antragId, kommentar);
            Meldungen.erfolg("Mehrbedarf abgelehnt.");
            laden();
            return true;
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            return false;
        }
    }
}
