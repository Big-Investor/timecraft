package de.timecraft.projekte.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Prüft, dass ein leeres Namensfeld als "nicht filtern" gilt und ein frischer Filter nichts filtert. */
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
    void einNamensTextWirdUnveraendertGespeichert() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("Website");

        assertThat(filter.getName()).isEqualTo("Website");
    }

    @Test
    void einLeererNameWirdZuNull() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("");

        assertThat(filter.getName()).isNull();
    }

    @Test
    void einNameAusLeerzeichenWirdZuNull() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName("   ");

        assertThat(filter.getName()).isNull();
    }

    @Test
    void einNameNullBleibtNull() {
        ProjektFilter filter = new ProjektFilter();
        filter.setName(null);

        assertThat(filter.getName()).isNull();
    }
}
