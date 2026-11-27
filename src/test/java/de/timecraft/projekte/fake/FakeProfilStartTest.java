package de.timecraft.projekte.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Die ganze Anwendung startet im Profil "fake", und jede Auskunft hat genau einen Anbieter. Die Services
 * injizieren sie mit {@code @Lazy}, ein doppelter Anbieter fiele sonst erst beim Aufruf einer Seite auf.
 * Eigene Datenbank, weil die Demodaten sonst die gemeinsame Testdatenbank fuellen.
 *
 * @author Tim Stukenbröker
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fakeprofil;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "fake"})
class FakeProfilStartTest {

    @Autowired
    private ApplicationContext kontext;

    @Test
    void jedeAuskunftHatGenauEinenAnbieter() {
        for (Class<?> schnittstelle : new Class<?>[] {MitarbeiterAuskunft.class, ProjektAuskunft.class,
                BuchungsAuskunft.class, BuchungsAenderung.class}) {
            assertThat(kontext.getBeanNamesForType(schnittstelle)).as(schnittstelle.getSimpleName()).hasSize(1);
        }
    }
}
