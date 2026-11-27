package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;

/**
 * Feste Uhr (Dienstag, 10.11.2026, 12:00 Uhr in Berlin) und Fakes fuer die Integrationstests der api.
 *
 * @author Noah Cipa
 */
@TestConfiguration
class IntegrationsTestKonfiguration {

    static final Instant JETZT = Instant.parse("2026-11-10T11:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(JETZT, ZoneId.of("Europe/Berlin"));
    }

    @Bean
    MitarbeiterAuskunft mitarbeiterAuskunft() {
        return new FakeMitarbeiterAuskunft(Rolle.MITARBEITER);
    }

    @Bean
    ProjektAuskunft projektAuskunft() {
        return new FakeProjektAuskunft();
    }

    /** Leert die M2-Tabellen, weil die Tests ohne Testtransaktion wirklich speichern. */
    static void leereTabellen(JdbcTemplate jdbc) {
        for (String tabelle : new String[] { "aenderungsprotokoll", "pause", "zeiteintrag", "abwesenheit",
                "feiertag" }) {
            jdbc.update("delete from " + tabelle);
        }
    }
}
