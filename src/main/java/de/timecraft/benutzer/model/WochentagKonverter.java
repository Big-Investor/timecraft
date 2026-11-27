package de.timecraft.benutzer.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

/**
 * Wandelt einen Wochentag in eine Zahl für die Spalte {@code arbeitstag.wochentag} um und zurück.
 * 1 = Montag, 2 = Dienstag, … 7 = Sonntag (wie im Relationenmodell).
 *
 * @author Leon-Selim Kannenberg
 */
@Converter
public class WochentagKonverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek wochentag) {
        if (wochentag == null) {
            return null;
        }
        int nummer = wochentag.getValue();
        return (short) nummer;
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short nummer) {
        if (nummer == null) {
            return null;
        }
        return DayOfWeek.of(nummer);
    }
}
