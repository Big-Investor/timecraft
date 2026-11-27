package de.timecraft.antraege.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.service.AntragAnsicht;
import de.timecraft.antraege.service.AntragService;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;

/**
 * Seite "Neuer Antrag" nach Mockup "M5 · Antrag erstellen": links das Formular, rechts die
 * eigenen Antraege mit Status, Entscheidung und Hinweis (FA-M5-08, FA-M5-09, FA-M5-18).
 */
@Route(value = "antraege", layout = HauptLayout.class)
@PageTitle("Neuer Antrag")
@RolesAllowed({"MITARBEITER", "PROJEKTLEITUNG"})
public class AntragListeView extends HorizontalLayout {

    private final AntragService service;
    private final AktuelleSitzung sitzung;
    private final AntragFormular formular;
    private final Div liste = new Div();

    public AntragListeView(AntragService service, AktuelleSitzung sitzung) {
        this.service = service;
        this.sitzung = sitzung;
        this.formular = new AntragFormular(service, sitzung, this::aktualisieren);
        Div meineAntraege = new Div(new H3("Meine Anträge"), liste);
        meineAntraege.addClassName("karte");
        setAlignItems(FlexComponent.Alignment.START);
        setWidthFull();
        setFlexGrow(3, formular);
        setFlexGrow(2, meineAntraege);
        add(formular, meineAntraege);
        aktualisieren();
    }

    private void aktualisieren() {
        liste.removeAll();
        try {
            List<AntragAnsicht> antraege = service.eigeneAntraege(sitzung.mitarbeiterId(), sitzung.rolle());
            if (antraege.isEmpty()) {
                liste.add(new Span("Sie haben noch keine Anträge gestellt."));
            }
            antraege.forEach(ansicht -> liste.add(new Hr(), eintrag(ansicht)));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private Div eintrag(AntragAnsicht ansicht) {
        Span titel = new Span(AntragTexte.titel(ansicht));
        titel.getStyle().set("font-weight", "700");
        HorizontalLayout kopf = new HorizontalLayout(titel, AntragTexte.statusKennzeichen(ansicht, true));
        kopf.setWidthFull();
        kopf.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        Div eintrag = new Div(kopf, new Div(new Span(untertitel(ansicht))));
        if (ansicht.antrag().getStatus() == Antragsstatus.OFFEN) {
            Button bearbeiten = new Button("Bearbeiten", ereignis -> formular.bearbeiten(ansicht));
            bearbeiten.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            Button zurueckziehen = new Button("Zurückziehen", ereignis -> zurueckziehen(ansicht));
            zurueckziehen.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            eintrag.add(new HorizontalLayout(bearbeiten, zurueckziehen));
        }
        return eintrag;
    }

    /** Zweite Zeile wie im Mockup: Einreichung, Aenderung bei Zeitkorrekturen, Entscheider oder Hinweis. */
    private static String untertitel(AntragAnsicht ansicht) {
        String eingereicht = (ansicht.typ() == Antragstyp.KRANKHEIT ? "gemeldet " : "eingereicht ")
                + AntragTexte.tag(ansicht.antrag().getEingereichtAm()).format(AntragTexte.DATUM);
        if (ansicht.antrag().getStatus() == Antragsstatus.OFFEN
                || ansicht.antrag().getStatus() == Antragsstatus.ZURUECKGEZOGEN) {
            return eingereicht;
        }
        String hinweis = ansicht.antrag().getEntscheidungshinweis();
        if (hinweis != null) {
            return "„" + hinweis + "“";
        }
        String entschieden = "entschieden von " + ansicht.entscheiderName();
        if (ansicht.antrag() instanceof Zeitkorrekturantrag korrektur) {
            return AntragTexte.korrektur(korrektur) + " · " + entschieden;
        }
        return entschieden;
    }

    private void zurueckziehen(AntragAnsicht ansicht) {
        try {
            service.zurueckziehen(sitzung.mitarbeiterId(), sitzung.rolle(), ansicht.antrag().getId());
            Meldungen.erfolg("Der Antrag wurde zurückgezogen.");
            aktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
