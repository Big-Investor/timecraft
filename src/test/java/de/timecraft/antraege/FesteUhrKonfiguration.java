package de.timecraft.antraege;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Ersetzt in Spring-Tests die Uhr des Geruests durch {@link Beispiele#FESTE_UHR}. */
@TestConfiguration
public class FesteUhrKonfiguration {

    @Bean
    @Primary
    public Clock festeUhr() {
        return Beispiele.FESTE_UHR;
    }
}
