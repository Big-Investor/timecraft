package de.timecraft.antraege.fake;

import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Lokaler Start ohne die anderen Module: {@code mvn spring-boot:run -Dspring-boot.run.profiles=fake}.
 * Die Rolle waehlt man ueber die Platzhalter-Anmeldung (mitarbeiter, projektleitung, admin).
 * Nach dem Merge mit den echten Modulen ist das Profil nicht aktiv.
 */
@Configuration
@Profile("fake")
public class FakeKonfiguration {

    @Bean
    public FakeMitarbeiterAuskunft fakeMitarbeiterAuskunft() {
        return FakeMitarbeiterAuskunft.beispiel();
    }

    @Bean
    public FakeProjektAuskunft fakeProjektAuskunft() {
        return FakeProjektAuskunft.beispiel();
    }

    /** Je Person mit Vorgesetztem eine abgeschlossene Buchung an jedem der letzten fuenf Werktage. */
    @Bean
    public FakeZeiterfassung fakeZeiterfassung(Clock clock) {
        FakeZeiterfassung fake = new FakeZeiterfassung();
        ZoneId zone = clock.getZone();
        LocalDate tag = LocalDate.now(clock);
        long id = 48200;
        for (int angelegt = 0; angelegt < 5; tag = tag.minusDays(1)) {
            if (tag.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            for (long person : new long[] {FakeMitarbeiterAuskunft.JONAS, FakeMitarbeiterAuskunft.LENA,
                    FakeMitarbeiterAuskunft.MEHMET, FakeMitarbeiterAuskunft.FELIX}) {
                fake.buchungHinzufuegen(new BuchungInfo(id++, person, FakeProjektAuskunft.KUECHE, Standort.UNTERWEGS,
                        tag.atTime(LocalTime.of(7, 0)).atZone(zone).toInstant(),
                        tag.atTime(LocalTime.of(14, 30)).atZone(zone).toInstant(), 30, 420, false));
            }
            angelegt++;
        }
        return fake;
    }
}
