package de.timecraft.zeiterfassung.view;

import java.util.Locale;

import com.vaadin.flow.component.timepicker.TimePicker;

/**
 * Uhrzeitfeld im deutschen Format, das unlesbare Eingaben erkennt (sonst gaelte das Feld als leer).
 *
 * @author Noah Cipa
 */
class ZeitFeld extends TimePicker {

    ZeitFeld(String beschriftung) {
        super(beschriftung);
        setLocale(Locale.GERMANY);
        setManualValidation(true);
    }

    boolean unlesbar() {
        return isInputUnparsable();
    }
}
