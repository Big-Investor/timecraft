package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.repository.BenutzerRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prüft die Eingaben des Mitarbeiterformulars in der {@link MitarbeiterPruefung}
 * (UC-M1-04 Schritt 5, 5a, 5b; QA-M1-05, QA-M1-07): Pflichtfelder, Plausibilität,
 * Eindeutigkeit und die Regeln für Vorgesetzte. Jede Meldung steht am betroffenen Feld.
 *
 * @author Leon-Selim Kannenberg
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MitarbeiterPruefungTest {

    @Autowired
    private BenutzerRepository benutzerRepository;

    @Autowired
    private MitarbeiterRepository mitarbeiterRepository;

    private MitarbeiterPruefung pruefung;
    private Mitarbeiter lena;

    @BeforeEach
    void vorbereiten() {
        pruefung = new MitarbeiterPruefung(benutzerRepository, mitarbeiterRepository);
        lena = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "l.schroeder",
                Rolle.PROJEKTLEITUNG, "1004");
    }

    @Test
    void richtigesFormularIstInOrdnungUndLeeresMeldetJedesPflichtfeld() {
        assertThatCode(() -> pruefung.pruefeNeuenMitarbeiter(formular())).doesNotThrowAnyException();

        MitarbeiterDaten leer = new MitarbeiterDaten();
        EingabeFehlerException fehler = catchThrowableOfType(EingabeFehlerException.class,
                () -> pruefung.pruefeNeuenMitarbeiter(leer));

        assertThat(fehler.getFehlerJeFeld()).containsOnlyKeys(MitarbeiterDaten.PERSONALNUMMER,
                MitarbeiterDaten.VORNAME, MitarbeiterDaten.NACHNAME, MitarbeiterDaten.EMAIL,
                MitarbeiterDaten.GEBURTSDATUM, MitarbeiterDaten.EINTRITTSDATUM, MitarbeiterDaten.QUALIFIKATION,
                MitarbeiterDaten.WOCHENSTUNDEN, MitarbeiterDaten.ARBEITSTAGE, MitarbeiterDaten.URLAUBSANSPRUCH,
                MitarbeiterDaten.BENUTZERNAME, MitarbeiterDaten.ROLLE);
        assertThat(fehler.meldungZu(MitarbeiterDaten.GEBURTSDATUM)).isEqualTo("Bitte geben Sie das Geburtsdatum ein.");
        assertThat(fehler.meldungZu(MitarbeiterDaten.ARBEITSTAGE)).isEqualTo("Bitte wählen Sie mindestens einen Arbeitstag.");
    }

    @Test
    void plausibilitaetEindeutigkeitUndVorgesetzte() {
        // Grenzen: 20 Urlaubstage gehen, 19 nicht; 60 Wochenstunden gehen, 60,01 nicht (0 siehe unten)
        MitarbeiterDaten grenzwerte = formular();
        grenzwerte.setUrlaubsanspruch(20);
        grenzwerte.setWochenstunden(new BigDecimal("60"));
        assertThatCode(() -> pruefung.pruefeNeuenMitarbeiter(grenzwerte)).doesNotThrowAnyException();

        MitarbeiterDaten falsch = formular();
        falsch.setUrlaubsanspruch(19);
        falsch.setUrlaubsuebertrag(-1);
        falsch.setWochenstunden(new BigDecimal("60.01"));
        falsch.setArbeitstage(Set.of());
        falsch.setEmail("keine-adresse");
        falsch.setPersonalnummer("1004");
        falsch.setBenutzername("l.schroeder");
        EingabeFehlerException fehler = fehlerVon(falsch);
        assertThat(fehler.meldungZu(MitarbeiterDaten.URLAUBSANSPRUCH))
                .isEqualTo("Der Urlaubsanspruch muss mindestens 20 Tage betragen (gesetzlicher Mindesturlaub).");
        assertThat(fehler.meldungZu(MitarbeiterDaten.URLAUBSUEBERTRAG))
                .isEqualTo("Der Resturlaub aus dem Vorjahr muss zwischen 0 und 365 Tagen liegen.");
        assertThat(fehler.meldungZu(MitarbeiterDaten.WOCHENSTUNDEN))
                .isEqualTo("Die Wochenstunden müssen größer als 0 und höchstens 60 sein.");
        assertThat(fehler.meldungZu(MitarbeiterDaten.ARBEITSTAGE)).isNotNull();
        assertThat(fehler.meldungZu(MitarbeiterDaten.EMAIL)).startsWith("Bitte geben Sie eine gültige E-Mail-Adresse ein");
        assertThat(fehler.meldungZu(MitarbeiterDaten.PERSONALNUMMER)).isEqualTo("Die Personalnummer 1004 ist bereits vergeben.");
        assertThat(fehler.meldungZu(MitarbeiterDaten.BENUTZERNAME)).isEqualTo("Der Benutzername l.schroeder ist bereits vergeben.");

        // 0 Wochenstunden und zu lange Texte (Spaltenlängen) werden am Feld gemeldet, nie als Datenbankfehler
        MitarbeiterDaten zuLang = formular();
        zuLang.setWochenstunden(BigDecimal.ZERO);
        zuLang.setPersonalnummer("MA-2026-0001");
        zuLang.setVorname("x".repeat(51));
        zuLang.setNachname("x".repeat(51));
        zuLang.setEmail("x".repeat(90) + "@test.example");
        zuLang.setTelefon("0".repeat(31));
        zuLang.setBenutzername("x".repeat(51));
        EingabeFehlerException laengen = fehlerVon(zuLang);
        assertThat(laengen.getFehlerJeFeld()).containsOnlyKeys(MitarbeiterDaten.WOCHENSTUNDEN,
                MitarbeiterDaten.PERSONALNUMMER, MitarbeiterDaten.VORNAME, MitarbeiterDaten.NACHNAME,
                MitarbeiterDaten.EMAIL, MitarbeiterDaten.TELEFON, MitarbeiterDaten.BENUTZERNAME);
        assertThat(laengen.meldungZu(MitarbeiterDaten.PERSONALNUMMER))
                .isEqualTo("Die Personalnummer darf höchstens 10 Zeichen haben.");

        // Obergrenze 365 Tage, damit nichts verfälscht in die SMALLINT-Spalten gelangt
        MitarbeiterDaten zuViel = formular();
        zuViel.setUrlaubsanspruch(40_000);
        zuViel.setUrlaubsuebertrag(366);
        EingabeFehlerException zuGross = fehlerVon(zuViel);
        assertThat(zuGross.meldungZu(MitarbeiterDaten.URLAUBSANSPRUCH))
                .isEqualTo("Der Urlaubsanspruch darf höchstens 365 Tage betragen.");
        assertThat(zuGross.meldungZu(MitarbeiterDaten.URLAUBSUEBERTRAG)).isNotNull();

        // Beim Bearbeiten darf die eigene Personalnummer bleiben, aber niemand ist sein eigener Vorgesetzter
        MitarbeiterDaten lenaSelbst = formular();
        lenaSelbst.setPersonalnummer("1004");
        lenaSelbst.setBenutzername("l.schroeder");
        lenaSelbst.setRolle(Rolle.PROJEKTLEITUNG);
        EingabeFehlerException eigener = catchThrowableOfType(EingabeFehlerException.class,
                () -> pruefung.pruefeAenderungDurchAdmin(lenaSelbst, lena));
        assertThat(eigener.getFehlerJeFeld()).containsOnlyKeys(MitarbeiterDaten.VORGESETZTER);
        assertThat(eigener.meldungZu(MitarbeiterDaten.VORGESETZTER))
                .isEqualTo("Ein Mitarbeiter kann nicht sein eigener Vorgesetzter sein.");

        // Vorgesetzte: Pflicht für Mitarbeiter, nur Projektleitungen wählbar, kein Vorgesetzter für Admins
        Mitarbeiter mehmet = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "m.yilmaz",
                Rolle.MITARBEITER, "1015");
        MitarbeiterDaten ohne = formular();
        ohne.setVorgesetzterId(null);
        MitarbeiterDaten mitarbeiterAlsVorgesetzter = formular();
        mitarbeiterAlsVorgesetzter.setVorgesetzterId(mehmet.getMitarbeiterId());
        Mitarbeiter ehemaligeLeitung = TestDaten.personAnlegen(benutzerRepository, mitarbeiterRepository, "t.krause",
                Rolle.PROJEKTLEITUNG, "1006");
        ehemaligeLeitung.setAktiv(false);
        mitarbeiterRepository.save(ehemaligeLeitung);
        MitarbeiterDaten inaktiveLeitung = formular();
        inaktiveLeitung.setVorgesetzterId(ehemaligeLeitung.getMitarbeiterId());
        MitarbeiterDaten unbekannt = formular();
        unbekannt.setVorgesetzterId(999_999L);
        MitarbeiterDaten adminMitVorgesetzter = formular();
        adminMitVorgesetzter.setRolle(Rolle.ADMIN);
        assertThat(fehlerVon(ohne).meldungZu(MitarbeiterDaten.VORGESETZTER))
                .isEqualTo("Bitte wählen Sie eine Vorgesetzte oder einen Vorgesetzten.");
        assertThat(fehlerVon(mitarbeiterAlsVorgesetzter).meldungZu(MitarbeiterDaten.VORGESETZTER))
                .isEqualTo("Als Vorgesetzte/r ist nur eine Projektleitung wählbar.");
        assertThat(fehlerVon(inaktiveLeitung).meldungZu(MitarbeiterDaten.VORGESETZTER))
                .isEqualTo("Test Person 1006 ist nicht mehr aktiv.");
        assertThat(fehlerVon(unbekannt).meldungZu(MitarbeiterDaten.VORGESETZTER)).contains("wurde nicht gefunden");
        assertThat(fehlerVon(adminMitVorgesetzter).meldungZu(MitarbeiterDaten.VORGESETZTER))
                .isEqualTo("Ein Admin-Konto hat keine Vorgesetzte und keinen Vorgesetzten.");
    }

    private MitarbeiterDaten formular() {
        return TestDaten.formular("1024", "l.meyer", Rolle.MITARBEITER, lena.getMitarbeiterId());
    }

    private EingabeFehlerException fehlerVon(MitarbeiterDaten daten) {
        return catchThrowableOfType(EingabeFehlerException.class, () -> pruefung.pruefeNeuenMitarbeiter(daten));
    }
}
