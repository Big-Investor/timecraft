package de.timecraft.benutzer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.model.Benutzer;
import de.timecraft.geruest.common.FachlicheException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Prüft den {@link PasswortService} ohne Datenbank: Passwortregeln (QA-M1-03),
 * Hash statt Klartext (QA-M1-01) und Einmalpasswörter (QA-M1-01, QA-M1-12).
 *
 * @author Leon-Selim Kannenberg
 */
class PasswortServiceTest {

    private final PasswortService passwortService = TestDaten.passwortService();

    @Test
    void passwortregelnUndBcryptHash() {
        // Mindestens 8 Zeichen und eine Ziffer (QA-M1-03), höchstens 72 Byte (Grenze von BCrypt)
        assertThatCode(() -> passwortService.pruefeRegeln("hobel123")).doesNotThrowAnyException();
        assertThatThrownBy(() -> passwortService.pruefeRegeln("hobel12"))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Das Passwort muss mindestens 8 Zeichen lang sein.");
        assertThatThrownBy(() -> passwortService.pruefeRegeln(null))
                .hasMessage("Das Passwort muss mindestens 8 Zeichen lang sein.");
        assertThatThrownBy(() -> passwortService.pruefeRegeln("hobelbank"))
                .hasMessage("Das Passwort muss mindestens eine Ziffer enthalten.");
        String genau72 = "a1" + "b".repeat(70);
        String zuLang = "a1" + "b".repeat(71);
        assertThatCode(() -> passwortService.pruefeRegeln(genau72)).doesNotThrowAnyException();
        assertThatThrownBy(() -> passwortService.pruefeRegeln(zuLang)).isInstanceOf(FachlicheException.class);

        // Gespeichert wird nur der BCrypt-Hash (QA-M1-01); ohne Hash oder Passwort passt nichts
        String hash = passwortService.hashen("hobel123");
        assertThat(hash).isNotEqualTo("hobel123").startsWith("$2a$").hasSize(60);
        assertThat(passwortService.passt("hobel123", hash)).isTrue();
        assertThat(passwortService.passt("hobel124", hash)).isFalse();
        assertThat(passwortService.passt("hobel123", null)).isFalse();
        assertThat(passwortService.passt(null, hash)).isFalse();
        assertThat(passwortService.passt(zuLang, passwortService.hashen(genau72))).isFalse();
    }

    @Test
    void einmalpasswortIstZufaelligNurAlsHashGespeichertUndHoechstens24StundenGueltig() {
        Set<String> erzeugt = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String einmalpasswort = passwortService.einmalpasswortErzeugen();
            assertThat(einmalpasswort).hasSize(PasswortService.EINMALPASSWORT_LAENGE);
            assertThatCode(() -> passwortService.pruefeRegeln(einmalpasswort)).doesNotThrowAnyException();
            erzeugt.add(einmalpasswort);
        }
        // Zufällig heißt: 50 Versuche ergeben 50 verschiedene Einmalpasswörter.
        assertThat(erzeugt).hasSize(50);

        Benutzer benutzer = new Benutzer("j.becker", Rolle.MITARBEITER);
        benutzer.setPasswortHash(passwortService.hashen("altesPasswort1"));
        passwortService.einmalpasswortSetzen(benutzer, "Xy7kLmNp2qRs");
        assertThat(benutzer.getEinmalpasswortHash()).isNotEqualTo("Xy7kLmNp2qRs").hasSize(60);
        assertThat(passwortService.passt("Xy7kLmNp2qRs", benutzer.getEinmalpasswortHash())).isTrue();
        // Feste Uhr: 14.11.2026 10:00 Uhr, also gültig bis 15.11.2026 10:00 Uhr (QA-M1-12).
        assertThat(benutzer.getEinmalpasswortGueltigBis()).isEqualTo(LocalDateTime.of(2026, 11, 15, 10, 0));
        assertThat(benutzer.getPasswortHash()).isNull();
        assertThat(benutzer.isPasswortAenderungNoetig()).isTrue();

        // Bis genau zum Ende gültig, eine Minute davor abgelaufen bzw. ohne Einmalpasswort abgelaufen
        benutzer.setEinmalpasswort("hash", LocalDateTime.of(2026, 11, 14, 10, 0));
        assertThat(passwortService.einmalpasswortAbgelaufen(benutzer)).isFalse();
        benutzer.setEinmalpasswort("hash", LocalDateTime.of(2026, 11, 14, 9, 59));
        assertThat(passwortService.einmalpasswortAbgelaufen(benutzer)).isTrue();
        benutzer.einmalpasswortLoeschen();
        assertThat(passwortService.einmalpasswortAbgelaufen(benutzer)).isTrue();
    }
}
