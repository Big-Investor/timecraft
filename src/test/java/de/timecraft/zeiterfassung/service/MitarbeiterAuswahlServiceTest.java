package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.ANNA_VOGT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.FELIX_BRANDT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LENA_SCHROEDER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LUKAS_MEYER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.MEHMET_YILMAZ;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;
import de.timecraft.zeiterfassung.model.MitarbeiterEintrag;

/** @author Noah Cipa */
class MitarbeiterAuswahlServiceTest {

    private final ZugriffsPruefung zugriffsPruefung = new ZugriffsPruefung(new FakeProjektAuskunft());
    private final MitarbeiterAuswahlService service = new MitarbeiterAuswahlService(
            new FakeMitarbeiterAuskunft(Rolle.MITARBEITER), zugriffsPruefung);

    @Test
    void mitarbeiterSiehtNurSichSelbst() {
        assertThat(service.lesbareMitarbeiter(JONAS_BECKER, Rolle.MITARBEITER))
                .containsExactly(new MitarbeiterEintrag(JONAS_BECKER, "Jonas Becker"));
    }

    @Test
    void adminHatKeineAuswahl() {
        assertThatThrownBy(() -> service.lesbareMitarbeiter(PETRA_KRUEGER, Rolle.ADMIN))
                .isInstanceOf(de.timecraft.geruest.common.ZugriffVerweigertException.class);
    }

    @Test
    void projektleitungStehtVornDanachIhreMitarbeiterNachNachnameOhneDoppelte() {
        assertThat(service.lesbareMitarbeiter(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG))
                .extracting(MitarbeiterEintrag::mitarbeiterId)
                .containsExactly(PAUL_HARTMANN, JONAS_BECKER, FELIX_BRANDT, LENA_SCHROEDER, MEHMET_YILMAZ);
    }

    @Test
    void andereProjektleitungSiehtNurIhreEigenenProjektmitarbeiter() {
        assertThat(service.lesbareMitarbeiter(SABINE_ENGEL, Rolle.PROJEKTLEITUNG))
                .extracting(MitarbeiterEintrag::mitarbeiterId)
                .containsExactly(SABINE_ENGEL, LUKAS_MEYER, ANNA_VOGT);
    }

    @Test
    void ohneRolleProjektleitungHilftDieIdNicht() {
        assertThat(service.lesbareMitarbeiter(PAUL_HARTMANN, Rolle.MITARBEITER))
                .extracting(MitarbeiterEintrag::mitarbeiterId).containsExactly(PAUL_HARTMANN);
    }

    @Test
    void mitarbeiterDieM1NichtKenntFehlen() {
        MitarbeiterAuskunft auskunft = spy(new FakeMitarbeiterAuskunft(Rolle.PROJEKTLEITUNG));
        doReturn(Optional.empty()).when(auskunft).finde(FELIX_BRANDT);

        List<MitarbeiterEintrag> auswahl = new MitarbeiterAuswahlService(auskunft, zugriffsPruefung)
                .lesbareMitarbeiter(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG);

        assertThat(auswahl).extracting(MitarbeiterEintrag::mitarbeiterId)
                .containsExactly(PAUL_HARTMANN, JONAS_BECKER, LENA_SCHROEDER, MEHMET_YILMAZ);
    }

    @Test
    void ohneAnmeldungKeineAuswahl() {
        assertThatThrownBy(() -> service.lesbareMitarbeiter(JONAS_BECKER, null))
                .isInstanceOf(NichtAngemeldetException.class);
    }
}
