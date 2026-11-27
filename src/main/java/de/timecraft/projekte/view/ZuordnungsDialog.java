package de.timecraft.projekte.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.projekte.service.ProjektService;
import java.util.List;

/**
 * Dialog "Mitarbeiter zuordnen" (UC-M3-05): Die verantwortliche Projektleitung wählt eine oder mehrere
 * Personen, die dem Projekt noch nicht zugeordnet sind. Welche das sind und ob die Zuordnung erlaubt ist,
 * entscheidet der {@link ProjektService}. Sind schon alle zugeordnet, zeigt der Dialog nur einen Hinweis.
 * Bei einem Fehler beim Speichern bleibt er offen und es wird nichts gespeichert. Lässt sich die Auswahl
 * gar nicht laden, wirft schon der Konstruktor: Der Aufrufer zeigt die Meldung, ein Dialog erscheint nicht.
 *
 * @author Tim Stukenbröker
 */
public class ZuordnungsDialog extends Dialog {

    private final ProjektService service;
    private final AktuelleSitzung sitzung;
    private final long projektId;
    private final Runnable nachDemZuordnen;

    private final MultiSelectComboBox<MitarbeiterInfo> auswahl = new MultiSelectComboBox<>("Mitarbeiter");

    /** @param nachDemZuordnen wird aufgerufen, nachdem gespeichert wurde (der Team-Reiter lädt dann neu) */
    public ZuordnungsDialog(ProjektService service, AktuelleSitzung sitzung, long projektId, Runnable nachDemZuordnen) {
        this.service = service;
        this.sitzung = sitzung;
        this.projektId = projektId;
        this.nachDemZuordnen = nachDemZuordnen;

        setHeaderTitle("Mitarbeiter zuordnen");
        setWidth("30rem");
        List<MitarbeiterInfo> zuordenbare =
                service.zuordenbareMitarbeiter(sitzung.mitarbeiterId(), sitzung.rolle(), projektId);
        if (zuordenbare.isEmpty()) {
            add(new Paragraph("Alle Mitarbeiter sind dem Projekt bereits zugeordnet."));
            getFooter().add(new Button("Schließen", ereignis -> close()));
        } else {
            auswahl.setItems(zuordenbare);
            auswahl.setItemLabelGenerator(MitarbeiterInfo::name);
            auswahl.setWidthFull();
            add(auswahl);
            Button zuordnen = new Button("Zuordnen", ereignis -> zuordnen());
            zuordnen.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            getFooter().add(new Button("Abbrechen", ereignis -> close()), zuordnen);
        }
    }

    private void zuordnen() {
        try {
            List<Long> ids = auswahl.getValue().stream().map(MitarbeiterInfo::mitarbeiterId).toList();
            service.zuordnen(sitzung.mitarbeiterId(), sitzung.rolle(), projektId, ids);
            close();
            Meldungen.erfolg(ids.size() == 1 ? "Der Mitarbeiter wurde zugeordnet." : "Die Mitarbeiter wurden zugeordnet.");
            nachDemZuordnen.run();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
