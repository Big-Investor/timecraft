package de.timecraft.beispiel.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.beispiel.model.Notiz;
import de.timecraft.beispiel.service.NotizService;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.PermitAll;
import java.time.format.DateTimeFormatter;

/**
 * Vorlage fuer eine View: holt die Person aus AktuelleSitzung, reicht sie an den Service weiter
 * und meldet Fehler ueber Meldungen. Keine Fachlogik und kein Repository-Zugriff.
 * Jede View braucht layout, @PageTitle und @PermitAll oder @RolesAllowed.
 */
@Route(value = "notizen", layout = HauptLayout.class)
@PageTitle("Notizen")
@PermitAll
public class NotizView extends Div {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final NotizService service;
    private final AktuelleSitzung sitzung;
    private final TextArea eingabe = new TextArea("Neue Notiz");
    private final Grid<Notiz> liste = new Grid<>();

    public NotizView(NotizService service, AktuelleSitzung sitzung) {
        this.service = service;
        this.sitzung = sitzung;
        addClassName("karte");
        eingabe.setWidthFull();
        eingabe.setMaxLength(NotizService.MAX_LAENGE);
        Button speichern = new Button("Speichern", ereignis -> speichern());
        speichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        spaltenEinrichten();
        add(eingabe, speichern, liste);
        aktualisieren();
    }

    private void spaltenEinrichten() {
        liste.addColumn(Notiz::getText).setHeader("Notiz").setFlexGrow(1);
        liste.addColumn(notiz -> notiz.getErstelltAm().format(DATUM)).setHeader("Erstellt am");
        liste.addColumn(Notiz::getErstellerId).setHeader("Von (Mitarbeiter-ID)");
        if (sitzung.rolle() == Rolle.ADMIN) {
            liste.addComponentColumn(this::loeschenKnopf).setHeader("");
        }
    }

    private Button loeschenKnopf(Notiz notiz) {
        Button knopf = new Button("Löschen", ereignis -> loeschen(notiz));
        knopf.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return knopf;
    }

    private void speichern() {
        try {
            service.anlegen(sitzung.mitarbeiterId(), sitzung.rolle(), eingabe.getValue());
            eingabe.clear();
            aktualisieren();
            Meldungen.erfolg("Notiz gespeichert.");
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void loeschen(Notiz notiz) {
        try {
            service.loeschen(sitzung.rolle(), notiz.getId());
            aktualisieren();
            Meldungen.erfolg("Notiz gelöscht.");
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void aktualisieren() {
        liste.setItems(service.alle(sitzung.rolle()));
    }
}
