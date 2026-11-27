package de.timecraft.zeiterfassung.fake;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.UWE_SCHULZ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;

/** @author Noah Cipa */
class FakeMitarbeiterAuskunftTest {

    private final FakeMitarbeiterAuskunft fake = new FakeMitarbeiterAuskunft(Rolle.MITARBEITER);

    @Test
    void angemeldetePersonRichtetSichNachDerRolle() {
        assertThat(new FakeMitarbeiterAuskunft(Rolle.MITARBEITER).angemeldetePerson().mitarbeiterId())
                .isEqualTo(JONAS_BECKER);
        assertThat(new FakeMitarbeiterAuskunft(Rolle.PROJEKTLEITUNG).angemeldetePerson().mitarbeiterId())
                .isEqualTo(PAUL_HARTMANN);
        assertThat(new FakeMitarbeiterAuskunft(Rolle.ADMIN).angemeldetePerson().mitarbeiterId())
                .isEqualTo(PETRA_KRUEGER);
    }

    @Test
    void ohneAnmeldungWirftAngemeldetePersonEineFachausnahme() {
        assertThatThrownBy(() -> new FakeMitarbeiterAuskunft(null).angemeldetePerson())
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void idsPassenZuDenPlatzhalterBenutzernDesGeruests() {
        assertThat(fake.finde(1).orElseThrow().rolle()).isEqualTo(Rolle.MITARBEITER);
        assertThat(fake.finde(2).orElseThrow().rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
        assertThat(fake.finde(3).orElseThrow().rolle()).isEqualTo(Rolle.ADMIN);
    }

    @Test
    void findeLiefertAuchEhemaligeAberNichtUnbekannte() {
        assertThat(fake.finde(UWE_SCHULZ)).get().extracting(MitarbeiterInfo::aktiv).isEqualTo(false);
        assertThat(fake.finde(99)).isEmpty();
    }

    @Test
    void aktiveOhneEhemaligeAlleMitEhemaligen() {
        assertThat(fake.aktive()).extracting(MitarbeiterInfo::mitarbeiterId).doesNotContain(UWE_SCHULZ);
        assertThat(fake.alle()).extracting(MitarbeiterInfo::mitarbeiterId).contains(UWE_SCHULZ);
        assertThat(fake.alle()).hasSize(fake.aktive().size() + 1);
    }

    @Test
    void urlaubsdatenUndStundensatzNurFuerBekannteMitarbeiter() {
        assertThat(fake.urlaubsdaten(JONAS_BECKER, 2026)).get().satisfies(daten -> {
            assertThat(daten.jahr()).isEqualTo(2026);
            assertThat(daten.anspruchTage()).isEqualTo(30);
        });
        assertThat(fake.urlaubsdaten(99, 2026)).isEmpty();
        assertThat(fake.stundensatz(JONAS_BECKER)).isPresent();
        assertThat(fake.stundensatz(99)).isEmpty();
    }
}
