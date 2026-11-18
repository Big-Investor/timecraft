package de.timecraft.kalender.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Der Fake fuer den lokalen Start aendert Buchung und Historie wie M2 und meldet Ueberschneidungen. */
class FakeBuchungsAenderungTest {

    private static final Instant JETZT = Instant.parse("2026-11-02T09:00:00Z");

    private final FakeBuchungsAuskunft auskunft = new FakeBuchungsAuskunft();
    private final FakeBuchungsAenderung aenderung = new FakeBuchungsAenderung(auskunft,
            Clock.fixed(JETZT, ZoneId.of("Europe/Berlin")));

    FakeBuchungsAenderungTest() {
        // 20.10.2026 07:00–15:00 und 15:30–17:00 Ortszeit (Sommerzeit, UTC+2), 30 Minuten Pause
        auskunft.hinzufuegen(new BuchungInfo(1, 7, 10L, Standort.BUERO,
                Instant.parse("2026-10-20T05:00:00Z"), Instant.parse("2026-10-20T13:00:00Z"), 30, 450, false));
        auskunft.hinzufuegen(new BuchungInfo(2, 7, 10L, Standort.BUERO,
                Instant.parse("2026-10-20T13:30:00Z"), Instant.parse("2026-10-20T15:00:00Z"), 0, 90, false));
    }

    @Test
    void aenderungLandetInDerBuchungMitMarkeGeaendertUndJedesGeaenderteFeldInDerHistorie() {
        aenderung.aendern(1, Instant.parse("2026-10-20T05:00:00Z"), Instant.parse("2026-10-20T12:00:00Z"),
                Standort.HOMEOFFICE, 10L, AenderungsArt.AENDERUNG, 2, "Früher gegangen");

        assertThat(auskunft.buchung(1)).hasValue(new BuchungInfo(1, 7, 10L, Standort.HOMEOFFICE,
                Instant.parse("2026-10-20T05:00:00Z"), Instant.parse("2026-10-20T12:00:00Z"), 30, 390, true));
        assertThat(auskunft.historie(1)).containsExactly(
                new AenderungInfo(JETZT, 2, AenderungsArt.AENDERUNG, "Ende", "20.10.2026 15:00", "20.10.2026 14:00",
                        "Früher gegangen"),
                new AenderungInfo(JETZT, 2, AenderungsArt.AENDERUNG, "Standort", "Büro", "Homeoffice",
                        "Früher gegangen"));
        assertThat(aenderung.aufrufe()).hasSize(1);
    }

    @Test
    void ueberschneidungWirdGemeldetUndNichtsGespeichert() {
        BuchungInfo vorher = auskunft.buchung(1).orElseThrow();

        assertThatThrownBy(() -> aenderung.aendern(1, Instant.parse("2026-10-20T05:00:00Z"),
                Instant.parse("2026-10-20T14:00:00Z"), Standort.BUERO, 10L, AenderungsArt.AENDERUNG, 2, "Länger"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Buchung überschneidet sich mit der Buchung vom 20.10.2026, 15:30–17:00 Uhr.");
        assertThat(auskunft.buchung(1)).hasValue(vorher);
        assertThat(auskunft.historie(1)).isEmpty();
        assertThat(aenderung.aufrufe()).isEmpty();
    }

    @Test
    void anschliessendeBuchungIstKeineUeberschneidung() {
        aenderung.aendern(1, Instant.parse("2026-10-20T05:00:00Z"), Instant.parse("2026-10-20T13:30:00Z"),
                Standort.BUERO, 10L, AenderungsArt.AENDERUNG, 2, "Bis zur nächsten Buchung");

        assertThat(auskunft.buchung(1).orElseThrow().ende()).isEqualTo(Instant.parse("2026-10-20T13:30:00Z"));
    }
}
