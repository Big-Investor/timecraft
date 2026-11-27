package de.timecraft.benutzer.fake;

import static org.assertj.core.api.Assertions.assertThat;

import de.timecraft.benutzer.service.EinmalpasswortMail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Prüft die Mail-Variante für den lokalen Start: Sie verschickt nichts und meldet
 * eine simulierte Zustellung zurück (Teamentscheidung 04.11.2026). Das Einmalpasswort steht im Log.
 *
 * @author Leon-Selim Kannenberg
 */
@ExtendWith(OutputCaptureExtension.class)
class LogMailServiceTest {

    @Test
    void meldetSimulierteZustellung() {
        EinmalpasswortMail mail = EinmalpasswortMail.erstellen("jonas.becker@test.example", "Jonas Becker",
                "j.becker", "Xy7kLmNp2qRs");

        String rueckmeldung = new LogMailService().senden(mail);

        assertThat(rueckmeldung).isEqualTo("E-Mail wurde zugestellt.");
    }

    @Test
    void schreibtEinmalpasswortInsLogDamitEsLokalSichtbarIst(CapturedOutput ausgabe) {
        EinmalpasswortMail mail = EinmalpasswortMail.erstellen("jonas.becker@test.example", "Jonas Becker",
                "j.becker", "Xy7kLmNp2qRs");

        new LogMailService().senden(mail);

        assertThat(ausgabe.getOut()).contains("jonas.becker@test.example", "Xy7kLmNp2qRs");
    }
}
