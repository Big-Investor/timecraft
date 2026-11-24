package de.timecraft.material.view;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.service.AnpassungsantragService;
import de.timecraft.material.service.Mehrbedarfsgrund;
import de.timecraft.material.service.MeinMehrbedarf;
import de.timecraft.material.service.PlanungOhnePreis;
import de.timecraft.material.service.Projektauswahl;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mehrbedarf anfordern (FA-M4-04, Mockup "Material · Mehrbedarf anfordern") in vier Schritten:
 * Projekt, Material, zusaetzliche Menge, Begruendung. Rechts die eigenen Antraege. Keine Preise.
 *
 * @author Tharsan Arumuganathar
 */
@Route(value = AnpassungsantragView.ROUTE, layout = HauptLayout.class)
@PageTitle("Mehrbedarf anfordern")
@RolesAllowed({"MITARBEITER", "PROJEKTLEITUNG"})
public class AnpassungsantragView extends Div {

    static final String ROUTE = "material/mehrbedarf";
    private static final BigDecimal SCHRITT = new BigDecimal("0.5");

    private final AnpassungsantragService service;
    private final AktuelleSitzung sitzung;
    private final Select<Projektauswahl> projekt = new Select<>();
    private final RadioButtonGroup<PlanungOhnePreis> material = new RadioButtonGroup<>();
    private final BigDecimalField menge = new BigDecimalField();
    private final Span einheit = new Span();
    private final Span neueMenge = new Span();
    private final Map<Mehrbedarfsgrund, Button> gruende = new EnumMap<>(Mehrbedarfsgrund.class);
    private final TextArea begruendung = new TextArea();
    private final Div meine = new Div();
    private Mehrbedarfsgrund grund;

    /**
     * @param service Mehrbedarf-Service
     * @param sitzung angemeldete Person
     */
    public AnpassungsantragView(AnpassungsantragService service, AktuelleSitzung sitzung) {
        this.service = service;
        this.sitzung = sitzung;
        HorizontalLayout spalten = new HorizontalLayout(formular(), meineKarte());
        spalten.setAlignItems(FlexComponent.Alignment.START);
        spalten.getStyle().set("flex-wrap", "wrap").set("gap", "1.5rem");
        getStyle().set("padding", "1.5rem");
        add(spalten);
        try {
            List<Projektauswahl> projekte = service.projekteFuerMehrbedarf(sitzung.mitarbeiterId(), sitzung.rolle());
            projekt.setItems(projekte);
            if (!projekte.isEmpty()) {
                projekt.setValue(projekte.get(0));
            }
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
        meineAktualisieren();
    }

    private Component formular() {
        projekt.setItemLabelGenerator(Projektauswahl::name);
        projekt.setWidthFull();
        projekt.addValueChangeListener(ereignis -> materialLaden());
        material.setItemLabelGenerator(p -> p.bezeichnung() + " – geplant " + Formate.menge(p.sollMenge(), p.einheit()));
        material.getStyle().set("flex-direction", "column");
        material.addValueChangeListener(ereignis -> mengeAnzeigen());

        menge.setValue(BigDecimal.ONE);
        menge.setWidth("6rem");
        menge.addValueChangeListener(ereignis -> mengeAnzeigen());
        Button minus = new Button("−", ereignis -> mengeAendern(SCHRITT.negate()));
        Button plus = new Button("+", ereignis -> mengeAendern(SCHRITT));
        HorizontalLayout mengenzeile = new HorizontalLayout(minus, menge, plus, einheit, neueMenge);
        mengenzeile.setAlignItems(FlexComponent.Alignment.CENTER);
        mengenzeile.getStyle().set("flex-wrap", "wrap");
        neueMenge.getStyle().set("color", "var(--tc-text-gedaempft)").set("font-size", "var(--lumo-font-size-s)");

        HorizontalLayout knoepfe = new HorizontalLayout();
        knoepfe.getStyle().set("flex-wrap", "wrap");
        for (Mehrbedarfsgrund g : Mehrbedarfsgrund.values()) {
            Button knopf = new Button(g.text(), ereignis -> grundWaehlen(g));
            gruende.put(g, knopf);
            knoepfe.add(knopf);
        }
        begruendung.setWidthFull();
        begruendung.setMaxLength(239);
        begruendung.setPlaceholder("Was ist passiert?");

        Button absenden = new Button("Mehrbedarf anfordern", ereignis -> absenden());
        absenden.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        absenden.setWidthFull();

        Div karte = new Div(schritt("1. Projekt"), projekt, schritt("2. Material"), material,
                schritt("3. Zusätzliche Menge"), mengenzeile, schritt("4. Begründung *"), knoepfe, begruendung, absenden);
        karte.addClassName("karte");
        karte.getStyle().set("flex", "1 1 28rem").set("max-width", "36rem").set("margin", "0");
        return karte;
    }

    private Component meineKarte() {
        Paragraph hinweis = new Paragraph("Preise sind nur für Projektleitung und Admin sichtbar.");
        hinweis.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--tc-text-gedaempft)");
        Div karte = new Div(new H3("Meine Mehrbedarfe"), meine, hinweis);
        karte.addClassName("karte");
        karte.getStyle().set("flex", "1 1 20rem").set("max-width", "24rem").set("margin", "0");
        return karte;
    }

    private static Span schritt(String text) {
        Span titel = new Span(text);
        titel.getStyle().set("display", "block").set("font-weight", "700").set("margin", "1rem 0 0.25rem");
        return titel;
    }

