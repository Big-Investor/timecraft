package de.timecraft.zeiterfassung.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.Standort;

/**
 * Auch das Model meldet Fehler als Fachausnahme mit deutschem Text, nie technisch (QA-A-02).
 *
 * @author Noah Cipa
 */
class ZeiteintragUndPauseTest {

    private static final Instant BEGINN = Instant.parse("2026-10-21T06:00:00Z");
    private static final Instant ENDE = Instant.parse("2026-10-21T14:00:00Z");

    @Test
    void ausstempelnSetztDasEnde() {
        Zeiteintrag eintrag = new Zeiteintrag(1, BEGINN, Standort.BUERO, null);

        eintrag.ausstempeln(ENDE);

        assertThat(eintrag.getEnde()).isEqualTo(ENDE);
    }

    @Test
    void zweimalAusstempelnIstEineFachausnahme() {
        Zeiteintrag eintrag = new Zeiteintrag(1, BEGINN, Standort.BUERO, null);
        eintrag.ausstempeln(ENDE);

        assertThatThrownBy(() -> eintrag.ausstempeln(ENDE.plusSeconds(60)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Der Zeiteintrag ist schon beendet.");
        assertThat(eintrag.getEnde()).isEqualTo(ENDE);
    }

    @Test
    void endeVorBeginnIstEineFachausnahme() {
        Zeiteintrag eintrag = new Zeiteintrag(1, BEGINN, Standort.BUERO, null);

        assertThatThrownBy(() -> eintrag.ausstempeln(BEGINN.minusSeconds(60)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Ende darf nicht vor dem Beginn liegen.");
        assertThat(eintrag.getEnde()).isNull();
    }

    @Test
    void pauseZweimalBeendenIstEineFachausnahme() {
        Pause pause = new Pause(new Zeiteintrag(1, BEGINN, Standort.BUERO, null), BEGINN.plusSeconds(3600));
        pause.pauseBeenden(BEGINN.plusSeconds(5400));

        assertThatThrownBy(() -> pause.pauseBeenden(BEGINN.plusSeconds(7200)))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die Pause ist schon beendet.");
    }

    @Test
    void pausenendeVorPausenbeginnIstEineFachausnahme() {
        Pause pause = new Pause(new Zeiteintrag(1, BEGINN, Standort.BUERO, null), BEGINN.plusSeconds(3600));

        assertThatThrownBy(() -> pause.pauseBeenden(BEGINN))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Ende der Pause darf nicht vor ihrem Beginn liegen.");
        assertThat(pause.getEnde()).isNull();
    }
}
