package de.timecraft.benutzer.view;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.PasswordField;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Kleine Hilfen für Eingabefelder der M1-Views: Passwort- und Datumsfelder anlegen und Fehlermeldungen
 * direkt am Feld anzeigen (QA-M1-07). Geprüft wird nur im Service; diese Klasse zeigt
 * dessen Meldungen nur an.
 *
 * @author Leon-Selim Kannenberg
 */
final class Eingabefelder {

    /** Hilfetext am Feld für ein neues Passwort (Regeln aus QA-M1-03). */
    static final String PASSWORTREGELN = "Mindestens 8 Zeichen und mindestens eine Ziffer.";

    private Eingabefelder() {
    }

    /**
     * Ein Passwortfeld über die ganze Breite. Die Prüfung macht der Service, deshalb schaltet
     * das Feld seine eigene Prüfung im Browser ab ({@code manualValidation}). Sonst würde
     * der Browser eine Fehlermeldung des Service beim nächsten Tippen wieder entfernen.
     */
    static PasswordField passwortFeld(String beschriftung) {
        PasswordField feld = new PasswordField(beschriftung);
        feld.setWidthFull();
        feld.setManualValidation(true);
        return feld;
    }

    /**
     * Ein deutsches Datumsfeld (TT.MM.JJJJ, Woche ab Montag) für die Jahre 1900 bis 2099.
     * Wie beim Passwortfeld prüft der Service, nicht der Browser ({@code manualValidation}).
     */
    static DatePicker datumFeld(String beschriftung) {
        DatePicker.DatePickerI18n deutsch = new DatePicker.DatePickerI18n();
        deutsch.setDateFormat("dd.MM.yyyy");
        deutsch.setFirstDayOfWeek(1);
        deutsch.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
                "August", "September", "Oktober", "November", "Dezember"));
        deutsch.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag",
                "Freitag", "Samstag"));
        deutsch.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
        deutsch.setToday("Heute");
        deutsch.setCancel("Abbrechen");

        DatePicker feld = new DatePicker(beschriftung);
        feld.setLocale(Locale.GERMANY);
        feld.setI18n(deutsch);
        feld.setMin(LocalDate.of(1900, 1, 1));
        feld.setMax(LocalDate.of(2099, 12, 31));
        feld.setWidthFull();
        feld.setManualValidation(true);
        return feld;
    }

    /**
     * Zeigt eine Meldung am Feld. Ist die Meldung {@code null}, ist das Feld in Ordnung und
     * eine alte Meldung verschwindet.
     */
    static void fehlerZeigen(HasValidation feld, String meldung) {
        if (meldung == null) {
            feld.setErrorMessage(null);
            feld.setInvalid(false);
        } else {
            feld.setErrorMessage(meldung);
            feld.setInvalid(true);
        }
    }
}
