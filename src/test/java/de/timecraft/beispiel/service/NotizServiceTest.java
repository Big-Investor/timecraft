package de.timecraft.beispiel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.timecraft.beispiel.model.Notiz;
import de.timecraft.beispiel.repository.NotizRepository;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotizServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Clock FESTE_UHR = Clock.fixed(Instant.parse("2026-10-22T07:30:00Z"), BERLIN);

    private final NotizRepository repository = mock(NotizRepository.class);
    private final NotizService service = new NotizService(repository, FESTE_UHR);

    @Test
    void anlegenSpeichertNotizMitErstellerUndFesterUhrzeit() {
        when(repository.save(any(Notiz.class))).thenAnswer(aufruf -> aufruf.getArgument(0));

        service.anlegen(2, Rolle.PROJEKTLEITUNG, "  Holz bestellen  ");

        ArgumentCaptor<Notiz> gespeichert = ArgumentCaptor.forClass(Notiz.class);
        verify(repository).save(gespeichert.capture());
        assertThat(gespeichert.getValue().getText()).isEqualTo("Holz bestellen");
        assertThat(gespeichert.getValue().getErstellerId()).isEqualTo(2);
        assertThat(gespeichert.getValue().getErstelltAm()).isEqualTo(LocalDateTime.of(2026, 10, 22, 9, 30));
    }

    @Test
    void anlegenMitLeeremTextWirftFachlicheExceptionUndSpeichertNichts() {
        assertThatThrownBy(() -> service.anlegen(1, Rolle.MITARBEITER, "   "))
                .isInstanceOf(FachlicheException.class)
                .hasMessage("Bitte geben Sie einen Text ein.");
        assertThatThrownBy(() -> service.anlegen(1, Rolle.MITARBEITER, null))
                .isInstanceOf(FachlicheException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void anlegenMitZuLangemTextWirftFachlicheException() {
        String zuLang = "x".repeat(NotizService.MAX_LAENGE + 1);

        assertThatThrownBy(() -> service.anlegen(1, Rolle.MITARBEITER, zuLang))
                .isInstanceOf(FachlicheException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void ohneAnmeldungIstKeineAktionErlaubt() {
        assertThatThrownBy(() -> service.anlegen(1, null, "Text")).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.alle(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> service.loeschen(null, 1)).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void alleLiefertDieNotizenDesRepositorys() {
        List<Notiz> notizen = List.of(new Notiz("a", LocalDateTime.of(2026, 10, 22, 9, 30), 1));
        when(repository.findAllByOrderByErstelltAmDesc()).thenReturn(notizen);

        for (Rolle rolle : Rolle.values()) {
            assertThat(service.alle(rolle)).isEqualTo(notizen);
        }
    }

    @Test
    void adminDarfLoeschen() {
        Notiz notiz = new Notiz("a", LocalDateTime.of(2026, 10, 22, 9, 30), 1);
        when(repository.findById(5L)).thenReturn(Optional.of(notiz));

        service.loeschen(Rolle.ADMIN, 5);

        verify(repository).delete(notiz);
    }

    @Test
    void mitarbeiterUndProjektleitungDuerfenNichtLoeschen() {
        for (Rolle rolle : new Rolle[] {Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG}) {
            assertThatThrownBy(() -> service.loeschen(rolle, 5))
                    .isInstanceOf(ZugriffVerweigertException.class);
        }
        verify(repository, never()).delete(any());
    }

    @Test
    void loeschenEinerUnbekanntenNotizWirftNichtGefundenException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loeschen(Rolle.ADMIN, 99))
                .isInstanceOf(NichtGefundenException.class);
    }
}
