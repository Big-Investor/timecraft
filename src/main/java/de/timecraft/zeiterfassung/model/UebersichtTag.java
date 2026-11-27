package de.timecraft.zeiterfassung.model;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Ein Tag der Zeitenuebersicht. {@code feiertag} ist der Name des Feiertags, {@code abwesenheit} die
 * genehmigte Abwesenheit an diesem Arbeitstag; beides ist sonst null.
 *
 * @author Noah Cipa
 */
public record UebersichtTag(LocalDate datum, String feiertag, AbwesenheitsTag abwesenheit,
        List<UebersichtEintrag> eintraege, Duration summe) {
}
