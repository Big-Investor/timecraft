package de.timecraft.antraege.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.service.AntragAnsicht;
import de.timecraft.antraege.service.AntragFilter;
import de.timecraft.antraege.service.AntragService;
import de.timecraft.antraege.service.EntscheidungService;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import jakarta.annotation.security.RolesAllowed;
import java.util.ArrayList;
import java.util.List;

/**
 * Seite "Anträge prüfen" nach Mockup "M5 · Anträge prüfen (Leitung)": Filter, Liste der
 * zustaendigen Antraege, Details mit allen Pruefdaten, Genehmigen oder Ablehnen
 * (FA-M5-10 bis FA-M5-12). Entscheidung aus der Liste: Antrag anklicken, Knopf druecken.
 */
@Route(value = "antraege/pruefen", layout = HauptLayout.class)
@PageTitle("Anträge prüfen")
@RolesAllowed("PROJEKTLEITUNG")
public class AntragPruefungView extends VerticalLayout {

    /** Eine Zeile der Vergleichstabelle einer Zeitkorrektur. */
    private record Vergleich(String feld, String bisher, String gewuenscht) {
    }

    private final AntragService antragService;
    private final EntscheidungService entscheidungService;
    private final AktuelleSitzung sitzung;

    private final Select<Antragstyp> typ = new Select<>();
    private final Select<Antragsstatus> status = new Select<>();
    private final Select<MitarbeiterInfo> mitarbeiter = new Select<>();
    private final DatePicker von = Datumsfelder.datum("Zeitraum von");
    private final DatePicker bis = Datumsfelder.datum("bis");
    private final Grid<AntragAnsicht> liste = new Grid<>();
    private final Div details = new Div();

