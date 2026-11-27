package de.timecraft.zeiterfassung.view;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.model.AbwesenheitImKalender;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.service.AbwesenheitService;
import de.timecraft.zeiterfassung.service.FeiertagService;
import de.timecraft.zeiterfassung.view.Bausteine.Farbe;
import jakarta.annotation.security.PermitAll;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "abwesenheit", layout = HauptLayout.class)
@PageTitle("Abwesenheit")
@PermitAll
public class KalenderView extends Div {

    private static final DateTimeFormatter MONAT_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY);
    private static final DateTimeFormatter DATUM_FORMATTER = DateTimeFormatter.ofPattern("EE dd.MM.", Locale.GERMANY);

    private final AbwesenheitService abwesenheitService;
    private final FeiertagService feiertagService;
    private final AktuelleSitzung sitzung;
    private final Select<YearMonth> monatAuswahl = new Select<>();
    private final Span anzahlAbwesenheitenText = new Span();
    private final Span anzahlFeiertageText = new Span();
    private final Grid<AbwesenheitImKalender> abwesenheitTabelle = new Grid<>();
    private final Span keineAbwesenheitenText = new Span("Keine Abwesenheiten in diesem Monat.");
    private final Span feiertageTitel = new Span();
    private final Div feiertagListe = new Div();

    public KalenderView(AbwesenheitService abwesenheitService, FeiertagService feiertagService, AktuelleSitzung sitzung) {
        this.abwesenheitService = abwesenheitService;
        this.feiertagService = feiertagService;
        this.sitzung = sitzung;
        addClassName("m2-seite");

        YearMonth aktuellerMonat = feiertagService.aktuellerMonat();
        List<YearMonth> monate = new ArrayList<>();
        YearMonth letzterMonat = YearMonth.of(aktuellerMonat.getYear() + 1, 12);
        for (YearMonth monat = YearMonth.of(aktuellerMonat.getYear() - 1, 1); !monat.isAfter(letzterMonat);
                monat = monat.plusMonths(1)) {
            monate.add(monat);
        }
        monatAuswahl.setLabel("Monat");
        monatAuswahl.setWidth("14rem");
        monatAuswahl.setItems(monate);
        monatAuswahl.setItemLabelGenerator(monat -> monat.format(MONAT_FORMATTER));
        monatAuswahl.setValue(aktuellerMonat);
        monatAuswahl.addValueChangeListener(ereignis -> aktualisieren());

        Div kacheln = new Div(Bausteine.kachel("Abwesenheiten im Monat", anzahlAbwesenheitenText),
                Bausteine.kachel("Feiertage im Monat", anzahlFeiertageText));
        kacheln.addClassNames("m2-kacheln", "m2-kacheln-zwei");

        abwesenheitTabelle.addColumn(AbwesenheitImKalender::name).setHeader("Mitarbeiter");
        abwesenheitTabelle.addColumn(abwesenheit -> abwesenheit.von().format(DATUM_FORMATTER)).setHeader("Von");
        abwesenheitTabelle.addColumn(abwesenheit -> abwesenheit.bis().format(DATUM_FORMATTER)).setHeader("Bis");
        abwesenheitTabelle.addComponentColumn(abwesenheit -> artSchild(abwesenheit.art())).setHeader("Art");
        abwesenheitTabelle.addColumn(abwesenheit -> tage(abwesenheit.arbeitstage())).setHeader("Arbeitstage")
                .setTextAlign(ColumnTextAlign.END);
        abwesenheitTabelle.getColumns().forEach(spalte -> spalte.setAutoWidth(true));
        abwesenheitTabelle.setAllRowsVisible(true);
        abwesenheitTabelle.addClassName("m2-tabelle");
        keineAbwesenheitenText.addClassName("m2-status-seit");
        Div links = Bausteine.bereich("m2-spalte", Bausteine.titel("Abwesenheiten"), abwesenheitTabelle,
                keineAbwesenheitenText);

        feiertageTitel.addClassName("m2-titel");
        Div unten = Bausteine.bereich("m2-zweispaltig", links, Bausteine.karte(feiertageTitel, feiertagListe));

        Span hinweis = Bausteine.gedaempft("Bei Kolleginnen und Kollegen steht nur „abwesend“, die Art sehen nur "
                + "die betroffene und die vorgesetzte Person.");

        monatAuswahl.addClassName("m2-links");
        add(monatAuswahl, kacheln, unten, hinweis);
        aktualisieren();
    }

    private void aktualisieren() {
        try {
            YearMonth monat = monatAuswahl.getValue();
            List<AbwesenheitImKalender> abwesenheiten = abwesenheitService.kalender(sitzung.mitarbeiterId(), sitzung.rolle(), monat);
            List<Feiertag> feiertage = feiertagService.imMonat(sitzung.rolle(), monat);

            abwesenheitTabelle.setItems(abwesenheiten);
            abwesenheitTabelle.setVisible(!abwesenheiten.isEmpty());
            keineAbwesenheitenText.setVisible(abwesenheiten.isEmpty());
            anzahlAbwesenheitenText.setText(String.valueOf(abwesenheiten.size()));
            anzahlFeiertageText.setText(String.valueOf(feiertage.size()));

            feiertageTitel.setText("Feiertage " + monat.format(MONAT_FORMATTER));
            feiertagListe.removeAll();
            if (feiertage.isEmpty()) {
                feiertagListe.add(Bausteine.gedaempft("Keine Feiertage in diesem Monat."));
            }
            for (Feiertag feiertag : feiertage) {
                feiertagListe.add(feiertagZeile(feiertag));
            }
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private String tage(long anzahl) {
        return anzahl + (anzahl == 1 ? " Tag" : " Tage");
    }

    private Div feiertagZeile(Feiertag feiertag) {
        Div zeile = Bausteine.bereich("m2-wertzeile",
                Bausteine.gedaempft(feiertag.getDatum().format(DATUM_FORMATTER)), new Span(feiertag.getName()));
        zeile.addClassName("m2-linie-unten");
        return zeile;
    }

    private Span artSchild(AbwesenheitsArt art) {
        Farbe farbe = Farbe.GRAU;
        if (art == AbwesenheitsArt.URLAUB) {
            farbe = Farbe.BLAU;
        } else if (art == AbwesenheitsArt.KRANKHEIT) {
            farbe = Farbe.ORANGE;
        }
        return Bausteine.schild(AbwesenheitsArtText.text(art), farbe);
    }
}
