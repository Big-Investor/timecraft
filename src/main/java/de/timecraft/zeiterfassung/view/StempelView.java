package de.timecraft.zeiterfassung.view;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.service.EingabeFehler;
import de.timecraft.zeiterfassung.service.ZeiteintragService;
import de.timecraft.zeiterfassung.service.ZeitkontoService;
import jakarta.annotation.security.RolesAllowed;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "stempeln", layout = HauptLayout.class)
@PageTitle("Stempeln")
@RolesAllowed({ "MITARBEITER", "PROJEKTLEITUNG" })
public class StempelView extends Div {

    private final ZeiteintragService zeiteintragService;
    private final ZeitkontoService zeitkontoService;
    private final AktuelleSitzung sitzung;
    private final DateTimeFormatter uhrzeitFormatter;
    private final DateTimeFormatter datumFormatter;
    private final Select<Standort> standortAuswahl = new Select<>();
    private final Select<ProjektInfo> projektAuswahl = new Select<>();
    private final Button einstempelnKnopf = new Button("Einstempeln", ereignis -> einstempeln());
    private final Button ausstempelnKnopf = new Button("Ausstempeln", ereignis -> ausstempeln());
    private final Button pauseStartenKnopf = new Button("Pause starten", ereignis -> pauseStarten());
    private final Button pauseBeendenKnopf = new Button("Pause beenden", ereignis -> pauseBeenden());
    private final Span statusText = new Span();
    private final Span eingestempeltOderPauseSeitText = new Span();
    private final Span arbeitszeitHeuteText = new Span();
    private final Div arbeitszeitZeile = new Div();
    private final Span laufendeDauerText = new Span();
    private final Div infoKaesten = new Div();
    private final Span standortWert = new Span();
    private final Span projektWert = new Span();
    private final Span projektIdWert = new Span();
    private final Span warnungText = new Span();
    private final Span pausenWert = new Span();
    private final HorizontalLayout knopfLayout = new HorizontalLayout();
    private final Hr trennlinie = new Hr();
    private final HorizontalLayout einstempelZeile = new HorizontalLayout();
    private final Span saldoText = new Span();
    private final Span wocheText = new Span();
    private final Span wocheSollText = new Span();
    private final ProgressBar wocheBalken = new ProgressBar();

    public StempelView(ZeiteintragService zeiteintragService, ZeitkontoService zeitkontoService, AktuelleSitzung sitzung,
            Clock clock) {
        this.zeiteintragService = zeiteintragService;
        this.zeitkontoService = zeitkontoService;
        this.sitzung = sitzung;
        this.uhrzeitFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clock.getZone());
        this.datumFormatter = DateTimeFormatter.ofPattern("dd.MM.").withZone(clock.getZone());
        addClassNames("m2-zweispaltig", "m2-start");

        standortAuswahl.setLabel("Standort");
        standortAuswahl.setRequiredIndicatorVisible(true);
        standortAuswahl.setItems(Standort.values());
        standortAuswahl.setItemLabelGenerator(StandortText::text);
        standortAuswahl.setManualValidation(true);
        standortAuswahl.addValueChangeListener(ereignis -> standortAuswahl.setInvalid(false));
        projektAuswahl.setLabel("Projekt (optional)");
        projektAuswahl.setEmptySelectionAllowed(true);
        projektAuswahl.setEmptySelectionCaption("ohne Projekt");
        projektAuswahl.setItemLabelGenerator(ProjektText::auswahlText);
        einstempelnKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        einstempelZeile.add(standortAuswahl, projektAuswahl, einstempelnKnopf);
        einstempelZeile.setWidthFull();
        einstempelZeile.setFlexGrow(1, projektAuswahl);
        einstempelZeile.setAlignItems(FlexComponent.Alignment.BASELINE);

        eingestempeltOderPauseSeitText.addClassName("m2-status-seit");
        HorizontalLayout statusZeile = new HorizontalLayout(statusText, eingestempeltOderPauseSeitText);
        statusZeile.setAlignItems(FlexComponent.Alignment.BASELINE);

