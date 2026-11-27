package de.timecraft.antraege.fake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Stellt im Testprofil die Fakes fuer M1, M2 und M3 bereit, damit der Spring-Kontext ohne die
 * anderen Module startet (Entscheidung Alper Sayar, 27.11.2026). Beim Merge mit den echten
 * Modulen loeschen, sonst gibt es die Schnittstellen doppelt.
 */
@Configuration
@Profile("test")
public class FakeTestKonfiguration {

    @Bean
    public FakeMitarbeiterAuskunft fakeMitarbeiterAuskunft() {
        return FakeMitarbeiterAuskunft.beispiel();
    }

    @Bean
    public FakeProjektAuskunft fakeProjektAuskunft() {
        return FakeProjektAuskunft.beispiel();
    }

    @Bean
    public FakeZeiterfassung fakeZeiterfassung() {
        return new FakeZeiterfassung();
    }
}
