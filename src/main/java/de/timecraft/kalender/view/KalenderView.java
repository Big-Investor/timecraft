package de.timecraft.kalender.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.kalender.model.ProjektAuswahl;
import de.timecraft.kalender.model.WaehlbarePerson;
import de.timecraft.kalender.service.BuchungAenderungService;
import de.timecraft.kalender.service.KalenderService;
import de.timecraft.zeiterfassung.api.Standort;
import jakarta.annotation.security.PermitAll;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Kalender (FA-M6-01 bis 07): eigene Buchungen, fuer Projektleitung und Admin auch die einer gewaehlten
 * Person. Haelt nur den Anzeigezustand (Ansicht, Bezugstag, Filter, Person); Rechte, Summen und
 * Zeitraeume kommen aus dem KalenderService. Aufbau nach dem Mockup „Kalender – Wochenansicht“.
 */
@Route(value = KalenderView.ROUTE, layout = HauptLayout.class)
@PageTitle("Kalender")
@PermitAll
public class KalenderView extends Div {

    static final String ROUTE = "kalender";

    private final KalenderService kalenderService;
    private final BuchungAenderungService aenderungService;
    private final AktuelleSitzung sitzung;

    private final Map<Kalenderansicht, Button> ansichtKnoepfe = new EnumMap<>(Kalenderansicht.class);
    private final Span zeitraum = new Span();
    private final H2 kalenderVon = new H2();
    private final Select<WaehlbarePerson> personAuswahl = new Select<>();
    private final Button meinKalender = new Button("Mein Kalender", klick -> meinenKalenderZeigen());
    private final Select<ProjektAuswahl> projektFilter = new Select<>();
    private final Select<Standort> standortFilter = new Select<>();
    private final Span projekthinweis = new Span("Sie sehen nur Buchungen auf Ihre Projekte.");
    private final Div inhaltBereich = new Div();
    private final Span fusszeile = new Span();

    private Kalenderansicht ansicht = Kalenderansicht.WOCHE;
    private LocalDate bezugstag;
    private WaehlbarePerson person;

    public KalenderView(KalenderService kalenderService, BuchungAenderungService aenderungService,
            AktuelleSitzung sitzung) {
        this.kalenderService = kalenderService;
        this.aenderungService = aenderungService;
        this.sitzung = sitzung;
        this.bezugstag = kalenderService.heute();
        getStyle().set("padding", "1.5rem").set("display", "flex").set("flex-direction", "column")
                .set("gap", "1rem");
        kalenderVon.getStyle().set("margin", "0").set("font-size", "1.25rem");
        kalenderVon.setVisible(false);
        add(kalenderVon, navigationsleiste(), filterleiste(), inhaltBereich, fusszeile);
        laden();
    }

    // ---------- Aufbau ----------

    private Div navigationsleiste() {
        Div ansichten = new Div();
        ansichten.getStyle().set("display", "flex");
        ansichtKnopf(ansichten, Kalenderansicht.TAG, "Tag");
        ansichtKnopf(ansichten, Kalenderansicht.WOCHE, "Woche");
        ansichtKnopf(ansichten, Kalenderansicht.MONAT, "Monat");
        ansichtKnoepfe.get(Kalenderansicht.TAG).getStyle().set("border-radius", "0.5rem 0 0 0.5rem");
        ansichtKnoepfe.get(Kalenderansicht.MONAT).getStyle().set("border-radius", "0 0.5rem 0.5rem 0");

        Button zurueck = rahmenKnopf(new Button("‹", klick -> blaettern(kalenderService.zurueck(ansicht, bezugstag))));
        zurueck.setAriaLabel("Zurück");
        zurueck.setTooltipText("Zurück");
        Button heute = rahmenKnopf(new Button("Heute", klick -> blaettern(kalenderService.heute())));
        Button vor = rahmenKnopf(new Button("›", klick -> blaettern(kalenderService.vor(ansicht, bezugstag))));
        vor.setAriaLabel("Vor");
        vor.setTooltipText("Vor");
        zeitraum.getStyle().set("font-weight", "700").set("font-size", "1.125rem").set("margin-left", "0.5rem");

        Div leiste = leiste(ansichten, zurueck, heute, vor, zeitraum);
        if (sitzung.rolle() != Rolle.MITARBEITER) {
            Span abstand = new Span();
            abstand.getStyle().set("flex", "1");
            leiste.add(abstand, personWahl());
        }
        return leiste;
    }

