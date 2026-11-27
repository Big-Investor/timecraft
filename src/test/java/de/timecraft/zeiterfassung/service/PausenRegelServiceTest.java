package de.timecraft.zeiterfassung.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.timecraft.zeiterfassung.model.Arbeitsblock;

/** @author Noah Cipa */
class PausenRegelServiceTest {

    private final PausenRegelService regeln = new PausenRegelService();

    @Test
    void minderjaehrigBisZumTagVorDemAchtzehntenGeburtstag() {
        LocalDate geburtsdatum = LocalDate.of(2008, 10, 23);

        assertThat(regeln.istMinderjaehrig(geburtsdatum, LocalDate.of(2026, 10, 22))).isTrue();
        assertThat(regeln.istMinderjaehrig(geburtsdatum, LocalDate.of(2026, 10, 23))).isFalse();
        assertThat(regeln.istMinderjaehrig(geburtsdatum, LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    void ohneGeburtsdatumGeltenDieRegelnFuerErwachsene() {
        assertThat(regeln.istMinderjaehrig(null, LocalDate.of(2026, 10, 22))).isFalse();
    }

    @Test
    void erwachseneBrauchenBisSechsStundenKeinePause() {
        assertThat(regeln.mindestPauseMinuten(6 * 60, false)).isEqualTo(0);
    }

    @Test
    void erwachseneBrauchenAbSechsStundenUndEinerMinuteDreissigMinuten() {
        assertThat(regeln.mindestPauseMinuten(6 * 60 + 1, false)).isEqualTo(30);
        assertThat(regeln.mindestPauseMinuten(9 * 60, false)).isEqualTo(30);
    }

    @Test
    void erwachseneBrauchenAbNeunStundenUndEinerMinuteFuenfundvierzigMinuten() {
        assertThat(regeln.mindestPauseMinuten(9 * 60 + 1, false)).isEqualTo(45);
    }

    @Test
    void minderjaehrigeBrauchenAbViereinhalbStundenDreissigMinuten() {
        assertThat(regeln.mindestPauseMinuten(4 * 60 + 30, true)).isEqualTo(0);
        assertThat(regeln.mindestPauseMinuten(4 * 60 + 31, true)).isEqualTo(30);
    }

    @Test
    void minderjaehrigeBrauchenAbSechsStundenSechzigMinuten() {
        assertThat(regeln.mindestPauseMinuten(6 * 60, true)).isEqualTo(30);
        assertThat(regeln.mindestPauseMinuten(6 * 60 + 1, true)).isEqualTo(60);
    }

    @Test
    void unterbrechungUnterFuenfzehnMinutenZaehltNichtAlsPause() {
        List<Duration> pausen = List.of(Duration.ofMinutes(14), Duration.ofMinutes(15), Duration.ofMinutes(20));

        assertThat(regeln.anrechenbarePausenMinuten(pausen)).isEqualTo(35);
    }

    @Test
    void fehlendePauseWirdBerechnet() {
        // 7 Stunden Arbeit, nur 10 Minuten Pause (zaehlt nicht) -> 30 Minuten fehlen
        assertThat(regeln.fehlendePauseMinuten(7 * 60, List.of(Duration.ofMinutes(10)), false)).isEqualTo(30);
        // 7 Stunden Arbeit, 20 Minuten Pause -> 10 Minuten fehlen
        assertThat(regeln.fehlendePauseMinuten(7 * 60, List.of(Duration.ofMinutes(20)), false)).isEqualTo(10);
    }

    @Test
    void genugPauseHeisstNichtsFehlt() {
        assertThat(regeln.fehlendePauseMinuten(7 * 60, List.of(Duration.ofMinutes(45)), false)).isEqualTo(0);
        assertThat(regeln.fehlendePauseMinuten(5 * 60, List.of(), false)).isEqualTo(0);
    }
    
    @Test
    void hoechstarbeitszeitErwachseneZehnStunden() {
        assertThat(regeln.hoechstarbeitszeitUeberschritten(10 * 60, false)).isFalse();
        assertThat(regeln.hoechstarbeitszeitUeberschritten(10 * 60 + 1, false)).isTrue();
    }

    @Test
    void hoechstarbeitszeitMinderjaehrigeAchtStunden() {
        assertThat(regeln.hoechstarbeitszeitUeberschritten(8 * 60, true)).isFalse();
        assertThat(regeln.hoechstarbeitszeitUeberschritten(8 * 60 + 1, true)).isTrue();
    }

    @Test
    void vorwarnungTagAbDreissigMinutenVorDerGrenze() {
        assertThat(regeln.vorwarnungTag(9 * 60 + 29, false)).isFalse();
        assertThat(regeln.vorwarnungTag(9 * 60 + 30, false)).isTrue();
        assertThat(regeln.vorwarnungTag(7 * 60 + 29, true)).isFalse();
        assertThat(regeln.vorwarnungTag(7 * 60 + 30, true)).isTrue();
    }

    @Test
    void wochengrenzeVierzigStundenNurFuerMinderjaehrige() {
        assertThat(regeln.wochenhoechstarbeitszeitUeberschritten(40 * 60, true)).isFalse();
        assertThat(regeln.wochenhoechstarbeitszeitUeberschritten(40 * 60 + 1, true)).isTrue();
        assertThat(regeln.wochenhoechstarbeitszeitUeberschritten(50 * 60, false)).isFalse();
    }

    @Test
    void vorwarnungWocheAbDreissigMinutenVorVierzigStunden() {
        assertThat(regeln.vorwarnungWoche(39 * 60 + 29, true)).isFalse();
        assertThat(regeln.vorwarnungWoche(39 * 60 + 30, true)).isTrue();
        assertThat(regeln.vorwarnungWoche(45 * 60, false)).isFalse();
    }

    // ---- Pausenregel je Tag: Abzug je Block ----

    private static final Instant ACHT_UHR = Instant.parse("2026-10-21T06:00:00Z");

    /** Block, der {@code abMinute} Minuten nach 08:00 beginnt. */
    private Arbeitsblock block(int abMinute, int dauerMinuten, Duration... pausen) {
        Instant beginn = ACHT_UHR.plus(Duration.ofMinutes(abMinute));
        return new Arbeitsblock(beginn, beginn.plus(Duration.ofMinutes(dauerMinuten)), List.of(pausen));
    }

    @Test
    void arbeitsMinutenSindDauerOhneAllePausen() {
        assertThat(block(0, 480, Duration.ofMinutes(30), Duration.ofMinutes(10)).arbeitsMinuten()).isEqualTo(440);
    }

    @Test
    void bloeckeEinesTagesZaehlenZusammenAbzugVomSpaetesten() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 240), block(240, 240)), false))
                .containsExactly(0, 30);
    }

    @Test
    void lueckeVonVierzehnMinutenZaehltNichtFuenfzehnSchon() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 240), block(254, 240)), false))
                .containsExactly(0, 30);
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 240), block(255, 240)), false))
                .containsExactly(0, 15);
    }

    @Test
    void erfasstePausenUndLueckenZaehlenZusammen() {
        // 30 Minuten Luecke + 15 Minuten erfasst = 45; die 10 Minuten zaehlen nicht
        assertThat(regeln.pflichtpausenAbzuege(List.of(
                block(0, 315, Duration.ofMinutes(15)), block(345, 300, Duration.ofMinutes(10))), false))
                .containsExactly(0, 0);
    }

    @Test
    void reichtDerSpaetesteBlockNichtGehtDerRestAufDenDavor() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 360), block(360, 10)), false))
                .containsExactly(20, 10);
    }

    @Test
    void ueberNeunStundenAmTagFuenfundvierzigMinuten() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 300), block(300, 241)), false))
                .containsExactly(0, 45);
    }

    @Test
    void minderjaehrigeJeTagAbViereinhalbStunden() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 135), block(135, 135)), true))
                .containsExactly(0, 0);
        assertThat(regeln.pflichtpausenAbzuege(List.of(block(0, 135), block(135, 136)), true))
                .containsExactly(0, 30);
    }

    @Test
    void ohneBloeckeGibtEsKeineAbzuege() {
        assertThat(regeln.pflichtpausenAbzuege(List.of(), false)).isEmpty();
    }

    @Test
    void wochengrenzeErreichtNurGenauBeiVierzigStundenUndNurFuerMinderjaehrige() {
        assertThat(regeln.wochenhoechstarbeitszeitErreicht(40 * 60, true)).isTrue();
        assertThat(regeln.wochenhoechstarbeitszeitErreicht(40 * 60 - 1, true)).isFalse();
        assertThat(regeln.wochenhoechstarbeitszeitErreicht(40 * 60, false)).isFalse();
    }
}