        arbeitszeitHeuteText.addClassName("m2-arbeitszeit-zahl");
        arbeitszeitZeile.addClassName("m2-arbeitszeit");
        arbeitszeitZeile.add(arbeitszeitHeuteText, Bausteine.text("h", "m2-einheit"));

        projektIdWert.addClassName("m2-status-seit");
        Div projektKasten = infoKasten("Projekt", projektWert);
        projektKasten.add(new Div(projektIdWert));
        infoKaesten.addClassName("m2-infokaesten");
        infoKaesten.add(infoKasten("Standort", standortWert), projektKasten, infoKasten("Pausen heute", pausenWert));

        ausstempelnKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_LARGE);
        ausstempelnKnopf.addClassName("m2-knopf-rot");
        grosserUmrandeterKnopf(pauseStartenKnopf);
        grosserUmrandeterKnopf(pauseBeendenKnopf);
        knopfLayout.add(pauseStartenKnopf, pauseBeendenKnopf, ausstempelnKnopf);
        knopfLayout.setWidthFull();
        knopfLayout.setFlexGrow(1, pauseStartenKnopf, pauseBeendenKnopf, ausstempelnKnopf);
        trennlinie.addClassName("m2-trennlinie");

        warnungText.addClassName("m2-fehler");
        laufendeDauerText.addClassName("m2-status-seit");
        Div stempelKarte = Bausteine.karte(statusZeile, arbeitszeitZeile, laufendeDauerText, warnungText, infoKaesten, knopfLayout,
                trennlinie, einstempelZeile);

        saldoText.addClassName("m2-gross");
        Div zeitkontoKarte = Bausteine.karte(Bausteine.text("Zeitkonto", "m2-kartentitel"), saldoText,
                new Div(new RouterLink("Zum Zeitkonto", ZeitkontoView.class)));

        wocheText.addClassName("m2-gross");
        wocheSollText.addClassNames("m2-status-seit", "m2-abstand-links");
        Div wocheKarte = Bausteine.karte(Bausteine.text("Diese Woche", "m2-kartentitel"),
                new Div(wocheText, wocheSollText), wocheBalken);

        Div antraegeKarte = Bausteine.karte(Bausteine.text("Meine Anträge", "m2-kartentitel"),
                Bausteine.gedaempft("Anträge werden hier noch nicht angezeigt."));

        add(stempelKarte, Bausteine.bereich("m2-spalte", zeitkontoKarte, wocheKarte, antraegeKarte));
        // Sichtbar wird nur, was zum geladenen Status passt; scheitert das Laden, bleibt alles verborgen
        einstempelZeile.setVisible(false);
        trennlinie.setVisible(false);
        knopfLayout.setVisible(false);
        infoKaesten.setVisible(false);
        warnungText.setVisible(false);
        laufendeDauerText.setVisible(false);
        arbeitszeitZeile.setVisible(false);
        aktualisieren();
    }

    private Div infoKasten(String titel, Span wert) {
        wert.addClassName("m2-infowert");
        return Bausteine.bereich("m2-infokasten", Bausteine.text(titel, "m2-infotitel"), wert);
    }

    private void grosserUmrandeterKnopf(Button knopf) {
        knopf.addThemeVariants(ButtonVariant.LUMO_LARGE);
        knopf.addClassName("m2-knopf-rand");
    }

    private void einstempeln() {
        standortAuswahl.setInvalid(false);
        ausfuehren(() -> {
            ProjektInfo projekt = projektAuswahl.getValue();
            zeiteintragService.einstempeln(sitzung.mitarbeiterId(), sitzung.rolle(), standortAuswahl.getValue(),
                    projekt == null ? null : projekt.projektId());
            Meldungen.erfolg("Eingestempelt");
            Hinweis.zeigen(zeiteintragService.hinweisZumHeutigenTag(sitzung.mitarbeiterId(), sitzung.rolle()));
        });
    }

    private void ausstempeln() {
        ausfuehren(() -> {
            Zeiteintrag eintrag = zeiteintragService.ausstempeln(sitzung.mitarbeiterId(), sitzung.rolle());
            Meldungen.erfolg("Ausgestempelt");
            Hinweis.zeigen(zeiteintragService.hinweis(sitzung.mitarbeiterId(), sitzung.rolle(), eintrag));
        });
    }

    private void pauseStarten() {
        ausfuehren(() -> {
            zeiteintragService.pauseStarten(sitzung.mitarbeiterId(), sitzung.rolle());
            Meldungen.erfolg("Pause gestartet");
        });
    }

    private void pauseBeenden() {
        ausfuehren(() -> {
            zeiteintragService.pauseBeenden(sitzung.mitarbeiterId(), sitzung.rolle());
            Meldungen.erfolg("Pause beendet");
        });
    }

    /** Auch nach einem Fehler neu laden: Ein zweites Fenster kann den Status geaendert haben (UC-ZE-01 3a). */
    private void ausfuehren(Runnable stempelaktion) {
        try {
            stempelaktion.run();
        } catch (EingabeFehler fehler) {
            eingabeFehlerZeigen(fehler);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
        aktualisieren();
    }

    /** Ein fehlender Standort steht am Feld (UC-ZE-01 2a), alles andere als Benachrichtigung. */
    private void eingabeFehlerZeigen(EingabeFehler fehler) {
        if (fehler.felder().contains(EingabeFehler.Feld.STANDORT)) {
            standortAuswahl.setErrorMessage(fehler.getMessage());
            standortAuswahl.setInvalid(true);
        } else {
            Meldungen.fehler(fehler);
        }
    }

    /** Ohne zugeordnetes aktives Projekt gibt es keine Projektauswahl (UC-ZE-01 1a). */
    private void projekteLaden() {
        List<ProjektInfo> projekte = zeiteintragService.aktiveProjekte(sitzung.mitarbeiterId(), sitzung.rolle());
        ProjektInfo bisher = projektAuswahl.getValue();
        projektAuswahl.setItems(projekte);
        if (projekte.contains(bisher)) {
            projektAuswahl.setValue(bisher);
        }
        projektAuswahl.setVisible(!projekte.isEmpty());
    }

    private void aktualisieren() {
        try {
            statusAnzeigen();
            seitenleisteAktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void statusAnzeigen() {
        StempelStatus status = zeiteintragService.status(sitzung.mitarbeiterId(), sitzung.rolle());
        if (istAusgestempelt(status)) {
            projekteLaden();
        }

        Optional<Zeiteintrag> laufenderEintrag = zeiteintragService.laufenderEintrag(sitzung.mitarbeiterId(), sitzung.rolle());
        Duration heute = zeiteintragService.arbeitszeitHeute(sitzung.mitarbeiterId(), sitzung.rolle());
        arbeitszeitHeuteText.setText(DauerFormat.text(heute));
        laufendeDauerAnzeigen(heute);
        if (laufenderEintrag.isPresent()) {
            standortWert.setText(StandortText.text(laufenderEintrag.get().getStandort()));
            Optional<ProjektInfo> projekt = zeiteintragService.projektDesLaufendenEintrags(sitzung.mitarbeiterId(), sitzung.rolle());
            projektWert.setText(projekt.map(ProjektInfo::name).orElse("ohne Projekt"));
            projektIdWert.setText(projekt.map(p -> ProjektText.id(p.projektId())).orElse(""));
        }
        String warnung = zeiteintragService.warnungHoechstarbeitszeit(sitzung.mitarbeiterId(), sitzung.rolle());
        warnungText.setText(warnung);
        warnungText.setVisible(!warnung.isEmpty());
        pausenWert.setText(DauerFormat.text(zeiteintragService.pausenHeute(sitzung.mitarbeiterId(), sitzung.rolle())) + " h");

        statusText.removeClassNames("m2-status-ein", "m2-status-pause", "m2-status-aus");
        switch (status) {
            case EINGESTEMPELT -> {
                statusText.addClassName("m2-status-ein");
                statusText.setText("Eingestempelt");
                eingestempeltOderPauseSeitText.setText(seitText(laufenderEintrag.get().getBeginn()));
            }
            case IN_PAUSE -> {
                statusText.addClassName("m2-status-pause");
                statusText.setText("In Pause");
                Instant pausenBeginn = zeiteintragService.laufendePause(sitzung.mitarbeiterId(), sitzung.rolle()).get().getBeginn();
                eingestempeltOderPauseSeitText.setText(seitText(pausenBeginn) + ", eingestempelt "
                        + seitText(laufenderEintrag.get().getBeginn()));
            }
            case NICHT_EINGESTEMPELT -> {
                statusText.addClassName("m2-status-aus");
                statusText.setText("Nicht eingestempelt");
                eingestempeltOderPauseSeitText.setText("");
            }
            case ABWESEND -> {
                statusText.addClassName("m2-status-aus");
                statusText.setText("Abwesend");
                eingestempeltOderPauseSeitText.setText("genehmigte Abwesenheit");
            }
        }
        // Erst nach dem Laden: Scheitert eine Abfrage, erscheinen keine Knoepfe ohne passenden Stand
        bereicheZeigen(status);
    }

    /** Eigene Zeile nur, wenn die grosse Zahl (Arbeitszeit heute) nicht schon die Dauer des laufenden Eintrags ist. */
    private void laufendeDauerAnzeigen(Duration heute) {
        Optional<Duration> laufend = zeiteintragService.dauerLaufenderEintrag(sitzung.mitarbeiterId(), sitzung.rolle())
                .filter(dauer -> !dauer.equals(heute));
        laufendeDauerText.setText(laufend
                .map(dauer -> "Laufender Eintrag: " + DauerFormat.text(dauer) + " h, oben die Arbeitszeit heute")
                .orElse(""));
        laufendeDauerText.setVisible(laufend.isPresent());
    }

    private void bereicheZeigen(StempelStatus status) {
        boolean ausgestempelt = istAusgestempelt(status);
        arbeitszeitZeile.setVisible(true);
        einstempelZeile.setVisible(ausgestempelt);
        trennlinie.setVisible(ausgestempelt);
        knopfLayout.setVisible(!ausgestempelt);
        infoKaesten.setVisible(!ausgestempelt);
        pauseStartenKnopf.setVisible(status == StempelStatus.EINGESTEMPELT);
        pauseBeendenKnopf.setVisible(status == StempelStatus.IN_PAUSE);
    }

    private boolean istAusgestempelt(StempelStatus status) {
        return status == StempelStatus.NICHT_EINGESTEMPELT || status == StempelStatus.ABWESEND;
    }

    /** Liegt der Zeitpunkt nicht heute, nennt der Text auch das Datum. */
    private String seitText(Instant zeitpunkt) {
        boolean heute = LocalDate.ofInstant(zeitpunkt, uhrzeitFormatter.getZone()).equals(zeiteintragService.heute());
        String datum = heute ? "" : datumFormatter.format(zeitpunkt) + ", ";
        return "seit " + datum + uhrzeitFormatter.format(zeitpunkt) + " Uhr";
    }

    private void seitenleisteAktualisieren() {
        int saldo = zeitkontoService.monat(sitzung.mitarbeiterId(), sitzung.rolle(), sitzung.mitarbeiterId(), zeitkontoService.aktuellerMonat())
                .gesamtsaldoMinuten();
        saldoText.setText(DauerFormat.saldo(saldo) + " h");
        saldoText.removeClassNames("m2-positiv", "m2-negativ");
        saldoText.addClassName(saldo < 0 ? "m2-negativ" : "m2-positiv");

        Duration woche = zeiteintragService.arbeitszeitDieseWoche(sitzung.mitarbeiterId(), sitzung.rolle());
        int wochenSoll = zeitkontoService.wochenSollMinuten(sitzung.mitarbeiterId(), sitzung.rolle());
        wocheText.setText(DauerFormat.text(woche));
        wocheSollText.setText("von " + DauerFormat.text(Duration.ofMinutes(wochenSoll)) + " h");
        wocheBalken.setValue(wochenSoll == 0 ? 0 : Math.min(1.0, (double) woche.toMinutes() / wochenSoll));
    }
}
