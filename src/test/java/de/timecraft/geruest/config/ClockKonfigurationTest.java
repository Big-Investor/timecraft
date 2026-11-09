package de.timecraft.geruest.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockKonfigurationTest {

    private final ApplicationContextRunner kontext =
            new ApplicationContextRunner().withUserConfiguration(ClockKonfiguration.class);

    @Test
    void stelltClockFuerEuropeBerlinBereit() {
        kontext.run(ctx -> {
            assertThat(ctx).hasSingleBean(Clock.class);
            assertThat(ctx.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        });
    }
}
