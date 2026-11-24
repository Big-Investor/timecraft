package de.timecraft.material.view;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.material.model.Einheit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Tests fuer Formate.
 *
 * @author Tharsan Arumuganathar
 */
class FormateTest {

    @Test
    void betraegeDeutschMitTausenderpunktUndEuro() {
        assertThat(Formate.euro(new BigDecimal("1074.00"))).isEqualTo("1.074,00 €");
        assertThat(Formate.euroMitVorzeichen(new BigDecimal("3228.70"))).isEqualTo("+3.228,70 €");
        assertThat(Formate.euroMitVorzeichen(new BigDecimal("-0.01"))).isEqualTo("-0,01 €");
        assertThat(Formate.euroMitVorzeichen(BigDecimal.ZERO)).isEqualTo("0,00 €");
    }

    @Test
    void mengenWieImMockup() {
        assertThat(Formate.menge(new BigDecimal("12.00"), Einheit.QUADRATMETER)).isEqualTo("12,0 m²");
        assertThat(Formate.menge(new BigDecimal("4.25"), Einheit.LAUFMETER)).isEqualTo("4,25 lfm");
        assertThat(Formate.menge(new BigDecimal("36.00"), Einheit.STUECK)).isEqualTo("36 Stk.");
    }

    @Test
    void datumUndStunden() {
        assertThat(Formate.datum(Instant.parse("2026-10-07T23:30:00Z"))).isEqualTo("08.10.2026");
        assertThat(Formate.tagMonat(LocalDateTime.of(2026, 10, 5, 9, 0))).isEqualTo("05.10.");
        assertThat(Formate.stunden(8835)).isEqualTo("147:15 h");
    }
}
