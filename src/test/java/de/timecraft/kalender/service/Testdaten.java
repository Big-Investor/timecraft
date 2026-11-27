package de.timecraft.kalender.service;

import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Gemeinsame Hilfen der Kalendertests: Ortszeiten in Europe/Berlin und Projekte.
 *
 * @author Bennet Antonio Brinkmeier
 */
final class Testdaten {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private Testdaten() {
    }

    /** Ortszeit wie "2026-10-20T07:00" als Zeitpunkt in Europe/Berlin. */
    static Instant instant(String ortszeit) {
        return LocalDateTime.parse(ortszeit).atZone(BERLIN).toInstant();
    }

    static ProjektInfo projekt(long id, String name, long projektleitung, boolean aktiv) {
        return new ProjektInfo(id, name, LocalDate.of(2026, 1, 1), null, projektleitung, aktiv, BigDecimal.ZERO);
    }
}
