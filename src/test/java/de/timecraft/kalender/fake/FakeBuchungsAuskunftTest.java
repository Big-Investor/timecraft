package de.timecraft.kalender.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueft, dass der Fake das Vertragsverhalten von M2 nachbildet, auf das sich die Kalendertests stuetzen.
 *
 * @author Bennet Antonio Brinkmeier
 */
class FakeBuchungsAuskunftTest {

    private final FakeBuchungsAuskunft auskunft = new FakeBuchungsAuskunft();

    @Test
    void buchungUeberMonatsendeWirdJeTagZerlegtUndMonatsgrenzeEingehalten() {
        // 31.10. 22:00 bis 01.11. 02:00 (Winterzeit, UTC+1), 30 Minuten Pause
        auskunft.hinzufuegen(new BuchungInfo(1, 7, 10L, Standort.BUERO,
                Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-01T01:00:00Z"), 30, 210, false));

        List<ZeitSegment> oktober = auskunft.segmente(7, YearMonth.of(2026, 10));
        List<ZeitSegment> november = auskunft.segmente(7, YearMonth.of(2026, 11));

        assertThat(oktober).singleElement().satisfies(s -> {
            assertThat(s.tag()).isEqualTo(LocalDate.of(2026, 10, 31));
            assertThat(s.anrechenbareMinuten()).isEqualTo(90);
        });
        assertThat(november).singleElement().satisfies(s -> {
            assertThat(s.tag()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(s.anrechenbareMinuten()).isEqualTo(120);
        });
    }

    @Test
    void laufendeBuchungLiefertKeineSegmenteAberBeruehrtDasIntervall() {
        auskunft.hinzufuegen(new BuchungInfo(2, 7, 10L, Standort.BUERO,
                Instant.parse("2026-10-22T05:00:00Z"), null, 0, 0, false));

        assertThat(auskunft.segmente(7, YearMonth.of(2026, 10))).isEmpty();
        assertThat(auskunft.buchungen(7, Instant.parse("2026-10-22T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z")))
                .hasSize(1);
        assertThat(auskunft.laufendeBuchung(7)).isPresent();
    }
}
