package de.timecraft.projekte.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Prüft, dass ein leeres Namensfeld als "nicht filtern" gilt und ein frischer Filter nichts filtert.
 *
 * @author Tim Stukenbröker
 */
class ProjektFilterTest {

    @Test
    void einFrischerFilterHatKeinenWert() {
        ProjektFilter filter = new ProjektFilter();

        assertThat(filter.getName()).isNull();
        assertThat(filter.getStatus()).isNull();
        assertThat(filter.getMitarbeiterId()).isNull();
        assertThat(filter.getStartVon()).isNull();
        assertThat(filter.getStartBis()).isNull();
        assertThat(filter.getZielVon()).isNull();
        assertThat(filter.getZielBis()).isNull();
    }

    @Test
    void einNamensTextBleibtUndLeereNamenWerdenZuNull() {
        ProjektFilter filter = new ProjektFilter();

        filter.setName("Website");
        assertThat(filter.getName()).isEqualTo("Website");

        for (String leer : new String[] {"", "   ", null}) {
            filter.setName(leer);
            assertThat(filter.getName()).isNull();
        }
    }
}
