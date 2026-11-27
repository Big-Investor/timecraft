package de.timecraft.zeiterfassung.view;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.Anwesenheit;
import de.timecraft.zeiterfassung.model.AnwesenheitsUebersicht;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.service.AnwesenheitService;
import de.timecraft.zeiterfassung.view.Bausteine.Farbe;
import jakarta.annotation.security.RolesAllowed;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "anwesenheit", layout = HauptLayout.class)
@PageTitle("Wer ist da?")
@RolesAllowed({ "MITARBEITER", "PROJEKTLEITUNG" })
public class AnwesenheitView extends Div {

    private static final DateTimeFormatter UHRZEIT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AnwesenheitService anwesenheitService;
    private final AktuelleSitzung sitzung;
    private final TextField namensuche = new TextField("Name");
    private final Select<StempelStatus> statusAuswahl = new Select<>();
    private final Select<Standort> standortAuswahl = new Select<>();
    private final Button aktualisierenKnopf = new Button("Aktualisieren", ereignis -> aktualisieren());
    private final Span standText = new Span();
    private final Div zaehler = new Div();
    private final Div kaertchen = new Div();
    private final Span keineTrefferText = new Span("Niemand passt zu den gewählten Filtern.");

    public AnwesenheitView(AnwesenheitService anwesenheitService, AktuelleSitzung sitzung) {
        this.anwesenheitService = anwesenheitService;
        this.sitzung = sitzung;
        addClassName("m2-seite");

        namensuche.setPlaceholder("Name suchen");
        namensuche.addValueChangeListener(ereignis -> aktualisieren());

        statusAuswahl.setLabel("Status");
        statusAuswahl.setItems(StempelStatus.values());
        statusAuswahl.setItemLabelGenerator(status -> statusText(status));
        statusAuswahl.setEmptySelectionAllowed(true);
        statusAuswahl.setEmptySelectionCaption("Alle");
        statusAuswahl.addValueChangeListener(ereignis -> aktualisieren());

        standortAuswahl.setLabel("Standort");
        standortAuswahl.setWidth("9rem");
        standortAuswahl.setItems(Standort.values());
        standortAuswahl.setItemLabelGenerator(standort -> standort == null ? "Alle" : StandortText.text(standort));
        standortAuswahl.setEmptySelectionAllowed(true);
        standortAuswahl.setEmptySelectionCaption("Alle");
        standortAuswahl.addValueChangeListener(ereignis -> aktualisieren());

        standText.addClassName("m2-status-seit");
        HorizontalLayout filterZeile = new HorizontalLayout(namensuche, statusAuswahl, standortAuswahl, aktualisierenKnopf, standText);
        filterZeile.setWidthFull();
        filterZeile.setFlexGrow(1, namensuche);
        aktualisierenKnopf.addClassName("m2-knopf-weiss");
        filterZeile.setAlignItems(FlexComponent.Alignment.END);

        zaehler.addClassName("m2-zaehler");
        kaertchen.addClassName("m2-personen");
        keineTrefferText.addClassName("m2-status-seit");

        Span datenschutzText = new Span(
                "Angezeigt werden nur Name, Status, Standort und Projekt – keine Arbeitszeiten, Salden oder Abwesenheitsarten.");
        datenschutzText.addClassName("m2-status-seit");
        add(filterZeile, zaehler, kaertchen, keineTrefferText, datenschutzText);
        aktualisieren();
    }

    private void aktualisieren() {
        try {
            AnwesenheitsUebersicht uebersicht = anwesenheitService.uebersicht(sitzung.rolle(), namensuche.getValue(),
                    statusAuswahl.getValue(), standortAuswahl.getValue());

            zaehler.removeAll();
            for (StempelStatus status : List.of(StempelStatus.EINGESTEMPELT, StempelStatus.IN_PAUSE,
                    StempelStatus.ABWESEND, StempelStatus.NICHT_EINGESTEMPELT)) {
                zaehler.add(statusSchild(status, uebersicht.anzahlJeStatus().get(status) + " " + statusText(status)));
            }

            kaertchen.removeAll();
            for (Anwesenheit anwesenheit : uebersicht.treffer()) {
                kaertchen.add(kaertchen(anwesenheit));
            }
            keineTrefferText.setVisible(uebersicht.treffer().isEmpty());
            standText.setText("Stand " + uebersicht.stand().format(UHRZEIT_FORMATTER) + " Uhr");
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private Div kaertchen(Anwesenheit anwesenheit) {
        Div kopf = Bausteine.bereich("m2-personenkopf", Bausteine.text(anwesenheit.name(), "m2-name"),
                statusSchild(anwesenheit.status(), statusText(anwesenheit.status())));
        Div karte = Bausteine.karte(kopf, Bausteine.text(detailText(anwesenheit), "m2-details"));
        karte.addClassName("m2-person");
        return karte;
    }

    private String detailText(Anwesenheit anwesenheit) {
        if (anwesenheit.standort() == null) {
            return "–";
        }
        String projekt = anwesenheit.projektId() == null ? "ohne Projekt"
                : ProjektText.listenText(anwesenheit.projektId(), anwesenheit.projektName());
        return StandortText.text(anwesenheit.standort()) + " · " + projekt;
    }

    private Span statusSchild(StempelStatus status, String text) {
        Farbe farbe = switch (status) {
            case EINGESTEMPELT -> Farbe.GRUEN;
            case IN_PAUSE -> Farbe.ORANGE;
            case ABWESEND -> Farbe.BLAU;
            case NICHT_EINGESTEMPELT -> Farbe.GRAU;
        };
        return Bausteine.schild(text, farbe);
    }

    private String statusText(StempelStatus status) {
        if (status == null) {
            return "Alle";
        }
        return switch (status) {
            case EINGESTEMPELT -> "eingestempelt";
            case IN_PAUSE -> "in Pause";
            case NICHT_EINGESTEMPELT -> "nicht eingestempelt";
            case ABWESEND -> "abwesend";
        };
    }
}
