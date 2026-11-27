package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import de.timecraft.geruest.common.FachlicheException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Prüft den Mailversand über SMTP mit einem nachgebildeten {@link JavaMailSender}:
 * Erfolg und simulierter Versandfehler (QA-M1-11), dazu Inhalt und Adressprüfung der Mail.
 *
 * @author Leon-Selim Kannenberg
 */
class MailServiceTest {

    private final JavaMailSender mailVersand = mock(JavaMailSender.class);
    private final SmtpMailService mailService = new SmtpMailService(mailVersand, "verwaltung@test.example");

    @Test
    void mailWirdMitAbsenderEmpfaengerBetreffUndTextVersendet() {
        EinmalpasswortMail mail = EinmalpasswortMail.erstellen("jonas.becker@test.example", "Jonas Becker",
                "j.becker", "Xy7kLmNp2qRs");

        String rueckmeldung = mailService.senden(mail);

        ArgumentCaptor<SimpleMailMessage> gesendet = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailVersand).send(gesendet.capture());
        SimpleMailMessage nachricht = gesendet.getValue();
        assertThat(nachricht.getFrom()).isEqualTo("verwaltung@test.example");
        assertThat(nachricht.getTo()).containsExactly("jonas.becker@test.example");
        assertThat(nachricht.getSubject()).isEqualTo("Ihr Einmalpasswort für TimeCraft");
        assertThat(nachricht.getText()).contains("Hallo Jonas Becker", "j.becker", "Xy7kLmNp2qRs", "24 Stunden");
        assertThat(rueckmeldung).isEqualTo("Das Einmalpasswort wurde per E-Mail an jonas.becker@test.example gesendet.");
    }

    @Test
    void versandfehlerUndUngueltigeAdressenErgebenVerstaendlicheMeldungen() {
        doThrow(new MailSendException("Connection refused: localhost:587"))
                .when(mailVersand).send(any(SimpleMailMessage.class));
        EinmalpasswortMail mail = EinmalpasswortMail.erstellen("jonas.becker@test.example", "Jonas Becker",
                "j.becker", "Xy7kLmNp2qRs");

        assertThatThrownBy(() -> mailService.senden(mail))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Die E-Mail mit dem Einmalpasswort konnte nicht versendet werden. "
                        + "Bitte versuchen Sie es später erneut.")
                .hasMessageNotContaining("Connection");

        assertThatThrownBy(() -> EinmalpasswortMail.erstellen("  ", "Jonas Becker", "j.becker", "Xy7kLmNp2qRs"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Für diesen Mitarbeiter ist keine E-Mail-Adresse hinterlegt.");
        assertThatThrownBy(() -> EinmalpasswortMail.erstellen(null, "Jonas Becker", "j.becker", "Xy7kLmNp2qRs"))
                .isInstanceOf(FachlicheException.class);
        String[] ungueltig = {"jonas.becker", "@test.example", "jonas@test", "jonas@.example", "jonas@test.",
            "jonas becker@test.example"};
        for (String adresse : ungueltig) {
            assertThatThrownBy(() -> EinmalpasswortMail.erstellen(adresse, "Jonas Becker", "j.becker", "Xy7kLmNp2qRs"))
                    .isInstanceOf(FachlicheException.class)
                    .hasMessage("Die E-Mail-Adresse „" + adresse
                            + "“ ist ungültig. Bitte korrigieren Sie sie in den Stammdaten.");
        }
    }
}
