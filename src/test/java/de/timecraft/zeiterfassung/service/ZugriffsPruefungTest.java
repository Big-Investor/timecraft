package de.timecraft.zeiterfassung.service;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.ANNA_VOGT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.FELIX_BRANDT;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PETRA_KRUEGER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.UWE_SCHULZ;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.zeiterfassung.fake.FakeProjektAuskunft;

/**
 * Paul verantwortet Wagner, Lindenstrasse und Krause (archiviert), Sabine verantwortet Schmitt (Anna, Lukas).
 *
 * @author Noah Cipa
 */
class ZugriffsPruefungTest {

    private final ZugriffsPruefung pruefung = new ZugriffsPruefung(new FakeProjektAuskunft());

    @Test
    void mitarbeiterUndProjektleitungLesenIhreEigenenDaten() {
        assertThatCode(() -> pruefung.pruefeLesen(JONAS_BECKER, Rolle.MITARBEITER, JONAS_BECKER)).doesNotThrowAnyException();
        assertThatCode(() -> pruefung.pruefeLesen(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, PAUL_HARTMANN)).doesNotThrowAnyException();
    }

    @Test
    void adminHatKeineZeiterfassung() {
        assertThatThrownBy(() -> pruefung.pruefeLesen(PETRA_KRUEGER, Rolle.ADMIN, PETRA_KRUEGER))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Zeiten und Zeitkonten gibt es nur für Mitarbeiter und Projektleitungen.");
        assertThatCode(() -> pruefung.pruefeAngemeldet(Rolle.ADMIN)).doesNotThrowAnyException();
    }

    @Test
    void mitarbeiterLiestKeineFremdenDatenAuchNichtImGleichenProjekt() {
        // Jonas und Felix arbeiten beide an der Treppe Lindenstrasse
        assertThatThrownBy(() -> pruefung.pruefeLesen(JONAS_BECKER, Rolle.MITARBEITER, FELIX_BRANDT))
                .isInstanceOf(ZugriffVerweigertException.class)
                .hasMessage("Sie dürfen nur Ihre eigenen Zeiten und die der Mitarbeiter Ihrer Projekte ansehen.");
    }

    @Test
    void projektleitungLiestMitarbeiterIhrerProjekte() {
        assertThatCode(() -> pruefung.pruefeLesen(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, JONAS_BECKER)).doesNotThrowAnyException();
        assertThatCode(() -> pruefung.pruefeLesen(SABINE_ENGEL, Rolle.PROJEKTLEITUNG, ANNA_VOGT)).doesNotThrowAnyException();
    }

    @Test
    void projektleitungLiestKeineMitarbeiterFremderProjekte() {
        assertThatThrownBy(() -> pruefung.pruefeLesen(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, ANNA_VOGT))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> pruefung.pruefeLesen(SABINE_ENGEL, Rolle.PROJEKTLEITUNG, JONAS_BECKER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> pruefung.pruefeLesen(PAUL_HARTMANN, Rolle.PROJEKTLEITUNG, UWE_SCHULZ))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void adminLiestKeineFremdenZeiten() {
        assertThatThrownBy(() -> pruefung.pruefeLesen(PETRA_KRUEGER, Rolle.ADMIN, JONAS_BECKER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void ohneRolleProjektleitungHilftDieIdNicht() {
        // Die Rolle zaehlt, nicht die ID: Pauls ID mit der Rolle MITARBEITER liest Jonas nicht
        assertThatThrownBy(() -> pruefung.pruefeLesen(PAUL_HARTMANN, Rolle.MITARBEITER, JONAS_BECKER))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void ohneAnmeldungKeinZugriff() {
        assertThatThrownBy(() -> pruefung.pruefeLesen(JONAS_BECKER, null, JONAS_BECKER))
                .isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> pruefung.pruefeAngemeldet(null)).isInstanceOf(NichtAngemeldetException.class);
    }
}
