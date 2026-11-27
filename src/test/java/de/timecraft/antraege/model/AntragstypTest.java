package de.timecraft.antraege.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import org.junit.jupiter.api.Test;

class AntragstypTest {

    @Test
    void ermitteltTypAusKlasseUndAbwesenheitsart() {
        assertThat(Antragstyp.von(new Zeitkorrekturantrag())).isEqualTo(Antragstyp.ZEITKORREKTUR);
        assertThat(Antragstyp.von(new Stornoantrag())).isEqualTo(Antragstyp.STORNO);
        assertThat(Antragstyp.von(abwesenheit(AbwesenheitsArt.URLAUB))).isEqualTo(Antragstyp.URLAUB);
        assertThat(Antragstyp.von(abwesenheit(AbwesenheitsArt.KRANKHEIT))).isEqualTo(Antragstyp.KRANKHEIT);
        assertThat(Antragstyp.von(abwesenheit(AbwesenheitsArt.SONSTIGE)))
                .isEqualTo(Antragstyp.SONSTIGE_ABWESENHEIT);
    }

    @Test
    void antraegeAndererModuleHabenKeinenM5Typ() {
        assertThat(Antragstyp.von(new MaterialanpassungsantragTestersatz())).isNull();
    }

    @Test
    void bezeichnungenSindDeutsch() {
        assertThat(Antragstyp.SONSTIGE_ABWESENHEIT.bezeichnung()).isEqualTo("Sonstige Abwesenheit");
        assertThat(Urlaubsart.UNBEZAHLTER_URLAUB.bezeichnung()).isEqualTo("Unbezahlter Urlaub");
    }

    @Test
    void neuerAntragIstOffen() {
        assertThat(new Stornoantrag().getStatus()).isEqualTo(Antragsstatus.OFFEN);
    }

    private static Abwesenheitsantrag abwesenheit(AbwesenheitsArt art) {
        Abwesenheitsantrag antrag = new Abwesenheitsantrag();
        antrag.setArt(art);
        return antrag;
    }
}