    public AntragPruefungView(AntragService antragService, EntscheidungService entscheidungService,
            AktuelleSitzung sitzung) {
        this.antragService = antragService;
        this.entscheidungService = entscheidungService;
        this.sitzung = sitzung;
        filterEinrichten();
        liste.addComponentColumn(this::listeneintrag);
        liste.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_WRAP_CELL_CONTENT);
        liste.asSingleSelect().addValueChangeListener(ereignis -> detailsZeigen(ereignis.getValue()));
        liste.setWidth("40%");
        details.addClassName("karte");
        details.setWidth("60%");
        details.setVisible(false);
        HorizontalLayout inhalt = new HorizontalLayout(liste, details);
        inhalt.setWidthFull();
        inhalt.setAlignItems(FlexComponent.Alignment.START);
        HorizontalLayout filter = new HorizontalLayout(typ, status, mitarbeiter, von, bis);
        filter.setAlignItems(FlexComponent.Alignment.BASELINE);
        add(filter, inhalt);
        aktualisieren(null);
    }

    private void filterEinrichten() {
        typ.setLabel("Typ");
        typ.setItems(Antragstyp.values());
        typ.setEmptySelectionAllowed(true);
        typ.setEmptySelectionCaption("Alle Typen");
        typ.setItemLabelGenerator(eintrag -> eintrag == null ? "Alle Typen" : eintrag.bezeichnung());
        status.setLabel("Status");
        status.setItems(Antragsstatus.values());
        status.setEmptySelectionAllowed(true);
        status.setEmptySelectionCaption("Alle");
        status.setItemLabelGenerator(eintrag -> eintrag == null ? "Alle" : statusname(eintrag));
        status.setValue(Antragsstatus.OFFEN);
        mitarbeiter.setLabel("Mitarbeiter");
        mitarbeiter.setEmptySelectionAllowed(true);
        mitarbeiter.setEmptySelectionCaption("Alle");
        mitarbeiter.setItemLabelGenerator(person -> person == null ? "Alle" : person.name());
        try {
            mitarbeiter.setItems(antragService.zugeordneteMitarbeiter(sitzung.mitarbeiterId(), sitzung.rolle()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
        typ.addValueChangeListener(ereignis -> aktualisieren(null));
        status.addValueChangeListener(ereignis -> aktualisieren(null));
        mitarbeiter.addValueChangeListener(ereignis -> aktualisieren(null));
        von.addValueChangeListener(ereignis -> aktualisieren(null));
        bis.addValueChangeListener(ereignis -> aktualisieren(null));
    }

    private void aktualisieren(Long auswahl) {
        try {
            MitarbeiterInfo person = mitarbeiter.getValue();
            List<AntragAnsicht> antraege = antragService.zustaendigeAntraege(sitzung.mitarbeiterId(), sitzung.rolle(),
                    new AntragFilter(typ.getValue(), status.getValue(), person == null ? null : person.mitarbeiterId(),
                            von.getValue(), bis.getValue()));
            liste.setItems(antraege);
            antraege.stream().filter(ansicht -> ansicht.antrag().getId().equals(auswahl)).findFirst()
                    .ifPresentOrElse(liste::select, () -> detailsZeigen(null));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private Component listeneintrag(AntragAnsicht ansicht) {
        Span titel = new Span(ansicht.typ() == Antragstyp.KRANKHEIT ? "Krankmeldung" : ansicht.typ().bezeichnung());
        titel.getStyle().set("font-weight", "700");
        Span eingereicht = new Span(AntragTexte.tag(ansicht.antrag().getEingereichtAm()).format(AntragTexte.TAG_MONAT));
        HorizontalLayout kopf = new HorizontalLayout(titel, eingereicht);
        kopf.setWidthFull();
        kopf.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        String zeitraum = AntragTexte.zeitraum(ansicht.antrag(), ansicht.bezugsantrag());
        String zeile = ansicht.antragstellerName() + " · "
                + (ansicht.typ() == Antragstyp.ZEITKORREKTUR ? "Buchung " + zeitraum : zeitraum);
        if (ansicht.urlaubstage() != null) {
            zeile += " (" + ansicht.urlaubstage() + " Tage)";
        }
        Div eintrag = new Div(kopf, new Div(new Span(zeile)));
        if (ansicht.abwesendImTeam() != null && ansicht.abwesendImTeam() > 0) {
            Span team = new Span(teamhinweis(ansicht));
            team.getElement().getThemeList().add("badge warning small");
            eintrag.add(new Div(team));
        }
        return eintrag;
    }

    private void detailsZeigen(AntragAnsicht ansicht) {
        details.removeAll();
        details.setVisible(ansicht != null);
        if (ansicht == null) {
            return;
        }
        H3 titel = new H3(ansicht.typ().bezeichnung() + " · " + ansicht.antragstellerName());
        HorizontalLayout kopf = new HorizontalLayout(titel, AntragTexte.statusKennzeichen(ansicht, false));
        kopf.setWidthFull();
        kopf.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        Span unterzeile = new Span("Antrag A-" + ansicht.antrag().getId() + " · eingereicht "
                + AntragTexte.datumUhrzeit(ansicht.antrag().getEingereichtAm()));
        details.add(kopf, unterzeile, pruefdaten(ansicht));
        if (ansicht.antrag().getBegruendung() != null) {
            details.add(new Div(new Span("Begründung: " + ansicht.antrag().getBegruendung())));
        }
        if (ansicht.antrag().getStatus() == Antragsstatus.OFFEN) {
            details.add(entscheidungsbereich(ansicht));
        } else if (ansicht.entscheiderName() != null) {
            String text = "Entschieden von " + ansicht.entscheiderName() + " am "
                    + AntragTexte.datumUhrzeit(ansicht.antrag().getEntschiedenAm());
            String hinweis = ansicht.antrag().getEntscheidungshinweis();
            details.add(new Div(new Span(hinweis == null ? text : text + ": „" + hinweis + "“")));
        }
    }

    /** Typabhaengige Pruefdaten (US-M5-05: "Typabhaengige Pruefdaten werden angezeigt"). */
    private Component pruefdaten(AntragAnsicht ansicht) {
        if (ansicht.antrag() instanceof Zeitkorrekturantrag korrektur) {
            return vergleichstabelle(korrektur, ansicht);
        }
        List<Vergleich> zeilen = new ArrayList<>();
        if (ansicht.antrag() instanceof Abwesenheitsantrag abwesenheit) {
            if (abwesenheit.getUrlaubsart() != null) {
                zeilen.add(new Vergleich("Urlaubsart", abwesenheit.getUrlaubsart().bezeichnung(), ""));
            }
            zeilen.add(new Vergleich("Zeitraum", abwesenheit.getVon().format(AntragTexte.DATUM) + " – "
                    + abwesenheit.getBis().format(AntragTexte.DATUM), ""));
        }
        if (ansicht.urlaubstage() != null) {
            zeilen.add(new Vergleich("Urlaubstage", String.valueOf(ansicht.urlaubstage()), ""));
        }
        if (ansicht.teamgroesse() != null) {
            zeilen.add(new Vergleich("Team", teamhinweis(ansicht), ""));
        }
        if (ansicht.sondergenehmigung()) {
            zeilen.add(new Vergleich("Hinweis", "Sondergenehmigung: unbezahlter Urlaub", ""));
        }
        if (ansicht.bezugsantrag() != null) {
            String art = ansicht.typ() == Antragstyp.STORNO ? "Storniert" : "Ändert";
            zeilen.add(new Vergleich(art, "Antrag A-" + ansicht.bezugsantrag().getId() + " · "
                    + AntragTexte.zeitraum(ansicht.bezugsantrag(), null), ""));
        }
        Grid<Vergleich> tabelle = new Grid<>();
        tabelle.addColumn(Vergleich::feld).setHeader("Feld");
        tabelle.addColumn(Vergleich::bisher).setHeader("Angabe").setFlexGrow(2);
        tabelle.setItems(zeilen);
        tabelle.setAllRowsVisible(true);
        return tabelle;
    }

    private Grid<Vergleich> vergleichstabelle(Zeitkorrekturantrag korrektur, AntragAnsicht ansicht) {
        BuchungInfo buchung = ansicht.buchung();
        List<Vergleich> zeilen = List.of(
                new Vergleich("Datum", AntragTexte.tag(korrektur.getAlterBeginn()).format(AntragTexte.WOCHENTAG_DATUM),
                        AntragTexte.tag(korrektur.getNeuerBeginn()).format(AntragTexte.WOCHENTAG_DATUM)),
                new Vergleich("Beginn", AntragTexte.uhrzeit(korrektur.getAlterBeginn()),
                        AntragTexte.uhrzeit(korrektur.getNeuerBeginn())),
                new Vergleich("Ende", AntragTexte.uhrzeit(korrektur.getAltesEnde()),
                        AntragTexte.uhrzeit(korrektur.getNeuesEnde())),
                new Vergleich("Standort", AntragTexte.standort(korrektur.getAlterStandort()),
                        AntragTexte.standort(korrektur.getNeuerStandort())),
                new Vergleich("Projekt", ansicht.bisherigesProjekt() == null ? "–" : ansicht.bisherigesProjekt(),
                        ansicht.gewuenschtesProjekt()),
                new Vergleich("Netto", buchung == null ? "–" : AntragTexte.dauer(buchung.nettoMinuten()),
                        AntragTexte.dauer(ansicht.gewuenschteNettoMinuten())));
        Grid<Vergleich> tabelle = new Grid<>();
        tabelle.addColumn(Vergleich::feld).setHeader("Feld");
        tabelle.addColumn(Vergleich::bisher).setHeader("Bisher");
        tabelle.addColumn(Vergleich::gewuenscht).setHeader("Gewünscht");
        tabelle.setItems(zeilen);
        tabelle.setAllRowsVisible(true);
        return tabelle;
    }

    private Component entscheidungsbereich(AntragAnsicht ansicht) {
        TextArea hinweis = new TextArea("Entscheidungshinweis (optional)");
        hinweis.setPlaceholder("z. B. Grund für eine Ablehnung");
        hinweis.setMaxLength(255);
        hinweis.setWidthFull();
        long antragId = ansicht.antrag().getId();
        Button ablehnen = new Button("Ablehnen", ereignis -> entscheiden(antragId, false, hinweis.getValue()));
        ablehnen.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button genehmigen = new Button("Genehmigen", ereignis -> entscheiden(antragId, true, hinweis.getValue()));
        genehmigen.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        HorizontalLayout knoepfe = new HorizontalLayout(ablehnen, genehmigen);
        knoepfe.setWidthFull();
        knoepfe.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return new Div(hinweis, knoepfe);
    }

    private void entscheiden(long antragId, boolean genehmigt, String hinweis) {
        try {
            if (genehmigt) {
                entscheidungService.genehmigen(sitzung.mitarbeiterId(), sitzung.rolle(), antragId, hinweis);
                Meldungen.erfolg("Der Antrag wurde genehmigt.");
            } else {
                entscheidungService.ablehnen(sitzung.mitarbeiterId(), sitzung.rolle(), antragId, hinweis);
                Meldungen.erfolg("Der Antrag wurde abgelehnt.");
            }
            aktualisieren(antragId);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private static String teamhinweis(AntragAnsicht ansicht) {
        return "Im Zeitraum sind bereits " + ansicht.abwesendImTeam() + " von " + ansicht.teamgroesse()
                + " Teammitgliedern abwesend.";
    }

    private static String statusname(Antragsstatus status) {
        return switch (status) {
            case OFFEN -> "offen";
            case GENEHMIGT -> "genehmigt";
            case ABGELEHNT -> "abgelehnt";
            case ZURUECKGEZOGEN -> "zurückgezogen";
        };
    }
}
