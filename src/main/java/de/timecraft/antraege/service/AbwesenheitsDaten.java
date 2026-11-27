package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import java.time.LocalDate;

/** Eingaben fuer Urlaub, Krankheit oder sonstige Abwesenheit; urlaubsart nur bei Urlaub. */
public record AbwesenheitsDaten(AbwesenheitsArt art, Urlaubsart urlaubsart, LocalDate von, LocalDate bis,
        String begruendung) {
}
