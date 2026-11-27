package de.timecraft.zeiterfassung.view;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.service.EingabeFehler;
import de.timecraft.zeiterfassung.service.FeiertagService;
import jakarta.annotation.security.RolesAllowed;

/** @author Noah Cipa */
@CssImport("./styles/zeiterfassung.css")
@Route(value = "feiertage", layout = HauptLayout.class)
@PageTitle("Feiertage")
@RolesAllowed("ADMIN")
public class FeiertagView extends Div {

    private static final DateTimeFormatter DATUM_FORMATTER = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", Locale.GERMANY);

    private final FeiertagService feiertagService;
    private final AktuelleSitzung sitzung;
    private final DatePicker datumFeld = new DatePicker("Datum");
    private final TextField nameFeld = new TextField("Name");
    private final Button speichernKnopf = new Button("Anlegen", ereignis -> speichern());
    private final Button abbrechenKnopf = new Button("Abbrechen", ereignis -> formularLeeren());
    private final Grid<Feiertag> tabelle = new Grid<>();

    private Feiertag ausgewaehlterFeiertag;

    public FeiertagView(FeiertagService feiertagService, AktuelleSitzung sitzung) {
        this.feiertagService = feiertagService;
        this.sitzung = sitzung;
        addClassNames("karte", "m2-feiertage");

        datumFeld.setLocale(Locale.GERMANY);
        datumFeld.setRequiredIndicatorVisible(true);
        nameFeld.setRequiredIndicatorVisible(true);
        datumFeld.setManualValidation(true);
        nameFeld.setManualValidation(true);
        nameFeld.setMaxLength(FeiertagService.MAX_LAENGE_NAME);
        speichernKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        abbrechenKnopf.setVisible(false);

        tabelle.addColumn(feiertag -> feiertag.getDatum().format(DATUM_FORMATTER)).setHeader("Datum");
        tabelle.addColumn(Feiertag::getName).setHeader("Name").setFlexGrow(1);
        tabelle.addComponentColumn(feiertag -> bearbeitenKnopf(feiertag)).setHeader("");
        tabelle.addComponentColumn(feiertag -> loeschenKnopf(feiertag)).setHeader("");
        tabelle.setAllRowsVisible(true);
        tabelle.addClassName("m2-tabelle");

        HorizontalLayout formular = new HorizontalLayout(datumFeld, nameFeld, speichernKnopf, abbrechenKnopf);
        formular.setAlignItems(FlexComponent.Alignment.END);
        add(formular, tabelle);
        aktualisieren();
    }

    private Button bearbeitenKnopf(Feiertag feiertag) {
        Button knopf = new Button("Bearbeiten", ereignis -> bearbeiten(feiertag));
        knopf.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return knopf;
    }

    private Button loeschenKnopf(Feiertag feiertag) {
        Button knopf = new Button("Löschen", ereignis -> loeschen(feiertag));
        knopf.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        return knopf;
    }

    private void bearbeiten(Feiertag feiertag) {
        fehlerZuruecksetzen();
        ausgewaehlterFeiertag = feiertag;
        datumFeld.setValue(feiertag.getDatum());
        nameFeld.setValue(feiertag.getName());
        speichernKnopf.setText("Speichern");
        abbrechenKnopf.setVisible(true);
    }

    private void speichern() {
        fehlerZuruecksetzen();
        try {
            if (ausgewaehlterFeiertag == null) {
                feiertagService.anlegen(sitzung.rolle(), datumFeld.getValue(), nameFeld.getValue());
                Meldungen.erfolg("Feiertag angelegt.");
            } else {
                feiertagService.aendern(sitzung.rolle(), ausgewaehlterFeiertag.getId(), datumFeld.getValue(), nameFeld.getValue());
                Meldungen.erfolg("Feiertag geändert.");
            }
            formularLeeren();
            aktualisieren();
        } catch (EingabeFehler fehler) {
            eingabeFehlerZeigen(fehler);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void eingabeFehlerZeigen(EingabeFehler fehler) {
        if (fehler.felder().contains(EingabeFehler.Feld.DATUM)) {
            datumFeld.setErrorMessage(fehler.getMessage());
            datumFeld.setInvalid(true);
        }
        if (fehler.felder().contains(EingabeFehler.Feld.NAME)) {
            nameFeld.setErrorMessage(fehler.getMessage());
            nameFeld.setInvalid(true);
        }
    }

    private void fehlerZuruecksetzen() {
        datumFeld.setInvalid(false);
        nameFeld.setInvalid(false);
    }

    private void loeschen(Feiertag feiertag) {
        try {
            feiertagService.loeschen(sitzung.rolle(), feiertag.getId());
            Meldungen.erfolg("Feiertag gelöscht.");
            formularLeeren();
            aktualisieren();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void formularLeeren() {
        fehlerZuruecksetzen();
        ausgewaehlterFeiertag = null;
        datumFeld.clear();
        nameFeld.clear();
        speichernKnopf.setText("Anlegen");
        abbrechenKnopf.setVisible(false);
    }

    private void aktualisieren() {
        try {
            tabelle.setItems(feiertagService.alle(sitzung.rolle()));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
