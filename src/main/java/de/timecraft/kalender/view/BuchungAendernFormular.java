package de.timecraft.kalender.view;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.kalender.model.BuchungAenderungEingabe;
import de.timecraft.kalender.model.ProjektAuswahl;
import de.timecraft.kalender.service.BuchungAenderungService;
import de.timecraft.kalender.service.BuchungInzwischenGeaendertException;
import de.timecraft.kalender.service.UngueltigeAenderungException;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.util.List;
import java.util.Locale;

/**
 * Aenderungsformular einer Buchung (FA-M6-08, 09, UC-M6-05): Beginn, Ende, Standort, Projekt und Begruendung.
 * Braucht nur die Buchungs-ID und laesst sich deshalb auch ohne Detailfenster oeffnen (Vorbereitung K1).
 * Alle Regeln prueft der BuchungAenderungService; Fehler zu einem Feld erscheinen an diesem Feld.
 *
 * @author Bennet Antonio Brinkmeier
 */
public class BuchungAendernFormular extends Dialog {

    private final BuchungAenderungService service;
    private final AktuelleSitzung sitzung;
    private final long zeiteintragId;
    private final Runnable nachDemSpeichern;

    private final DateTimePicker beginn = zeitfeld("Beginn");
    private final DateTimePicker ende = zeitfeld("Ende");
    private final Select<Standort> standort = new Select<>();
    private final Select<ProjektAuswahl> projekt = new Select<>();
    private final TextArea begruendung = new TextArea("Begründung");

    /** Der Stand beim Oeffnen; der Service vergleicht ihn beim Speichern mit dem aktuellen (UC-M6-05 4d). */
    private BuchungInfo stand;

    /** nachDemSpeichern wird nach erfolgreichem Speichern aufgerufen, z. B. um den Kalender neu zu laden. */
    public BuchungAendernFormular(BuchungAenderungService service, AktuelleSitzung sitzung, long zeiteintragId,
            Runnable nachDemSpeichern) {
        this.service = service;
        this.sitzung = sitzung;
        this.zeiteintragId = zeiteintragId;
        this.nachDemSpeichern = nachDemSpeichern;
        setHeaderTitle("Buchung ändern");
        setWidth("36rem");
        standort.setLabel("Standort");
        standort.setItems(Standort.values());
        standort.setItemLabelGenerator(KalenderTexte::standort);
        projekt.setLabel("Projekt");
        projekt.setItemLabelGenerator(auswahl -> auswahl == null ? "Ohne Projekt" : auswahl.name());
        begruendung.setPlaceholder("Warum wird die Buchung geändert?");
        begruendung.setMaxLength(500);
        begruendung.setRequiredIndicatorVisible(true);
        FormLayout formular = new FormLayout(beginn, ende, standort, projekt, begruendung);
        formular.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        formular.setColspan(begruendung, 2);
        add(formular);

        Button abbrechen = new Button("Abbrechen", klick -> close());
        Button speichern = new Button("Speichern", klick -> speichern());
        speichern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(abbrechen, speichern);
    }

    /** Laedt die Buchung und oeffnet das Formular; ohne Recht erscheint nur die Meldung (UC-M6-05 1a, 1b). */
    public void oeffnen() {
        try {
            standLaden(service.buchungZumAendern(sitzung.mitarbeiterId(), sitzung.rolle(), zeiteintragId));
            open();
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    private void speichern() {
        felderZuruecksetzen();
        BuchungAenderungEingabe eingabe = new BuchungAenderungEingabe(beginn.getValue(), ende.getValue(),
                standort.getValue(), projekt.getValue() == null ? null : projekt.getValue().projektId(),
                begruendung.getValue());
        try {
            service.aendern(sitzung.mitarbeiterId(), sitzung.rolle(), stand, eingabe);
            Meldungen.erfolg("Die Buchung wurde geändert.");
            close();
            nachDemSpeichern.run();
        } catch (UngueltigeAenderungException fehler) {
            HasValidation feld = feld(fehler.feld());
            feld.setErrorMessage(fehler.getMessage());
            feld.setInvalid(true);
        } catch (BuchungInzwischenGeaendertException fehler) {
            Meldungen.fehler(fehler);
            standLaden(fehler.aktuellerStand());
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /**
     * Merkt sich den Stand und traegt ihn mit der Projektauswahl in die Felder ein. Die Begruendung bleibt,
     * damit sie nach einer gleichzeitigen Aenderung (4d) nicht neu getippt werden muss.
     */
    private void standLaden(BuchungInfo neuerStand) {
        stand = neuerStand;
        List<ProjektAuswahl> projekte = service.waehlbareZielprojekte(sitzung.mitarbeiterId(), sitzung.rolle(),
                zeiteintragId);
        projekt.setItems(projekte);
        projekt.setEmptySelectionAllowed(stand.projektId() == null);
        projekt.setEmptySelectionCaption("Ohne Projekt");
        BuchungAenderungEingabe werte = service.formularwerte(stand);
        beginn.setValue(werte.beginn());
        ende.setValue(werte.ende());
        standort.setValue(werte.standort());
        projekt.setValue(projekte.stream()
                .filter(auswahl -> werte.projektId() != null && auswahl.projektId() == werte.projektId())
                .findFirst().orElse(null));
    }

    private HasValidation feld(UngueltigeAenderungException.Feld feld) {
        return switch (feld) {
            case BEGINN -> beginn;
            case ENDE -> ende;
            case STANDORT -> standort;
            case PROJEKT -> projekt;
            case BEGRUENDUNG -> begruendung;
        };
    }

    private void felderZuruecksetzen() {
        for (HasValidation feld : List.<HasValidation>of(beginn, ende, standort, projekt, begruendung)) {
            feld.setInvalid(false);
        }
    }

    private static DateTimePicker zeitfeld(String beschriftung) {
        DateTimePicker feld = new DateTimePicker(beschriftung);
        feld.setLocale(Locale.GERMANY);
        feld.setDatePlaceholder("Datum");
        feld.setTimePlaceholder("Uhrzeit");
        feld.setDatePickerI18n(deutscherKalender());
        return feld;
    }

    private static DatePicker.DatePickerI18n deutscherKalender() {
        return new DatePicker.DatePickerI18n()
                .setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
                        "September", "Oktober", "November", "Dezember"))
                .setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"))
                .setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"))
                .setFirstDayOfWeek(1)
                .setToday("Heute")
                .setCancel("Abbrechen")
                .setDateFormat("dd.MM.yyyy");
    }
}
