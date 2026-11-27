package de.timecraft.benutzer.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.junit.jupiter.api.Test;

/**
 * Prüft Aufbau und Texte der Anmeldeseite nach dem Mockup „M1 · Anmeldung“ (UC-M1-01).
 *
 * @author Leon-Selim Kannenberg
 */
class AnmeldeViewTest {

    @Test
    void zeigtMarkenbereichUndKarteWieImMockupUndIstOhneAnmeldungErreichbar() {
        AnmeldeView seite = new AnmeldeView();
        String text = seite.getElement().getTextRecursively();

        assertThat(text).contains("TimeCraft", "Zeiterfassung für die Tischlerei.",
                "Stempeln, Projekte buchen, Urlaub beantragen – in der Werkstatt am Tablet oder im Büro.",
                "Tischlerei Hartmann · Demo-Umgebung", "Anmelden", "Mit Benutzername und Passwort.");
        assertThat(seite.anmeldeformular.getAction()).isEqualTo("login");
        assertThat(seite.anmeldeformular.isForgotPasswordButtonVisible()).isFalse();
        assertThat(seite.anmeldeformular.isError()).isFalse();
        assertThat(AnmeldeView.class.getAnnotation(Route.class).value()).isEqualTo("login");
        assertThat(AnmeldeView.class.isAnnotationPresent(AnonymousAllowed.class)).isTrue();
    }

    @Test
    void formularIstDeutschBeschriftetUndZeigtDieMeldungDesAnmeldeService() {
        LoginI18n texte = AnmeldeView.deutscheTexte("Ihr Konto ist gesperrt. Bitte wenden Sie sich an die Verwaltung.");

        assertThat(texte.getForm().getUsername()).isEqualTo("Benutzername");
        assertThat(texte.getForm().getPassword()).isEqualTo("Passwort");
        assertThat(texte.getForm().getSubmit()).isEqualTo("Anmelden");
        assertThat(texte.getErrorMessage().getTitle()).isEmpty();
        assertThat(texte.getErrorMessage().getMessage())
                .isEqualTo("Ihr Konto ist gesperrt. Bitte wenden Sie sich an die Verwaltung.");
        assertThat(texte.getAdditionalInformation()).isEqualTo(
                "Passwort vergessen? Wende dich an die Verwaltung – sie sendet dir ein Einmalpasswort per E-Mail.");
    }
}