    private void materialLaden() {
        material.clear();
        Projektauswahl gewaehlt = projekt.getValue();
        if (gewaehlt == null) {
            return;
        }
        try {
            List<PlanungOhnePreis> geplant = service.geplanteMaterialien(sitzung.mitarbeiterId(), sitzung.rolle(),
                    gewaehlt.projektId());
            material.setItems(geplant);
            if (!geplant.isEmpty()) {
                material.setValue(geplant.get(0));
            }
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void mengeAendern(BigDecimal schritt) {
        BigDecimal alt = menge.getValue() == null ? BigDecimal.ZERO : menge.getValue();
        BigDecimal neu = alt.add(schritt);
        menge.setValue(neu.signum() > 0 ? neu : SCHRITT);
    }

    /** Nur Anzeige: Einheit und neue Menge (geplant + zusaetzlich) neben dem Feld. */
    private void mengeAnzeigen() {
        PlanungOhnePreis gewaehlt = material.getValue();
        if (gewaehlt == null || menge.getValue() == null) {
            einheit.setText("");
            neueMenge.setText("");
            return;
        }
        einheit.setText(gewaehlt.einheit().kuerzel());
        neueMenge.setText("neu: " + Formate.menge(gewaehlt.sollMenge().add(menge.getValue()), gewaehlt.einheit()));
    }

    private void grundWaehlen(Mehrbedarfsgrund gewaehlt) {
        grund = gewaehlt == grund ? null : gewaehlt;
        gruende.forEach((g, knopf) -> {
            if (g == grund) {
                knopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            } else {
                knopf.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
        });
    }

    private void grundZuruecksetzen() {
        grund = null;
        gruende.values().forEach(knopf -> knopf.removeThemeVariants(ButtonVariant.LUMO_PRIMARY));
    }

    private void absenden() {
        PlanungOhnePreis gewaehlt = material.getValue();
        if (gewaehlt == null) {
            Meldungen.fehler(new FachlicheException("Bitte wählen Sie ein Material."));
            return;
        }
        try {
            Materialanpassungsantrag antrag = service.einreichen(sitzung.mitarbeiterId(), sitzung.rolle(),
                    gewaehlt.positionId(), menge.getValue(), grund, begruendung.getValue());
            Meldungen.erfolg(antrag.getStatus() == Antragsstatus.GENEHMIGT
                    ? "Menge erhöht. Als Projektleitung ist Ihr Antrag sofort genehmigt."
                    : "Mehrbedarf angefordert. Die Projektleitung wird ihn prüfen.");
            begruendung.clear();
            grundZuruecksetzen();
            menge.setValue(BigDecimal.ONE);
            materialLaden();
            meineAktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void meineAktualisieren() {
        meine.removeAll();
        try {
            List<MeinMehrbedarf> antraege = service.meineAntraege(sitzung.mitarbeiterId(), sitzung.rolle());
            if (antraege.isEmpty()) {
                meine.add(new Paragraph("Noch keine Mehrbedarfe."));
            }
            antraege.forEach(antrag -> meine.add(eintrag(antrag)));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private Component eintrag(MeinMehrbedarf antrag) {
        Span titel = new Span(antrag.bezeichnung() + " +" + Formate.menge(antrag.zusatzmenge(), antrag.einheit()));
        titel.getStyle().set("font-weight", "700");
        HorizontalLayout kopf = new HorizontalLayout(titel, statusMarke(antrag.status()));
        kopf.setWidthFull();
        kopf.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        String zusatz = antrag.entscheider().isEmpty() ? "" : " · " + antrag.entscheider();
        Span unterzeile = new Span(antrag.projekt() + " · " + Formate.datum(antrag.eingereichtAm()) + zusatz);
        unterzeile.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--tc-text-gedaempft)");
        Div eintrag = new Div(kopf, unterzeile);
        if (antrag.entscheidungshinweis() != null) {
            Span hinweis = new Span("„" + antrag.entscheidungshinweis() + "“");
            hinweis.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-s)");
            eintrag.add(hinweis);
        }
        if (antrag.status() == Antragsstatus.OFFEN) {
            Button zurueck = new Button("Zurückziehen", ereignis -> zurueckziehen(antrag.antragId()));
            zurueck.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            eintrag.add(zurueck);
        }
        eintrag.getStyle().set("border-top", "1px solid var(--tc-rahmen)").set("padding", "0.75rem 0");
        return eintrag;
    }

    private static Span statusMarke(Antragsstatus status) {
        Span marke = new Span(switch (status) {
            case OFFEN -> "offen";
            case GENEHMIGT -> "genehmigt";
            case ABGELEHNT -> "abgelehnt";
            case ZURUECKGEZOGEN -> "zurückgezogen";
        });
        String[] farben = switch (status) {
            case GENEHMIGT -> new String[] {"var(--lumo-success-color-10pct)", "var(--lumo-success-text-color)"};
            case ABGELEHNT -> new String[] {"var(--lumo-error-color-10pct)", "var(--lumo-error-text-color)"};
            default -> new String[] {"var(--lumo-contrast-5pct)", "var(--tc-text-gedaempft)"};
        };
        marke.getStyle().set("background", farben[0]).set("color", farben[1]).set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "700").set("padding", "0.1rem 0.6rem").set("border-radius", "1rem");
        return marke;
    }

    private void zurueckziehen(long antragId) {
        try {
            service.zurueckziehen(sitzung.mitarbeiterId(), sitzung.rolle(), antragId);
            Meldungen.erfolg("Antrag zurückgezogen.");
            materialLaden();
            meineAktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
