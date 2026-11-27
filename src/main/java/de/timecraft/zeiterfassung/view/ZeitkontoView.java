package de.timecraft.zeiterfassung.view;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.zeiterfassung.model.Urlaubskonto;
import de.timecraft.zeiterfassung.model.ZeitkontoMonat;
import de.timecraft.zeiterfassung.model.ZeitkontoZeile;
import de.timecraft.zeiterfassung.model.Zeitkontouebersicht;
import de.timecraft.zeiterfassung.service.AbwesenheitService;
import de.timecraft.zeiterfassung.service.MitarbeiterAuswahlService;
import de.timecraft.zeiterfassung.service.ZeitkontoService;
import jakarta.annotation.security.RolesAllowed;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "zeitkonto", layout = HauptLayout.class)
@PageTitle("Zeitkonto & Urlaub")
@RolesAllowed({ "MITARBEITER", "PROJEKTLEITUNG" })
public class ZeitkontoView extends Div {

    private static final DateTimeFormatter MONAT_FORMATTER = DateTimeFormatter.ofPattern("MMMM", Locale.GERMANY);
    private static final DateTimeFormatter TAG_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.");

    private final ZeitkontoService zeitkontoService;
    private final AbwesenheitService abwesenheitService;
    private final AktuelleSitzung sitzung;

    private final MitarbeiterAuswahlFeld mitarbeiterAuswahl = new MitarbeiterAuswahlFeld();
    private final Div kacheln = new Div();
    private final Div unten = new Div();

    public ZeitkontoView(ZeitkontoService zeitkontoService, AbwesenheitService abwesenheitService,
            MitarbeiterAuswahlService auswahlService, AktuelleSitzung sitzung) {
        this.zeitkontoService = zeitkontoService;
        this.abwesenheitService = abwesenheitService;
        this.sitzung = sitzung;
        addClassName("m2-seite");
        kacheln.addClassName("m2-kacheln");
        unten.addClassName("m2-zweispaltig");
        mitarbeiterAuswahl.addClassName("m2-links");
        add(mitarbeiterAuswahl, kacheln, unten);
        mitarbeiterAuswahl.laden(auswahlService, sitzung);
        mitarbeiterAuswahl.addValueChangeListener(ereignis -> anzeigen());
        anzeigen();
    }

