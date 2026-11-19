package de.timecraft.projekte.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.model.Projekt;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Rechteregeln ohne Datenbank und ohne Spring: Die Klasse hat keine Abhängigkeiten,
 * man kann sie einfach mit "new" erzeugen.
 */
class ProjektBerechtigungTest {

    /** Die Projektleitung mit der Mitarbeiter-ID 2 verantwortet das Beispielprojekt. */
    private static final long PROJEKTLEITUNG_ID = 2L;

    private final ProjektBerechtigung berechtigung = new ProjektBerechtigung();
    private final Projekt projekt = new Projekt(PROJEKTLEITUNG_ID, "Website", null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    @Test
    void ohneAnmeldungIstNichtsErlaubt() {
        assertThatThrownBy(() -> berechtigung.pruefeAngemeldet(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> berechtigung.pruefeAdmin(null)).isInstanceOf(NichtAngemeldetException.class);
        assertThatThrownBy(() -> berechtigung.pruefeVerantwortlicheProjektleitung(PROJEKTLEITUNG_ID, null, projekt))
                .isInstanceOf(NichtAngemeldetException.class);
    }

    @Test
    void angemeldetSindAlleDreiRollen() {
        for (Rolle rolle : Rolle.values()) {
            assertThatCode(() -> berechtigung.pruefeAngemeldet(rolle)).doesNotThrowAnyException();
        }
    }

    @Test
    void nurAdminDarfAdminAktionen() {
        assertThatCode(() -> berechtigung.pruefeAdmin(Rolle.ADMIN)).doesNotThrowAnyException();

        assertThatThrownBy(() -> berechtigung.pruefeAdmin(Rolle.MITARBEITER))
                .isInstanceOf(ZugriffVerweigertException.class);
        assertThatThrownBy(() -> berechtigung.pruefeAdmin(Rolle.PROJEKTLEITUNG))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void verantwortlicheProjektleitungIstErlaubt() {
        assertThatCode(() -> berechtigung.pruefeVerantwortlicheProjektleitung(PROJEKTLEITUNG_ID, Rolle.PROJEKTLEITUNG, projekt))
                .doesNotThrowAnyException();
    }

    @Test
    void projektleitungEinesAnderenProjektsIstVerweigert() {
        long andereProjektleitung = 7L;

        assertThatThrownBy(() -> berechtigung.pruefeVerantwortlicheProjektleitung(andereProjektleitung, Rolle.PROJEKTLEITUNG, projekt))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void adminIstNieVerantwortlicheProjektleitung() {
        // Auch mit derselben ID wie die Projektleitung: Entscheidend ist die Rolle.
        assertThatThrownBy(() -> berechtigung.pruefeVerantwortlicheProjektleitung(PROJEKTLEITUNG_ID, Rolle.ADMIN, projekt))
                .isInstanceOf(ZugriffVerweigertException.class);
    }

    @Test
    void mitarbeiterIstNieVerantwortlicheProjektleitung() {
        assertThatThrownBy(() -> berechtigung.pruefeVerantwortlicheProjektleitung(PROJEKTLEITUNG_ID, Rolle.MITARBEITER, projekt))
                .isInstanceOf(ZugriffVerweigertException.class);
    }
}
