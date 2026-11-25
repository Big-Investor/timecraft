package de.timecraft.kalender.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.kalender.model.BuchungDetails;
import de.timecraft.kalender.model.HistorienEintrag;
import de.timecraft.kalender.service.BuchungAenderungService;
import de.timecraft.kalender.service.KalenderService;

/**
 * Detailfenster einer Buchung (FA-M6-05, 10): Projekt, Projekt-ID, Projektleitung, Standort, Beginn, Ende,
 * Dauer und Aenderungshistorie. „Bearbeiten“ erscheint nur, wenn der Service es erlaubt (UC-M6-05 1a, 1b);
 * die Pruefung beim Speichern macht der Service trotzdem noch einmal.
 *
 * @author Bennet Antonio Brinkmeier
 */
class BuchungDetailDialog extends Dialog {

    private final KalenderService kalenderService;
    private final BuchungAenderungService aenderungService;
    private final AktuelleSitzung sitzung;
    private final long zeiteintragId;
    private final Runnable nachAenderung;

    /** nachAenderung laedt den Kalender neu, nachdem die Buchung gespeichert wurde. */
    BuchungDetailDialog(KalenderService kalenderService, BuchungAenderungService aenderungService,
            AktuelleSitzung sitzung, long zeiteintragId, Runnable nachAenderung) {
        this.kalenderService = kalenderService;
        this.aenderungService = aenderungService;
        this.sitzung = sitzung;
        this.zeiteintragId = zeiteintragId;
        this.nachAenderung = nachAenderung;
        setHeaderTitle("Buchungsdetails");
        setWidth("52rem");
    }

    /** Laedt die Details und oeffnet das Fenster; ohne Recht erscheint nur die Meldung. */
    void oeffnen() {
        try {
            BuchungDetails details = kalenderService.details(sitzung.mitarbeiterId(), sitzung.rolle(), zeiteintragId);
            add(angaben(details), historie(details));
            Button schliessen = new Button("Schließen", klick -> close());
            if (aenderungService.darfAendern(sitzung.mitarbeiterId(), sitzung.rolle(), zeiteintragId)) {
                Button bearbeiten = new Button("Bearbeiten", klick -> bearbeiten());
                bearbeiten.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                getFooter().add(schliessen, bearbeiten);
            } else {
                getFooter().add(schliessen);
            }
            open();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void bearbeiten() {
        close();
        new BuchungAendernFormular(aenderungService, sitzung, zeiteintragId, nachAenderung).oeffnen();
    }

    private static Div angaben(BuchungDetails details) {
        Div raster = new Div();
        raster.getStyle().set("display", "grid").set("grid-template-columns", "10rem 1fr").set("gap", "0.5rem 1rem");
        zeile(raster, "Projekt", details.projektName());
        zeile(raster, "Projekt-ID", KalenderTexte.projektKennung(details.projektId()));
        zeile(raster, "Projektleitung", details.projektleitung());
        zeile(raster, "Standort", KalenderTexte.standort(details.standort()));
        zeile(raster, "Beginn", KalenderTexte.datumUhrzeit(details.beginn()));
        zeile(raster, "Ende", details.laeuft() ? "läuft" : KalenderTexte.datumUhrzeit(details.ende()));
        zeile(raster, "Dauer", KalenderTexte.dauer(details.dauerMinuten()) + (details.laeuft() ? " (bis jetzt)" : ""));
        if (details.geaendert()) {
            zeile(raster, "Status", "geändert");
        }
        return raster;
    }

    private static void zeile(Div raster, String beschriftung, String wert) {
        Span links = new Span(beschriftung);
        links.getStyle().set("font-weight", "700");
        raster.add(links, new Span(wert));
    }

    private static Div historie(BuchungDetails details) {
        H3 ueberschrift = new H3("Änderungshistorie");
        ueberschrift.getStyle().set("font-size", "1rem").set("margin", "1.5rem 0 0.5rem");
        Div bereich = new Div(ueberschrift);
        if (details.historie().isEmpty()) {
            bereich.add(new Span("Diese Buchung wurde nicht geändert."));
            return bereich;
        }
        Grid<HistorienEintrag> tabelle = new Grid<>();
        tabelle.addColumn(e -> KalenderTexte.datumUhrzeit(e.zeitpunkt())).setHeader("Zeitpunkt").setAutoWidth(true);
        tabelle.addColumn(HistorienEintrag::geaendertVon).setHeader("Geändert von").setAutoWidth(true);
        tabelle.addColumn(HistorienEintrag::feld).setHeader("Feld").setAutoWidth(true);
        tabelle.addColumn(HistorienEintrag::alterWert).setHeader("Alter Wert").setAutoWidth(true);
        tabelle.addColumn(HistorienEintrag::neuerWert).setHeader("Neuer Wert").setAutoWidth(true);
        tabelle.addColumn(HistorienEintrag::begruendung).setHeader("Begründung").setFlexGrow(1);
        tabelle.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT, GridVariant.LUMO_COMPACT);
        tabelle.setAllRowsVisible(true);
        tabelle.setItems(details.historie());
        bereich.add(tabelle);
        return bereich;
    }
}
