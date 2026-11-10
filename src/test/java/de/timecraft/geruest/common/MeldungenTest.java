package de.timecraft.geruest.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MeldungenTest {

    @Test
    void bekannteFehlerklassenZeigenIhreMeldung() {
        List<RuntimeException> ausnahmen = List.of(
                new FachlicheException("Das Datum liegt in der Zukunft."),
                new ZugriffVerweigertException("Dafür fehlt Ihnen die Berechtigung."),
                new NichtAngemeldetException("Bitte melden Sie sich an."),
                new NichtGefundenException("Das Projekt wurde nicht gefunden."));

        for (RuntimeException ausnahme : ausnahmen) {
            assertThat(Meldungen.fehlertext(ausnahme)).isEqualTo(ausnahme.getMessage());
        }
    }

    @Test
    void unbekannteFehlerZeigenNurAllgemeinenHinweis() {
        RuntimeException intern = new IllegalStateException("interner Zustand: Tabelle xyz");

        assertThat(Meldungen.fehlertext(intern))
                .isEqualTo(Meldungen.UNERWARTETER_FEHLER)
                .doesNotContain("xyz");
    }
}
