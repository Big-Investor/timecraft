package de.timecraft.antraege.view;

import static de.timecraft.antraege.Beispiele.abwesenheit;
import static de.timecraft.antraege.Beispiele.buchung;
import static de.timecraft.antraege.Beispiele.mitStatus;
import static de.timecraft.antraege.Beispiele.storno;
import static de.timecraft.antraege.Beispiele.urlaub;
import static de.timecraft.antraege.Beispiele.zeit;
import static de.timecraft.antraege.Beispiele.zeitkorrektur;
import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.service.AntragAnsicht;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Anzeigetexte der Views wie in den Mockups (nur Darstellung). */
class AntragTexteTest {

    private static AntragAnsicht ansicht(Antrag antrag, Antrag bezug) {
        return new AntragAnsicht(antrag, Antragstyp.von(antrag), "Jonas Becker", null, bezug, null, null, null,
                null, null, null, null, false);
    }

    @Test
    void titelWieImMockup() {
        Abwesenheitsantrag urlaub = urlaub(1, Urlaubsart.ERHOLUNGSURLAUB, LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30));
        BuchungInfo buchung = buchung(1, 1, LocalDate.of(2026, 10, 19), LocalTime.of(7, 0), LocalTime.of(15, 15), null);

        assertThat(AntragTexte.titel(ansicht(urlaub, null))).isEqualTo("Urlaub · 26.–30.10.");
        assertThat(AntragTexte.titel(ansicht(zeitkorrektur(buchung, zeit(LocalDate.of(2026, 10, 19), 15, 45)), null)))
                .isEqualTo("Zeitkorrektur · 19.10.");
        assertThat(AntragTexte.titel(ansicht(abwesenheit(1, AbwesenheitsArt.KRANKHEIT, LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 8)), null))).isEqualTo("Krankheit · 07.–08.10.");
        assertThat(AntragTexte.titel(ansicht(storno(1, 5L, "Grund"), urlaub))).isEqualTo("Storno · 26.–30.10.");
    }

    @Test
    void wichtigsteAenderungEinerZeitkorrektur() {
        LocalDate tag = LocalDate.of(2026, 10, 19);
        BuchungInfo buchung = buchung(1, 1, tag, LocalTime.of(7, 0), LocalTime.of(15, 15), null);
        assertThat(AntragTexte.korrektur(zeitkorrektur(buchung, zeit(tag, 15, 45)))).isEqualTo("Ende 15:15 → 15:45");

        Zeitkorrekturantrag beginn = zeitkorrektur(buchung, buchung.ende());
        beginn.setNeuerBeginn(zeit(tag, 6, 30));
        assertThat(AntragTexte.korrektur(beginn)).isEqualTo("Beginn 07:00 → 06:30");

        Zeitkorrekturantrag standort = zeitkorrektur(buchung, buchung.ende());
        standort.setNeuerStandort(Standort.HOMEOFFICE);
        assertThat(AntragTexte.korrektur(standort)).isEqualTo("Standort Büro → Homeoffice");

        Zeitkorrekturantrag projekt = zeitkorrektur(buchung, buchung.ende());
        projekt.setNeuesProjektId(1024L);
        assertThat(AntragTexte.korrektur(projekt)).isEqualTo("Projekt geändert");
    }

    @Test
    void zeitraeumeUeberMonatsgrenzenUndEinzelneTage() {
        assertThat(AntragTexte.zeitraum(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2)))
                .isEqualTo("30.10.–02.11.");
        assertThat(AntragTexte.zeitraum(LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 7))).isEqualTo("07.12.");
    }

    @Test
    void statustexteJeSicht() {
        Abwesenheitsantrag urlaub = urlaub(1, Urlaubsart.ERHOLUNGSURLAUB, LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30));
        assertThat(AntragTexte.statustext(ansicht(urlaub, null), true)).isEqualTo("beantragt");
        assertThat(AntragTexte.statustext(ansicht(urlaub, null), false)).isEqualTo("offen");
        Abwesenheitsantrag krank = mitStatus(abwesenheit(1, AbwesenheitsArt.KRANKHEIT, LocalDate.of(2026, 10, 7),
                LocalDate.of(2026, 10, 8)), Antragsstatus.GENEHMIGT);
        assertThat(AntragTexte.statustext(ansicht(krank, null), true)).isEqualTo("bestätigt");
        assertThat(AntragTexte.statustext(ansicht(mitStatus(urlaub, Antragsstatus.ZURUECKGEZOGEN), null), true))
                .isEqualTo("zurückgezogen");
    }

    @Test
    void auswahlStandortUndDauer() {
        assertThat(AntragTexte.auswahlname(Antragstyp.STORNO)).isEqualTo("Änderung / Storno");
        assertThat(AntragTexte.auswahlname(Antragstyp.SONSTIGE_ABWESENHEIT)).isEqualTo("Sonstige Abwesenheit");
        assertThat(AntragTexte.standort(Standort.BUERO)).isEqualTo("Büro");
        assertThat(AntragTexte.standort(null)).isEqualTo("–");
        assertThat(AntragTexte.dauer(510)).isEqualTo("8:30");
        assertThat(AntragTexte.dauer(null)).isEqualTo("–");
    }
}
