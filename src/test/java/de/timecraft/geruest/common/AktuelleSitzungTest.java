package de.timecraft.geruest.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AktuelleSitzungTest {

    private final AktuelleSitzung sitzung = new AktuelleSitzung();

    @AfterEach
    void sitzungLeeren() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void liefertMitarbeiterIdBenutzernameUndRolleDerAngemeldetenPerson() {
        anmelden(new TimeCraftBenutzer(2, "projektleitung", "hash", Rolle.PROJEKTLEITUNG));

        assertThat(sitzung.istAngemeldet()).isTrue();
        assertThat(sitzung.mitarbeiterId()).isEqualTo(2);
        assertThat(sitzung.benutzername()).isEqualTo("projektleitung");
        assertThat(sitzung.rolle()).isEqualTo(Rolle.PROJEKTLEITUNG);
    }

    @Test
    void ohneAnmeldungIstNiemandAngemeldetUndAbfragenWerfen() {
        assertThat(sitzung.istAngemeldet()).isFalse();
        assertThatThrownBy(sitzung::mitarbeiterId).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(sitzung::benutzername).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(sitzung::rolle).isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void anonymerBesucherGiltAlsNichtAngemeldet() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "schluessel", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(sitzung.istAngemeldet()).isFalse();
        assertThatThrownBy(sitzung::rolle).isInstanceOf(NichtAngemeldetException.class);
    }

    private void anmelden(TimeCraftBenutzer benutzer) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }
}