    private void ansichtKnopf(Div gruppe, Kalenderansicht zielAnsicht, String text) {
        Button knopf = rahmenKnopf(new Button(text, klick -> {
            ansicht = zielAnsicht;
            laden();
        }));
        knopf.getStyle().set("margin", "0").set("border-radius", "0");
        ansichtKnoepfe.put(zielAnsicht, knopf);
        gruppe.add(knopf);
    }

    /** „Person wählen“ für Projektleitung und Admin (FA-M6-07, UC-M6-04). */
    private Component personWahl() {
        Span beschriftung = new Span("Person");
        beschriftung.getStyle().set("font-weight", "700").set("font-size", "0.875rem");
        personAuswahl.setPlaceholder("Person wählen");
        personAuswahl.setItemLabelGenerator(WaehlbarePerson::name);
        personAuswahl.setAriaLabel("Person wählen");
        meinKalender.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        meinKalender.getStyle().set("text-decoration", "underline");
        Div bereich = leiste(beschriftung, personAuswahl, meinKalender);
        try {
            List<WaehlbarePerson> personen = kalenderService.waehlbarePersonen(sitzung.mitarbeiterId(), sitzung.rolle());
            personAuswahl.setItems(personen);
            if (personen.isEmpty()) {
                personAuswahl.setEnabled(false);
                Span hinweis = new Span("Ihren aktiven Projekten ist niemand zugeordnet.");
                hinweis.getStyle().set("font-size", "0.875rem").set("color", "var(--tc-text-gedaempft)");
                bereich.add(hinweis);
            }
        } catch (RuntimeException fehler) {
            personAuswahl.setEnabled(false);
            Meldungen.fehler(fehler);
        }
        personAuswahl.addValueChangeListener(ereignis -> {
            if (ereignis.isFromClient() && ereignis.getValue() != null) {
                personZeigen(ereignis.getValue());
            }
        });
        return bereich;
    }

    /** Filter nach Projekt und Standort, gemeinsam wirksam und zurücksetzbar (FA-M6-06). */
    private Div filterleiste() {
        Span beschriftung = new Span("Filter:");
        beschriftung.getStyle().set("font-weight", "700").set("font-size", "0.875rem");
        projektFilter.setEmptySelectionAllowed(true);
        projektFilter.setEmptySelectionCaption("Alle Projekte");
        projektFilter.setItemLabelGenerator(projekt -> projekt == null ? "Alle Projekte" : projekt.name());
        projektFilter.setAriaLabel("Projekt");
        projektFilter.setWidth("13rem");
        standortFilter.setItems(Standort.values());
        standortFilter.setEmptySelectionAllowed(true);
        standortFilter.setEmptySelectionCaption("Alle Standorte");
        standortFilter.setItemLabelGenerator(standort -> standort == null ? "Alle Standorte"
                : KalenderTexte.standort(standort));
        standortFilter.setAriaLabel("Standort");
        projektFilter.addValueChangeListener(ereignis -> {
            if (ereignis.isFromClient()) {
                laden();
            }
        });
        standortFilter.addValueChangeListener(ereignis -> {
            if (ereignis.isFromClient()) {
                laden();
            }
        });
        Button zuruecksetzen = new Button("Filter zurücksetzen", klick -> {
            filterLeeren();
            laden();
        });
        zuruecksetzen.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        projekthinweis.getStyle().set("font-size", "0.875rem").set("color", "var(--tc-text-gedaempft)");
        return leiste(beschriftung, projektFilter, standortFilter, zuruecksetzen, projekthinweis);
    }

    // ---------- Aktionen ----------

    private void blaettern(LocalDate neuerBezugstag) {
        bezugstag = neuerBezugstag;
        laden();
    }

    private void personZeigen(WaehlbarePerson gewaehlt) {
        person = gewaehlt;
        filterLeeren();
        laden();
    }

    private void meinenKalenderZeigen() {
        person = null;
        personAuswahl.clear();
        filterLeeren();
        laden();
    }

    private void filterLeeren() {
        projektFilter.clear();
        standortFilter.clear();
    }

    private void buchungOeffnen(long zeiteintragId) {
        new BuchungDetailDialog(kalenderService, aenderungService, sitzung, zeiteintragId, this::laden).oeffnen();
    }

