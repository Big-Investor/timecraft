package de.timecraft.material.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Speichert eine Einheit als Kuerzel (z. B. "m²") statt als Enum-Namen. */
@Converter
public class EinheitKonverter implements AttributeConverter<Einheit, String> {

    @Override
    public String convertToDatabaseColumn(Einheit einheit) {
        return einheit == null ? null : einheit.kuerzel();
    }

    @Override
    public Einheit convertToEntityAttribute(String kuerzel) {
        return kuerzel == null ? null : Einheit.ausKuerzel(kuerzel);
    }
}
