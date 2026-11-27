package de.timecraft.zeiterfassung.model;

import java.time.Duration;

/**
 * Ein Zeiteintrag mit den berechneten Werten. {@code projektName} ist null ohne Projekt oder wenn M3 das
 * Projekt nicht kennt.
 *
 * @author Noah Cipa
 */
public record UebersichtEintrag(Zeiteintrag eintrag, String projektName, Duration pausen, Duration netto,
        int fehlendePauseMinuten) {
}
