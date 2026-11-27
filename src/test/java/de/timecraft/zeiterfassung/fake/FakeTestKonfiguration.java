package de.timecraft.zeiterfassung.fake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.context.annotation.Profile;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.api.ProjektAuskunft;

/**
 * Damit der Anwendungskontext im Profil "test" ohne M1 und M3 startet. {@code @Fallback}: Sobald nach dem
 * Merge die echten Beans von M1 und M3 da sind, nimmt Spring diese und der Fake bleibt ungenutzt.
 *
 * @author Noah Cipa
 */
@Configuration
@Profile("test")
public class FakeTestKonfiguration {

    @Bean
    @Fallback
    public MitarbeiterAuskunft fakeMitarbeiterAuskunft() {
        return new FakeMitarbeiterAuskunft(Rolle.MITARBEITER);
    }

    @Bean
    @Fallback
    public ProjektAuskunft fakeProjektAuskunft() {
        return new FakeProjektAuskunft();
    }
}