    private void anzeigen() {
        kacheln.removeAll();
        unten.removeAll();
        try {
            kontoZeigen(mitarbeiterAuswahl.gewaehlteId(sitzung.mitarbeiterId()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /** Das Urlaubskonto gibt es nur fuer die eigene Person. */
    private void kontoZeigen(long mitarbeiterId) {
        Zeitkontouebersicht uebersicht = zeitkontoService.jahresuebersicht(sitzung.mitarbeiterId(), sitzung.rolle(),
                mitarbeiterId);
        ZeitkontoMonat aktuell = uebersicht.monate().get(uebersicht.monate().size() - 1).konto();
        String monatsname = aktuell.monat().format(MONAT_FORMATTER);

        kacheln.add(
                Bausteine.kachel("Soll " + monatsname + " (bis gestern)", new Span(stunden(aktuell.sollMinuten()))),
                Bausteine.kachel("Ist " + monatsname, new Span(stunden(aktuell.istMinuten()))),
                Bausteine.kachel("Saldo " + monatsname, Bausteine.text(DauerFormat.saldo(aktuell.saldoMonatMinuten()) + " h",
                        farbKlasse(aktuell.saldoMonatMinuten()))),
                gesamtsaldoKachel(aktuell.gesamtsaldoMinuten()));

        unten.add(monatsKarte(uebersicht));
        if (mitarbeiterId == sitzung.mitarbeiterId()) {
            unten.add(urlaubsKarte(aktuell.monat().getYear()));
        }
    }

    private record Zeile(String monat, String soll, String ist, Integer saldoMonat, int gesamtsaldo, boolean fett) {
    }

    private Div monatsKarte(Zeitkontouebersicht uebersicht) {
        List<Zeile> zeilen = new ArrayList<>();
        zeilen.add(new Zeile("Startsaldo (" + uebersicht.anfangssaldoDatum().format(TAG_FORMATTER) + ")", "–", "–",
                null, uebersicht.anfangssaldoMinuten(), false));
        for (ZeitkontoZeile monat : uebersicht.monate()) {
            ZeitkontoMonat konto = monat.konto();
            zeilen.add(new Zeile(monatsText(monat),
                    DauerFormat.text(Duration.ofMinutes(konto.sollMinuten())),
                    DauerFormat.text(Duration.ofMinutes(konto.istMinuten())),
                    konto.saldoMonatMinuten(), konto.gesamtsaldoMinuten(), monat.laufend()));
        }

        Grid<Zeile> tabelle = new Grid<>();
        spalte(tabelle, zeile -> zelle(zeile.monat(), zeile.fett()), "Monat").setFlexGrow(1);
        zahlenSpalte(tabelle, zeile -> zelle(zeile.soll(), zeile.fett()), "Soll");
        zahlenSpalte(tabelle, zeile -> zelle(zeile.ist(), zeile.fett()), "Ist");
        zahlenSpalte(tabelle, this::saldoMonatZelle, "Saldo Monat");
        zahlenSpalte(tabelle, zeile -> zelle(DauerFormat.saldo(zeile.gesamtsaldo()), zeile.fett()), "Gesamtsaldo");
        tabelle.setItems(zeilen);
        tabelle.setAllRowsVisible(true);
        tabelle.addClassName("m2-tabelle");

        Span hinweis = Bausteine.gedaempft(
                "Feiertage und arbeitsfreie Tage: Soll 0. Genehmigte Abwesenheit: Soll gilt als erfüllt.");
        hinweis.addClassName("m2-fusstext");

        return Bausteine.karte(Bausteine.titel("Monate " + uebersicht.monate().get(0).konto().monat().getYear()),
                tabelle, hinweis);
    }

    private Grid.Column<Zeile> spalte(Grid<Zeile> tabelle, SerializableFunction<Zeile, Span> inhalt, String titel) {
        return tabelle.addComponentColumn(inhalt::apply).setHeader(titel).setAutoWidth(true).setFlexGrow(0);
    }

    private void zahlenSpalte(Grid<Zeile> tabelle, SerializableFunction<Zeile, Span> inhalt, String titel) {
        spalte(tabelle, inhalt, titel).setTextAlign(ColumnTextAlign.END);
    }

    private Span zelle(String text, boolean fett) {
        return fett ? Bausteine.fett(text) : new Span(text);
    }

    private Span saldoMonatZelle(Zeile zeile) {
        if (zeile.saldoMonat() == null) {
            return new Span("–");
        }
        Span zelle = zelle(DauerFormat.saldo(zeile.saldoMonat()), zeile.fett());
        zelle.addClassName(farbKlasse(zeile.saldoMonat()));
        return zelle;
    }

    private String monatsText(ZeitkontoZeile zeile) {
        String text = zeile.konto().monat().format(MONAT_FORMATTER);
        List<String> grenzen = new ArrayList<>();
        if (zeile.ab() != null) {
            grenzen.add("ab " + zeile.ab().format(TAG_FORMATTER));
        }
        if (zeile.bis() != null) {
            grenzen.add("bis " + zeile.bis().format(TAG_FORMATTER));
        }
        return grenzen.isEmpty() ? text : text + " (" + String.join(" ", grenzen) + ")";
    }

    private Div urlaubsKarte(int jahr) {
        Urlaubskonto konto = abwesenheitService.urlaubskonto(sitzung.mitarbeiterId(), sitzung.rolle(), jahr);
        Div resturlaub = wertZeile(new Span("Resturlaub"),
                Bausteine.text(tage(konto.resturlaubTage()), "m2-resturlaub-wert"));
        resturlaub.addClassName("m2-resturlaub");
        return Bausteine.karte(Bausteine.titel("Urlaubskonto " + jahr),
                zeile("Jahresanspruch", tage(konto.anspruchTage())),
                zeile("Übertrag " + (jahr - 1), tage(konto.uebertragTage())),
                zeile("Genommen", tage(konto.genommenTage())),
                new Hr(),
                resturlaub);
    }

    private String tage(Integer anzahl) {
        if (anzahl == null) {
            return "–";
        }
        return anzahl + (anzahl == 1 ? " Tag" : " Tage");
    }

    private Div gesamtsaldoKachel(int minuten) {
        Div kachel = Bausteine.kachel("Gesamtsaldo", new Span(DauerFormat.saldo(minuten) + " h"));
        kachel.addClassName("m2-dunkel");
        return kachel;
    }

    private Div zeile(String beschriftung, String wert) {
        return wertZeile(Bausteine.text(beschriftung, "m2-status-seit"), new Span(wert));
    }

    private Div wertZeile(Span links, Span rechts) {
        return Bausteine.bereich("m2-wertzeile", links, rechts);
    }

    private String stunden(int minuten) {
        return DauerFormat.text(Duration.ofMinutes(minuten)) + " h";
    }

    private String farbKlasse(int minuten) {
        return minuten < 0 ? "m2-negativ" : "m2-positiv";
    }
}
