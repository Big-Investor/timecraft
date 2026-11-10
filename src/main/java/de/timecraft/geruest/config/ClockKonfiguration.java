package de.timecraft.geruest.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockKonfiguration {

    private static final ZoneId ZEITZONE = ZoneId.of("Europe/Berlin");

    @Bean
    public Clock clock() {
        return Clock.system(ZEITZONE);
    }
}
