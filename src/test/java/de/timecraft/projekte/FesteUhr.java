package de.timecraft.projekte;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Eine Uhr, die immer dieselbe Zeit zeigt, damit Tests nicht von der echten Zeit abhängen.
 * <p>
 * Mit "new ProjektService(..., FesteUhr.UHR)" nutzt man sie direkt, in Datenbanktests bindet man
 * die ganze Klasse mit "@Import(FesteUhr.class)" ein und bekommt sie als Clock-Bean.
 * Die feste Zeit ist der 18.11.2026, 10:00 Uhr in Berlin (Europe/Berlin).
 */
@TestConfiguration
public class FesteUhr {

    public static final Clock UHR = Clock.fixed(Instant.parse("2026-11-18T09:00:00Z"), ZoneId.of("Europe/Berlin"));

    /** Dieselbe Zeit als Datum mit Uhrzeit, so wie sie in der Datenbank steht. */
    public static final LocalDateTime JETZT = LocalDateTime.of(2026, 11, 18, 10, 0);

    @Bean
    public Clock clock() {
        return UHR;
    }
}
