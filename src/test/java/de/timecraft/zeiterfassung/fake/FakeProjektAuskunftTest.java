package de.timecraft.zeiterfassung.fake;

import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.JONAS_BECKER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.LUKAS_MEYER;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.PAUL_HARTMANN;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.SABINE_ENGEL;
import static de.timecraft.zeiterfassung.fake.FakeMitarbeiterAuskunft.UWE_SCHULZ;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.BUEROUMBAU_SCHMITT;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.EINBAUKUECHE_WAGNER;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.LADENBAU_KRAUSE;
import static de.timecraft.zeiterfassung.fake.FakeProjektAuskunft.TREPPE_LINDENSTRASSE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.timecraft.projekte.api.ProjektInfo;

/** @author Noah Cipa */
class FakeProjektAuskunftTest {

    private final FakeProjektAuskunft fake = new FakeProjektAuskunft();

    @Test
    void dreiAktiveUndEinArchiviertesProjekt() {
        assertThat(fake.alle()).extracting(ProjektInfo::aktiv).containsExactly(true, true, false, true);
        assertThat(fake.finde(LADENBAU_KRAUSE)).get().extracting(ProjektInfo::aktiv).isEqualTo(false);
        assertThat(fake.finde(9999)).isEmpty();
    }

    @Test
    void aktiveProjekteVonEnthaeltKeineArchiviertenUndNurZugeordnete() {
        assertThat(fake.aktiveProjekteVon(JONAS_BECKER)).extracting(ProjektInfo::projektId)
                .containsExactly(EINBAUKUECHE_WAGNER, TREPPE_LINDENSTRASSE);
        assertThat(fake.aktiveProjekteVon(LUKAS_MEYER)).extracting(ProjektInfo::projektId)
                .containsExactly(BUEROUMBAU_SCHMITT);
        assertThat(fake.aktiveProjekteVon(UWE_SCHULZ)).isEmpty();
    }

    @Test
    void verantworteteProjekteAuchArchivierte() {
        assertThat(fake.verantworteteProjekte(PAUL_HARTMANN)).hasSize(3);
        assertThat(fake.verantworteteProjekte(SABINE_ENGEL)).extracting(ProjektInfo::projektId)
                .containsExactly(BUEROUMBAU_SCHMITT);
        assertThat(fake.verantworteteProjekte(JONAS_BECKER)).isEmpty();
    }

    @Test
    void zuordnungen() {
        assertThat(fake.istZugeordnet(JONAS_BECKER, LADENBAU_KRAUSE)).isTrue();
        assertThat(fake.istZugeordnet(LUKAS_MEYER, EINBAUKUECHE_WAGNER)).isFalse();
        assertThat(fake.zugeordneteMitarbeiter(9999)).isEmpty();
    }
}
