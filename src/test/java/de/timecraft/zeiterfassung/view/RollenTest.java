package de.timecraft.zeiterfassung.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.timecraft.benutzer.api.Rolle;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

/**
 * Menue und Route lassen dieselben Rollen zu (FA-M2-28). Die Rechte selbst prueft der Service.
 *
 * @author Noah Cipa
 */
class RollenTest {

    private static final String[] ZEITERFASSUNG = { "MITARBEITER", "PROJEKTLEITUNG" };

    private static String[] rollen(Class<?> view) {
        return view.getAnnotation(RolesAllowed.class).value();
    }

    @Test
    void zeitenZeitkontoUndAnwesenheitNichtFuerAdmins() {
        assertThat(rollen(StempelView.class)).containsExactly(ZEITERFASSUNG);
        assertThat(rollen(MeineZeitenView.class)).containsExactly(ZEITERFASSUNG);
        assertThat(rollen(ZeitkontoView.class)).containsExactly(ZEITERFASSUNG);
        assertThat(rollen(AnwesenheitView.class)).containsExactly(ZEITERFASSUNG);
        assertThat(MenuEintraege.stempelMenuEintrag().erlaubteRollen())
                .containsExactlyInAnyOrder(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);
        assertThat(MenuEintraege.meineZeitenMenuEintrag().erlaubteRollen())
                .containsExactlyInAnyOrder(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);
        assertThat(MenuEintraege.zeitkontoMenuEintrag().erlaubteRollen())
                .containsExactlyInAnyOrder(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);
        assertThat(MenuEintraege.anwesenheitMenuEintrag().erlaubteRollen())
                .containsExactlyInAnyOrder(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);
    }

    @Test
    void feiertagePflegenNurAdmins() {
        assertThat(rollen(FeiertagView.class)).containsExactly("ADMIN");
        assertThat(MenuEintraege.feiertagMenuEintrag().erlaubteRollen()).containsExactly(Rolle.ADMIN);
    }

    @Test
    void abwesenheitskalenderFuerAlleAngemeldeten() {
        assertThat(KalenderView.class.isAnnotationPresent(PermitAll.class)).isTrue();
        assertThat(MenuEintraege.kalenderMenuEintrag().erlaubteRollen()).containsExactlyInAnyOrder(Rolle.values());
    }
}
