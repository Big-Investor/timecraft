package de.timecraft.kalender.view;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Anzeigetexte des Kalenders wie im Mockup (FA-M6-03, 04: Stunden und Minuten). */
class KalenderTexteTest {

    @Test
    void dauerInStundenUndMinuten() {
        assertThat(KalenderTexte.dauer(0)).isEqualTo("0:00 h");
        assertThat(KalenderTexte.dauer(206)).isEqualTo("3:26 h");
        assertThat(KalenderTexte.dauer(480)).isEqualTo("8:00 h");
        assertThat(KalenderTexte.dauer(1676)).isEqualTo("27:56 h");
    }

    @Test
    void standortWieImDokument() {
        assertThat(KalenderTexte.standort(Standort.BUERO)).isEqualTo("Büro");
        assertThat(KalenderTexte.standort(Standort.HOMEOFFICE)).isEqualTo("Homeoffice");
        assertThat(KalenderTexte.standort(Standort.UNTERWEGS)).isEqualTo("unterwegs");
    }

    @Test
    void projektKennungWieInDenMockups() {
        assertThat(KalenderTexte.projektKennung(1024L)).isEqualTo("P-1024");
        assertThat(KalenderTexte.projektKennung(null)).isEqualTo("–");
    }

    @Test
    void tagKopfUndZeiten() {
        assertThat(KalenderTexte.tagKopf(LocalDate.of(2026, 10, 19))).isEqualTo("Mo 19.10.");
        assertThat(KalenderTexte.tagKopf(LocalDate.of(2026, 10, 25))).isEqualTo("So 25.10.");
        assertThat(KalenderTexte.uhrzeit(LocalDateTime.of(2026, 10, 22, 7, 2))).isEqualTo("07:02");
        assertThat(KalenderTexte.datumUhrzeit(LocalDateTime.of(2026, 10, 20, 16, 30)))
                .isEqualTo("20.10.2026, 16:30 Uhr");
    }

    @Test
    void zeitraumDerWocheWieImMockup() {
        assertThat(KalenderTexte.zeitraum(Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)))
                .isEqualTo("KW 43 · 19.–25. Oktober 2026");
    }

    @Test
    void zeitraumDerWocheUeberMonatsUndJahresgrenze() {
        assertThat(KalenderTexte.zeitraum(Kalenderansicht.WOCHE, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .isEqualTo("KW 44 · 26. Oktober – 1. November 2026");
        assertThat(KalenderTexte.zeitraum(Kalenderansicht.WOCHE, LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)))
                .isEqualTo("KW 53 · 28. Dezember 2026 – 3. Januar 2027");
    }

    @Test
    void zeitraumVonTagUndMonat() {
        assertThat(KalenderTexte.zeitraum(Kalenderansicht.TAG, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22)))
                .isEqualTo("Donnerstag, 22. Oktober 2026");
        assertThat(KalenderTexte.zeitraum(Kalenderansicht.MONAT, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .isEqualTo("März 2026");
    }
}
