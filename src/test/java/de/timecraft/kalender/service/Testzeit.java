package de.timecraft.kalender.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Gemeinsame Zeithilfen der Kalendertests: Ortszeiten in Europe/Berlin.
 *
 * @author Bennet Antonio Brinkmeier
 */
final class Testzeit {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private Testzeit() {
    }

    /** Ortszeit wie "2026-10-20T07:00" als Zeitpunkt in Europe/Berlin. */
    static Instant instant(String ortszeit) {
        return LocalDateTime.parse(ortszeit).atZone(BERLIN).toInstant();
    }
}
