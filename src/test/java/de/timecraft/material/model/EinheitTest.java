package de.timecraft.material.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests fuer Einheit.
 *
 * @author Tharsan Arumuganathar
 */
class EinheitTest {

    @Test
    void findetEinheitZumKuerzel() {
        assertThat(Einheit.ausKuerzel("m²")).isEqualTo(Einheit.QUADRATMETER);
        assertThat(Einheit.ausKuerzel("Stk.")).isEqualTo(Einheit.STUECK);
        assertThat(Einheit.ausKuerzel("lfm")).isEqualTo(Einheit.LAUFMETER);
    }

    @Test
    void jedesKuerzelPasstInDieSpalteUndIstEindeutig() {
        for (Einheit einheit : Einheit.values()) {
            assertThat(einheit.kuerzel().length()).isBetween(1, 5);
            assertThat(Einheit.ausKuerzel(einheit.kuerzel())).isEqualTo(einheit);
        }
    }

    @Test
    void unbekanntesKuerzelWirdAbgelehnt() {
        assertThatThrownBy(() -> Einheit.ausKuerzel("stk")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void konverterUebersetztInBeideRichtungenUndLaesstNullDurch() {
        EinheitKonverter konverter = new EinheitKonverter();

        assertThat(konverter.convertToDatabaseColumn(Einheit.LAUFMETER)).isEqualTo("lfm");
        assertThat(konverter.convertToEntityAttribute("lfm")).isEqualTo(Einheit.LAUFMETER);
        assertThat(konverter.convertToDatabaseColumn(null)).isNull();
        assertThat(konverter.convertToEntityAttribute(null)).isNull();
    }
}