    /** Laedt den Zeitraum neu und baut Kopf, Raster und Summen auf. */
    private void laden() {
        KalenderFilter filter = new KalenderFilter(
                projektFilter.getValue() == null ? null : projektFilter.getValue().projektId(),
                standortFilter.getValue());
        try {
            KalenderInhalt inhalt = person == null
                    ? kalenderService.eigenerKalender(sitzung.mitarbeiterId(), sitzung.rolle(), ansicht, bezugstag, filter)
                    : kalenderService.fremderKalender(sitzung.mitarbeiterId(), sitzung.rolle(),
                            person.mitarbeiterId(), ansicht, bezugstag, filter);
            anzeigen(inhalt);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
            if (person != null) {
                meinenKalenderZeigen();
            }
        }
    }

    private void anzeigen(KalenderInhalt inhalt) {
        ansichtKnoepfe.forEach((art, knopf) -> knopf.getStyle()
                .set("background", art == ansicht ? "var(--lumo-primary-color)" : "#ffffff")
                .set("color", art == ansicht ? "#ffffff" : "var(--tc-text)")
                .set("font-weight", art == ansicht ? "700" : "400"));
        zeitraum.setText(KalenderTexte.zeitraum(ansicht, inhalt.ersterTag(), inhalt.letzterTag()));
        kalenderVon.setVisible(person != null);
        kalenderVon.setText(person == null ? "" : "Kalender von " + person.name());
        meinKalender.setVisible(person != null);
        projekthinweis.setVisible(person != null && sitzung.rolle() == Rolle.PROJEKTLEITUNG);
        projektauswahlSetzen(inhalt.projekte());

        inhaltBereich.removeAll();
        if (inhalt.keineTreffer()) {
            Span hinweis = new Span("Keine Buchungen gefunden");
            hinweis.getStyle().set("display", "block").set("margin-bottom", "0.75rem")
                    .set("color", "var(--tc-text-gedaempft)");
            inhaltBereich.add(hinweis);
        }
        LocalDate heute = kalenderService.heute();
        inhaltBereich.add(ansicht == Kalenderansicht.MONAT
                ? new MonatsRaster(inhalt, heute)
                : new KalenderRaster(inhalt, heute, this::buchungOeffnen));
        fusszeileSetzen(inhalt);
    }

    /** Projekte des Zeitraums; ein gewaehltes Projekt bleibt in der Liste, auch wenn es hier fehlt. */
    private void projektauswahlSetzen(List<ProjektAuswahl> projekte) {
        ProjektAuswahl gewaehlt = projektFilter.getValue();
        List<ProjektAuswahl> eintraege = new ArrayList<>(projekte);
        if (gewaehlt != null && !eintraege.contains(gewaehlt)) {
            eintraege.add(gewaehlt);
        }
        projektFilter.setItems(eintraege);
        projektFilter.setValue(gewaehlt);
    }

    private void fusszeileSetzen(KalenderInhalt inhalt) {
        fusszeile.removeAll();
        if (ansicht == Kalenderansicht.MONAT) {
            fusszeile.setVisible(false);
            return;
        }
        fusszeile.setVisible(true);
        Span summe = new Span((ansicht == Kalenderansicht.WOCHE ? "Wochensumme: " : "Tagessumme: ")
                + KalenderTexte.dauer(inhalt.gesamtMinuten()));
        summe.getStyle().set("font-weight", "700");
        Span hinweis = new Span(" · Klick auf eine Buchung öffnet die Details");
        hinweis.getStyle().set("color", "var(--tc-text-gedaempft)");
        fusszeile.add(summe, hinweis);
    }

    // ---------- Hilfen ----------

    private static Div leiste(Component... inhalt) {
        Div leiste = new Div(inhalt);
        leiste.getStyle().set("display", "flex").set("align-items", "center").set("gap", "0.75rem")
                .set("flex-wrap", "wrap");
        return leiste;
    }

    /** Weisser Knopf mit Rahmen wie im Mockup. */
    private static Button rahmenKnopf(Button knopf) {
        knopf.getStyle().set("background", "#ffffff").set("color", "var(--tc-text)")
                .set("border", "1px solid #c5ccd6").set("margin", "0");
        return knopf;
    }
}
