package de.timecraft.kalender.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.kalender.model.WaehlbarePerson;
import de.timecraft.kalender.service.KalenderService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Die Beispieldaten fuer den lokalen Start zeigen alle drei Rollen sinnvoll (CLAUDE.md Abschnitt 9). */
class KalenderFakeKonfigurationTest {

    // Mittwoch, 18.11.2026, 10:28 Uhr
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T09:28:00Z"), ZoneId.of("Europe/Berlin"));
    private final KalenderFakeKonfiguration konfiguration = new KalenderFakeKonfiguration();
    private final KalenderService service = new KalenderService(konfiguration.fakeBuchungsAuskunft(clock),
            konfiguration.fakeProjektAuskunft(), konfiguration.fakeMitarbeiterAuskunft(), clock);

    @Test
    void mitarbeiterSiehtInDerAktuellenWocheBuchungenEineLaufendeUndEineGeaenderte() {
        KalenderInhalt woche = service.eigenerKalender(1, Rolle.MITARBEITER, Kalenderansicht.WOCHE,
                LocalDate.of(2026, 11, 18), KalenderFilter.KEINER);

        assertThat(woche.eintraege()).isNotEmpty();
        assertThat(woche.eintraege()).anyMatch(eintrag -> eintrag.laeuft());
        assertThat(woche.eintraege()).anyMatch(eintrag -> eintrag.geaendert());
    }

    @Test
    void projektleitungUndAdminHabenPersonenZurAuswahl() {
        assertThat(service.waehlbarePersonen(2, Rolle.PROJEKTLEITUNG)).extracting(WaehlbarePerson::name)
                .containsExactly("Jonas Becker", "Mehmet Yilmaz");
        assertThat(service.waehlbarePersonen(3, Rolle.ADMIN)).hasSize(6);
    }

    @Test
    void buchungUeberMitternachtUndUmstellungsnachtSindDabei() {
        KalenderInhalt vorwoche = service.eigenerKalender(1, Rolle.MITARBEITER, Kalenderansicht.WOCHE,
                LocalDate.of(2026, 11, 13), KalenderFilter.KEINER);
        KalenderInhalt umstellung = service.fremderKalender(3, Rolle.ADMIN, 4, Kalenderansicht.TAG,
                LocalDate.of(2026, 10, 25), KalenderFilter.KEINER);

        assertThat(vorwoche.tagessummen().get(LocalDate.of(2026, 11, 14))).isEqualTo(90);
        assertThat(umstellung.gesamtMinuten()).isEqualTo(7 * 60);
    }
}
