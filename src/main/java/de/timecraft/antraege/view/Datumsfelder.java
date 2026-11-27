package de.timecraft.antraege.view;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.util.List;
import java.util.Locale;

/** Deutsche Datums- und Zeitfelder (Format TT.MM.JJJJ, 24 Stunden). */
final class Datumsfelder {

    private Datumsfelder() {
    }

    static DatePicker datum(String beschriftung) {
        DatePicker feld = new DatePicker(beschriftung);
        feld.setLocale(Locale.GERMANY);
        DatePicker.DatePickerI18n texte = new DatePicker.DatePickerI18n();
        texte.setDateFormat("dd.MM.yyyy");
        texte.setFirstDayOfWeek(1);
        texte.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
                "September", "Oktober", "November", "Dezember"));
        texte.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"));
        texte.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"));
        texte.setToday("Heute");
        texte.setCancel("Abbrechen");
        feld.setI18n(texte);
        return feld;
    }

    static TimePicker uhrzeit(String beschriftung) {
        TimePicker feld = new TimePicker(beschriftung);
        feld.setLocale(Locale.GERMANY);
        return feld;
    }
}
