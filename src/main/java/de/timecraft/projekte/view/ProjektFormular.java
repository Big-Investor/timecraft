package de.timecraft.projekte.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.service.ProjektService;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Formular zum Anlegen und Bearbeiten eines Projekts (nur für den Admin, der Service prüft das).
 * Mit {@code projekt == null} legt es ein neues Projekt an, sonst ändert es das übergebene.
 * Ungültige Eingaben (Pflichtfelder, Zieldatum vor Startdatum ...) prüft allein der Service: Das Formular
 * zeigt seine deutsche Meldung, bleibt offen und es wird nichts gespeichert.
 *
 * @author Tim Stukenbröker
 */
public class ProjektFormular extends Dialog {

    private final ProjektService service;
    private final AktuelleSitzung sitzung;
    private final Projekt projekt;
    private final Consumer<Projekt> nachDemSpeichern;

    private final TextField name = new TextField("Projektname");
    private final TextArea beschreibung = new TextArea("Beschreibung");
    private final DatePicker startdatum = new DatePicker("Startdatum");
    private final DatePicker zieldatum = new DatePicker("Zieldatum");
    private final ComboBox<MitarbeiterInfo> projektleitung = new ComboBox<>("Verantwortliche Projektleitung");

    /** @param nachDemSpeichern wird mit dem gespeicherten Projekt aufgerufen, nachdem das Formular geschlossen ist */
    public ProjektFormular(ProjektService service, AktuelleSitzung sitzung, Projekt projekt,
            Consumer<Projekt> nachDemSpeichern) {
        this.service = service;
        this.sitzung = sitzung;
        this.projekt = projekt;
        this.nachDemSpeichern = nachDemSpeichern;

        setHeaderTitle(projekt == null ? "Neues Projekt" : "Projekt bearbeiten");
        setWidth("34rem");
        add(felder());
        getFooter().add(abbrechen(), speichern());
        projektleitungenLaden();
        if (projekt != null) {
            felderFuellen(projekt);
        }
    }

    private FormLayout felder() {
        name.setMaxLength(ProjektService.MAX_NAME_LAENGE);
        name.setRequiredIndicatorVisible(true);
        beschreibung.setMinHeight("8rem");
        startdatum.setRequiredIndicatorVisible(true);
        zieldatum.setRequiredIndicatorVisible(true);
        projektleitung.setRequiredIndicatorVisible(true);
        projektleitung.setItemLabelGenerator(MitarbeiterInfo::name);
        for (DatePicker datum : new DatePicker[] {startdatum, zieldatum}) {
            datum.setLocale(Locale.GERMANY);
        }

        FormLayout formular = new FormLayout(name, beschreibung, startdatum, zieldatum, projektleitung);
        formular.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("24rem", 2));
        formular.setColspan(name, 2);
        formular.setColspan(beschreibung, 2);
        formular.setColspan(projektleitung, 2);
        return formular;
    }

    private Button abbrechen() {
        return new Button("Abbrechen", ereignis -> close());
    }

    private Button speichern() {
        Button speichern = new Button("Speichern", ereignis -> speichern(datenAusFeldern()));
        speichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return speichern;
    }

    private void projektleitungenLaden() {
        try {
            List<MitarbeiterInfo> auswahl = service.projektleitungenZurAuswahl(sitzung.mitarbeiterId(), sitzung.rolle());
            projektleitung.setItems(auswahl);
            if (projekt != null) {
                auswahl.stream().filter(person -> person.mitarbeiterId() == projekt.getProjektleitungId())
                        .findFirst().ifPresent(projektleitung::setValue);
            }
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void felderFuellen(Projekt vorhanden) {
        name.setValue(vorhanden.getName());
        beschreibung.setValue(vorhanden.getBeschreibung() == null ? "" : vorhanden.getBeschreibung());
        startdatum.setValue(vorhanden.getStartdatum());
        zieldatum.setValue(vorhanden.getZieldatum());
    }

    private ProjektDaten datenAusFeldern() {
        MitarbeiterInfo leitung = projektleitung.getValue();
        return new ProjektDaten(name.getValue(), beschreibung.getValue(), startdatum.getValue(),
                zieldatum.getValue(), leitung == null ? null : leitung.mitarbeiterId());
    }

    private void speichern(ProjektDaten daten) {
        try {
            Projekt gespeichert = projekt == null
                    ? service.anlegen(sitzung.mitarbeiterId(), sitzung.rolle(), daten)
                    : service.aendern(sitzung.mitarbeiterId(), sitzung.rolle(), projekt.getProjektId(), daten);
            close();
            Meldungen.erfolg("Das Projekt wurde gespeichert.");
            nachDemSpeichern.accept(gespeichert);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
