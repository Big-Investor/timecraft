package de.timecraft.projekte.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.projekte.FesteUhr;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Prüft, dass die Fakes für M1 und M2 sich wie im Vertrag verhalten und Beispieldaten für alle Rollen haben. */
class FakeAuskunftTest {

    private final FakeMitarbeiterAuskunft mitarbeiter = new FakeMitarbeiterAuskunft(Rolle.PROJEKTLEITUNG);
    private final FakeBuchungsAuskunft buchungen = new FakeBuchungsAuskunft(FesteUhr.UHR);

    @Test
    void esGibtPersonenFuerAlleDreiRollenMitDenIdsDerPlatzhalterBenutzer() {
        assertThat(mitarbeiter.finde(1).orElseThrow().rolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(mitarbeiter.finde(2).orElseThrow().rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
        assertThat(mitarbeiter.finde(3).orElseThrow().rolle()).isEqualTo(Rolle.ADMIN);
    }

    @Test
    void angemeldetePersonHatDieRolleAusDerProperty() {
        assertThat(new FakeMitarbeiterAuskunft(Rolle.ADMIN).angemeldetePerson().rolle()).isEqualTo(Rolle.ADMIN);
        assertThat(new FakeMitarbeiterAuskunft(Rolle.MITARBEITER).angemeldetePerson().rolle())
                .isEqualTo(Rolle.MITARBEITER);
    }

    @Test
    void aktiveLassenEhemaligeWegAlleEnthaeltSie() {
        assertThat(mitarbeiter.aktive()).extracting(MitarbeiterInfo::mitarbeiterId).doesNotContain(8L);
        assertThat(mitarbeiter.alle()).extracting(MitarbeiterInfo::mitarbeiterId).contains(8L);
        assertThat(mitarbeiter.finde(8).orElseThrow().aktiv()).isFalse();
    }

    @Test
    void unbekanntePersonGibtLeeresOptional() {
        assertThat(mitarbeiter.finde(99)).isEmpty();
    }

    @Test
    void zweiLaufendeBuchungenAufDasDemoprojektMitStandortUndBeginnHeute() {
        List<BuchungInfo> laufend = buchungen.laufendeBuchungenAufProjekt(1);

        assertThat(laufend).extracting(BuchungInfo::mitarbeiterId).containsExactlyInAnyOrder(1L, 4L);
        assertThat(laufend).allSatisfy(buchung -> {
            assertThat(buchung.laeuft()).isTrue();
            assertThat(buchung.standort()).isEqualTo(Standort.UNTERWEGS);
        });
        BuchungInfo jonas = buchungen.laufendeBuchung(1).orElseThrow();
        assertThat(jonas.beginn().atZone(ZoneId.of("Europe/Berlin")).toLocalTime()).isEqualTo(LocalTime.of(7, 2));
    }

    @Test
    void anderesProjektHatKeineLaufendeBuchungUndRestIstLeer() {
        assertThat(buchungen.laufendeBuchungenAufProjekt(2)).isEmpty();
        assertThat(buchungen.laufendeBuchung(5)).isEmpty();
        assertThat(buchungen.buchungen(1, FesteUhr.UHR.instant(), FesteUhr.UHR.instant())).isEmpty();
    }
}
