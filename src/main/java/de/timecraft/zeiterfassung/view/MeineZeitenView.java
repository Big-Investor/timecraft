package de.timecraft.zeiterfassung.view;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.UebersichtEintrag;
import de.timecraft.zeiterfassung.model.UebersichtTag;
import de.timecraft.zeiterfassung.model.UebersichtWoche;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.model.Zeitenuebersicht;
import de.timecraft.zeiterfassung.service.EingabeFehler;
import de.timecraft.zeiterfassung.service.EingabeFehler.Feld;
import de.timecraft.zeiterfassung.service.MitarbeiterAuswahlService;
import de.timecraft.zeiterfassung.service.ZeiteintragService;
import de.timecraft.zeiterfassung.view.Bausteine.Farbe;
import jakarta.annotation.security.RolesAllowed;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "meine-zeiten", layout = HauptLayout.class)
@PageTitle("Meine Zeiten")
@RolesAllowed({ "MITARBEITER", "PROJEKTLEITUNG" })
public class MeineZeitenView extends Div {

    private static final DateTimeFormatter DATUM_FORMATTER = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMANY);

    /** Eine Tabellenzeile: Zeiteintrag, Markierung (Feiertag, Abwesenheit) oder Summe eines Tages bzw. einer Woche. */
    private record Zeile(LocalDate datum, UebersichtEintrag eintrag, String markierung, String sollText,
            String summenTitel, Duration summe) {

        static Zeile eintrag(LocalDate datum, UebersichtEintrag eintrag) {
            return new Zeile(datum, eintrag, null, null, null, null);
        }

        static Zeile markierung(LocalDate datum, String markierung, String sollText) {
            return new Zeile(datum, null, markierung, sollText, null, null);
        }

        static Zeile summe(String titel, Duration summe) {
            return new Zeile(null, null, null, null, titel, summe);
        }
    }

    private final ZeiteintragService zeiteintragService;
    private final AktuelleSitzung sitzung;
    private final DateTimeFormatter uhrzeitFormatter;
    private final MitarbeiterAuswahlFeld mitarbeiterAuswahl = new MitarbeiterAuswahlFeld();
    private final DatePicker vonFeld = new DatePicker("Von");
    private final DatePicker bisFeld = new DatePicker("Bis");
    private final Button filternKnopf = new Button("Filtern", ereignis -> aktualisieren());
    private final Grid<Zeile> tabelle = new Grid<>();
    private final Span keineTrefferText = new Span("Keine Zeiteinträge im gewählten Zeitraum.");

    private final Button nachtragenKnopf = new Button("+ Zeit nachtragen", ereignis -> formularZeigen(true));
    private final Div formular = new Div();
    private final DatePicker datumFeld = new DatePicker("Datum");
    private final ZeitFeld beginnFeld = new ZeitFeld("Beginn");
    private final ZeitFeld endeFeld = new ZeitFeld("Ende");
    private final ZeitFeld pauseVonFeld = new ZeitFeld("Pause von");
    private final ZeitFeld pauseBisFeld = new ZeitFeld("Pause bis");
    private final Select<Standort> standortAuswahl = new Select<>();
    private final Select<ProjektInfo> projektAuswahl = new Select<>();
    private final Span fehlerText = new Span();
    private final Button speichernKnopf = new Button("Speichern", ereignis -> nachtragen());
    private final Button abbrechenKnopf = new Button("Abbrechen", ereignis -> formularZeigen(false));

    public MeineZeitenView(ZeiteintragService zeiteintragService, MitarbeiterAuswahlService auswahlService,
            AktuelleSitzung sitzung, Clock clock) {
        this.zeiteintragService = zeiteintragService;
        this.sitzung = sitzung;
        this.uhrzeitFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clock.getZone());
        addClassName("m2-seite");

        LocalDate heute = zeiteintragService.heute();
        vonFeld.setLocale(Locale.GERMANY);
        bisFeld.setLocale(Locale.GERMANY);
        vonFeld.setManualValidation(true);
        bisFeld.setManualValidation(true);
        vonFeld.setValue(heute.with(DayOfWeek.MONDAY));
        bisFeld.setValue(heute);
        nachtragenKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nachtragenKnopf.addClassName("m2-rechts");
        filternKnopf.addClassName("m2-knopf-weiss");
        HorizontalLayout filterZeile = new HorizontalLayout(mitarbeiterAuswahl, vonFeld, bisFeld, filternKnopf,
                nachtragenKnopf);
        filterZeile.setWidthFull();
        filterZeile.setAlignItems(FlexComponent.Alignment.END);

        tabelleAufbauen();
        keineTrefferText.addClassName("m2-status-seit");

        formularAufbauen();
        add(filterZeile, tabelle, keineTrefferText, formular);
        mitarbeiterAuswahl.laden(auswahlService, sitzung);
        mitarbeiterAuswahl.addValueChangeListener(ereignis -> mitarbeiterWechseln());
        aktualisieren();
    }

    private void tabelleAufbauen() {
        spalte(tabelle.addComponentColumn(this::datumZelle), "Datum");
        spalte(tabelle.addComponentColumn(this::standortZelle), "Standort");
        spalte(tabelle.addComponentColumn(this::projektZelle), "Projekt").setFlexGrow(1);
        spalte(tabelle.addColumn(zeile -> zeile.eintrag() == null ? ""
                : uhrzeitFormatter.format(zeile.eintrag().eintrag().getBeginn())), "Beginn");
        spalte(tabelle.addComponentColumn(this::endeZelle), "Ende");
        spalte(tabelle.addColumn(zeile -> zeile.eintrag() == null ? "" : DauerFormat.text(zeile.eintrag().pausen())),
                "Pausen");
        spalte(tabelle.addComponentColumn(this::nettoZelle), "Netto").setTextAlign(ColumnTextAlign.END);
        tabelle.setPartNameGenerator(this::zeilenKlasse);
        tabelle.setAllRowsVisible(true);
        tabelle.addClassName("m2-tabelle");
    }

    /** Jede Spalte ist so breit wie ihr Inhalt, damit bei 1280 px nichts abgeschnitten wird. */
    private Grid.Column<Zeile> spalte(Grid.Column<Zeile> spalte, String titel) {
        return spalte.setHeader(titel).setAutoWidth(true).setFlexGrow(0);
    }

    private String zeilenKlasse(Zeile zeile) {
        if (zeile.summe() != null) {
            return "m2-zeile-summe";
        }
        if (zeile.markierung() != null) {
            return "m2-zeile-markierung";
        }
        return zeile.eintrag().eintrag().getEnde() == null ? "m2-zeile-laufend" : null;
    }

    /** Fremde Zeiten sind nur lesbar (FA-M2-10), dort gibt es kein Nachtragen. */
    private void mitarbeiterWechseln() {
        formularZeigen(false);
        nachtragenKnopf.setVisible(gewaehlteId() == sitzung.mitarbeiterId());
        aktualisieren();
    }

    private long gewaehlteId() {
        return mitarbeiterAuswahl.gewaehlteId(sitzung.mitarbeiterId());
    }

    private void formularAufbauen() {
        datumFeld.setLocale(Locale.GERMANY);
        datumFeld.setRequiredIndicatorVisible(true);
        beginnFeld.setRequiredIndicatorVisible(true);
        endeFeld.setRequiredIndicatorVisible(true);
        standortAuswahl.setLabel("Standort");
        standortAuswahl.setRequiredIndicatorVisible(true);
        standortAuswahl.setItems(Standort.values());
        standortAuswahl.setItemLabelGenerator(StandortText::text);
        projektAuswahl.setLabel("Projekt");
        projektAuswahl.setEmptySelectionAllowed(true);
        projektAuswahl.setEmptySelectionCaption("ohne Projekt");
        projektAuswahl.setItemLabelGenerator(ProjektText::auswahlText);
        speichernKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        abbrechenKnopf.addClassName("m2-knopf-weiss");
        fehlerText.addClassName("m2-fehler");
        datumFeld.setManualValidation(true);
        standortAuswahl.setManualValidation(true);
        projektAuswahl.setManualValidation(true);

        HorizontalLayout felder = new HorizontalLayout(datumFeld, beginnFeld, endeFeld, pauseVonFeld, pauseBisFeld,
                standortAuswahl, projektAuswahl);
        felder.setWidthFull();
        felder.addClassName("m2-felder");
        HorizontalLayout knoepfe = new HorizontalLayout(abbrechenKnopf, speichernKnopf);
        knoepfe.setWidthFull();
        knoepfe.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        formular.addClassNames("karte", "m2-karte");
        formular.add(Bausteine.titel("Zeit nachtragen"), felder, fehlerText, knoepfe);
        formular.setVisible(false);
    }

    private void nachtragen() {
        fehlerZuruecksetzen();
        List<ZeitFeld> unlesbare = Stream.of(beginnFeld, endeFeld, pauseVonFeld, pauseBisFeld)
                .filter(ZeitFeld::unlesbar).toList();
        if (!unlesbare.isEmpty()) {
            fehlerText.setText("Bitte geben Sie Uhrzeiten wie 07:30 ein.");
            unlesbare.forEach(feld -> feld.setInvalid(true));
            return;
        }
        Zeiteintrag eintrag;
        try {
            ProjektInfo projekt = projektAuswahl.getValue();
            eintrag = zeiteintragService.nachtragen(sitzung.mitarbeiterId(), sitzung.rolle(), datumFeld.getValue(),
                    beginnFeld.getValue(), endeFeld.getValue(), pauseVonFeld.getValue(), pauseBisFeld.getValue(),
                    standortAuswahl.getValue(), projekt == null ? null : projekt.projektId());
        } catch (EingabeFehler fehler) {
            eingabeFehlerZeigen(fehler);
            return;
        } catch (RuntimeException fehler) {
            fehlerText.setText(Meldungen.fehlertext(fehler));
            return;
        }
        Meldungen.erfolg("Zeit nachgetragen");
        formularZeigen(false);
        aktualisieren();
        hinweisZeigen(eintrag);
    }

    private void eingabeFehlerZeigen(EingabeFehler fehler) {
        if (fehler.felder().contains(Feld.PROJEKT)) {
            projekteLaden();
        }
        fehlerText.setText(fehler.getMessage());
        fehler.felder().forEach(feld -> formularFelder().get(feld).setInvalid(true));
    }

    private Map<Feld, HasValidation> formularFelder() {
        return Map.of(Feld.DATUM, datumFeld, Feld.BEGINN, beginnFeld, Feld.ENDE, endeFeld,
                Feld.PAUSE_VON, pauseVonFeld, Feld.PAUSE_BIS, pauseBisFeld, Feld.STANDORT, standortAuswahl,
                Feld.PROJEKT, projektAuswahl);
    }

    private void fehlerZuruecksetzen() {
        fehlerText.setText("");
        formularFelder().values().forEach(feld -> feld.setInvalid(false));
    }

    /** Der Eintrag ist schon gespeichert, ein Fehler hier darf das Formular nicht wieder oeffnen. */
    private void hinweisZeigen(Zeiteintrag eintrag) {
        try {
            Hinweis.zeigen(zeiteintragService.hinweis(sitzung.mitarbeiterId(), sitzung.rolle(), eintrag));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void formularZeigen(boolean sichtbar) {
        formular.setVisible(sichtbar);
        fehlerZuruecksetzen();
        if (sichtbar) {
            projekteLaden();
        } else {
            datumFeld.clear();
            beginnFeld.clear();
            endeFeld.clear();
            pauseVonFeld.clear();
            pauseBisFeld.clear();
            standortAuswahl.clear();
            projektAuswahl.clear();
        }
    }

    /** Ohne zugeordnetes aktives Projekt gibt es keine Projektauswahl. */
    private void projekteLaden() {
        try {
            List<ProjektInfo> projekte = zeiteintragService.aktiveProjekte(sitzung.mitarbeiterId(), sitzung.rolle());
            projektAuswahl.setItems(projekte);
            projektAuswahl.setVisible(!projekte.isEmpty());
        } catch (RuntimeException fehler) {
            projektAuswahl.setVisible(false);
            Meldungen.fehler(fehler);
        }
    }

    private void aktualisieren() {
        vonFeld.setInvalid(false);
        bisFeld.setInvalid(false);
        try {
            Zeitenuebersicht uebersicht = zeiteintragService.uebersicht(sitzung.mitarbeiterId(), sitzung.rolle(),
                    gewaehlteId(), vonFeld.getValue(), bisFeld.getValue());
            tabelle.setItems(zeilen(uebersicht));
            tabelle.recalculateColumnWidths();
            keineTrefferText.setVisible(uebersicht.ohneZeiteintraege());
        } catch (EingabeFehler fehler) {
            tabelleLeeren();
            if (fehler.felder().contains(Feld.VON)) {
                amFeldMelden(vonFeld, fehler.getMessage());
            }
            if (fehler.felder().contains(Feld.BIS)) {
                amFeldMelden(bisFeld, fehler.getMessage());
            }
        } catch (RuntimeException fehler) {
            tabelleLeeren();
            Meldungen.fehler(fehler);
        }
    }

    /** Keine alten Zeilen stehen lassen, sie koennten zu einer anderen Person oder einem anderen Zeitraum gehoeren. */
    private void tabelleLeeren() {
        tabelle.setItems(List.of());
        keineTrefferText.setVisible(false);
    }

    private void amFeldMelden(DatePicker feld, String meldung) {
        feld.setErrorMessage(meldung);
        feld.setInvalid(true);
    }

    /** Eine Tagessumme steht nur bei mehreren Eintraegen da, sonst ist sie schon die Spalte Netto. */
    private List<Zeile> zeilen(Zeitenuebersicht uebersicht) {
        List<Zeile> zeilen = new ArrayList<>();
        for (UebersichtWoche woche : uebersicht.wochen()) {
            for (UebersichtTag tag : woche.tage()) {
                if (tag.feiertag() != null) {
                    zeilen.add(Zeile.markierung(tag.datum(), tag.feiertag(), "Feiertag, Soll 0"));
                }
                if (tag.abwesenheit() != null) {
                    zeilen.add(Zeile.markierung(tag.datum(),
                            AbwesenheitsArtText.text(tag.abwesenheit().art()) + " (genehmigt)",
                            "Soll gilt als erfüllt"));
                }
                for (UebersichtEintrag eintrag : tag.eintraege()) {
                    zeilen.add(Zeile.eintrag(tag.datum(), eintrag));
                }
                if (tag.eintraege().size() > 1) {
                    zeilen.add(Zeile.summe("Summe " + tag.datum().format(DATUM_FORMATTER), tag.summe()));
                }
            }
            zeilen.add(Zeile.summe("Summe Woche " + woche.kalenderwoche(), woche.summe()));
        }
        if (uebersicht.wochen().size() > 1) {
            zeilen.add(Zeile.summe("Summe gesamt", uebersicht.summe()));
        }
        return zeilen;
    }

    private Span datumZelle(Zeile zeile) {
        if (zeile.summe() != null) {
            return Bausteine.fett(zeile.summenTitel());
        }
        return new Span(zeile.datum().format(DATUM_FORMATTER));
    }

    private Span standortZelle(Zeile zeile) {
        if (zeile.markierung() != null) {
            return Bausteine.schild(zeile.markierung(), Farbe.BLAU);
        }
        if (zeile.eintrag() == null) {
            return new Span("");
        }
        return new Span(StandortText.text(zeile.eintrag().eintrag().getStandort()));
    }

    private Span projektZelle(Zeile zeile) {
        if (zeile.eintrag() == null) {
            return Bausteine.gedaempft(zeile.sollText() == null ? "" : zeile.sollText());
        }
        Long projektId = zeile.eintrag().eintrag().getProjektId();
        if (projektId == null) {
            return new Span("–");
        }
        return new Span(ProjektText.listenText(projektId, zeile.eintrag().projektName()));
    }

    private Span endeZelle(Zeile zeile) {
        if (zeile.eintrag() == null) {
            return new Span("");
        }
        if (zeile.eintrag().eintrag().getEnde() == null) {
            return Bausteine.schild("läuft", Farbe.GRUEN);
        }
        return new Span(uhrzeitFormatter.format(zeile.eintrag().eintrag().getEnde()));
    }

    private Span nettoZelle(Zeile zeile) {
        if (zeile.summe() != null) {
            return Bausteine.fett(DauerFormat.text(zeile.summe()));
        }
        if (zeile.eintrag() == null) {
            return new Span("");
        }
        Span netto = Bausteine.fett(DauerFormat.text(zeile.eintrag().netto()));
        int fehlendePause = zeile.eintrag().fehlendePauseMinuten();
        if (fehlendePause == 0) {
            return netto;
        }
        Span hinweis = Bausteine.schild("−" + DauerFormat.text(Duration.ofMinutes(fehlendePause)) + " Pause",
                Farbe.ORANGE);
        hinweis.addClassName("m2-abstand-links");
        return new Span(netto, hinweis);
    }
}
