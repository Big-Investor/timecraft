package de.timecraft.zeiterfassung.model;

import java.time.YearMonth;

/** @author Noah Cipa */
public record ZeitkontoMonat(YearMonth monat, int sollMinuten, int istMinuten, int saldoMonatMinuten,
        int gesamtsaldoMinuten) {
}
