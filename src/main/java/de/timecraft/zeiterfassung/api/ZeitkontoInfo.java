// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.YearMonth;

/** saldoMinuten = Gesamtsaldo am Monatsende (bei laufendem Monat: bis heute), inklusive Startsaldo. */
public record ZeitkontoInfo(
        long mitarbeiterId, YearMonth monat, int sollMinuten, int istMinuten,
        int differenzMinuten, int saldoMinuten) { }
