package de.timecraft.material.view;

import de.timecraft.material.service.AntragZurPruefung;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

/**
 * Offene Mehrbedarfe pruefen (FA-M4-05): Alt-/Neuwert, Kostendifferenz und Begruendung, dann Freigeben oder
 * Ablehnen. Die Entscheidung trifft der Service ueber die Rueckrufe der View (true = erledigt).
 */
class AntragPruefenDialog extends Dialog {

    AntragPruefenDialog(List<AntragZurPruefung> antraege, LongPredicate freigeben,
            BiPredicate<Long, String> ablehnen) {
        setHeaderTitle("Offene Mehrbedarfe prüfen");
        setWidth("36rem");
        antraege.forEach(antrag -> add(eintrag(antrag, freigeben, ablehnen)));
        getFooter().add(new Button("Schließen", ereignis -> close()));
    }

    private Div eintrag(AntragZurPruefung antrag, LongPredicate freigeben, BiPredicate<Long, String> ablehnen) {
        Span kopf = new Span(antrag.antragsteller() + " · " + antrag.bezeichnung());
        kopf.getStyle().set("font-weight", "600");
        Paragraph menge = new Paragraph("Soll-Menge: " + Formate.menge(antrag.alteMenge(), antrag.einheit())
                + " → " + Formate.menge(antrag.neueMenge(), antrag.einheit())
                + " (+" + Formate.menge(antrag.zusatzmenge(), antrag.einheit()) + ")");
        Paragraph kosten = new Paragraph("Kostendifferenz (EK): " + Formate.euroMitVorzeichen(antrag.kostendifferenz()));
        Paragraph grund = new Paragraph("Begründung: „" + antrag.begruendung() + "“ · eingereicht am "
                + Formate.datum(antrag.eingereichtAm()));
        TextArea kommentar = new TextArea("Kommentar (Pflicht bei Ablehnung)");
        kommentar.setWidthFull();
        kommentar.setMaxLength(255);

        Button freigabe = new Button("Freigeben", ereignis -> {
            if (freigeben.test(antrag.antragId())) {
                close();
            }
        });
        freigabe.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button ablehnung = new Button("Ablehnen", ereignis -> {
            if (ablehnen.test(antrag.antragId(), kommentar.getValue())) {
                close();
            }
        });
        ablehnung.addThemeVariants(ButtonVariant.LUMO_ERROR);

        Div eintrag = new Div(kopf, menge, kosten, grund, kommentar, new HorizontalLayout(freigabe, ablehnung));
        eintrag.getStyle().set("border-bottom", "1px solid var(--tc-rahmen)").set("padding-bottom", "1rem")
                .set("margin-bottom", "1rem");
        return eintrag;
    }
}
