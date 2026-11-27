package de.timecraft.benutzer.view;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.timecraft.benutzer.service.AnmeldeService;
import org.springframework.security.web.WebAttributes;

/**
 * Anmeldeseite (UC-M1-01, FA-M1-01, Mockup „M1 · Anmeldung“). Ersetzt die provisorische
 * Anmeldeseite des Gerüsts.
 *
 * <p>Das Formular schickt Benutzername und Passwort an Spring Security; geprüft wird im
 * {@code AnmeldeService}. Schlägt die Anmeldung fehl, leitet Spring Security auf
 * {@code /login?error} zurück und legt die Ausnahme in der Sitzung ab. Welchen Text wir dann
 * zeigen, entscheidet {@link AnmeldeService#meldungFuer(Object)}.
 *
 * @author Leon-Selim Kannenberg
 */
@Route("login")
@PageTitle("Anmelden | TimeCraft")
@AnonymousAllowed
public class AnmeldeView extends Div implements BeforeEnterObserver {

    static final String HINWEIS_PASSWORT_VERGESSEN =
            "Passwort vergessen? Wende dich an die Verwaltung – sie sendet dir ein Einmalpasswort per E-Mail.";

    final LoginForm anmeldeformular = new LoginForm();

    public AnmeldeView() {
        addClassName("login-view");
        anmeldeformular.setI18n(deutscheTexte(AnmeldeService.FALSCHE_DATEN));
        anmeldeformular.setAction("login");
        anmeldeformular.setForgotPasswordButtonVisible(false);
        add(new Markenbereich(), formularbereich());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent ereignis) {
        boolean fehlgeschlagen = ereignis.getLocation().getQueryParameters().getParameters().containsKey("error");
        if (fehlgeschlagen) {
            String meldung = AnmeldeService.meldungFuer(letzteAnmeldeAusnahme());
            anmeldeformular.setI18n(deutscheTexte(meldung));
        }
        anmeldeformular.setError(fehlgeschlagen);
    }

    private Div formularbereich() {
        Div karte = new Div(new H2("Anmelden"), new Paragraph("Mit Benutzername und Passwort."), anmeldeformular);
        karte.addClassName("login-karte");
        Div bereich = new Div(karte);
        bereich.addClassName("login-formular");
        return bereich;
    }

    /** Die Ausnahme der letzten fehlgeschlagenen Anmeldung, die Spring Security in der Sitzung ablegt. */
    private Object letzteAnmeldeAusnahme() {
        VaadinSession sitzung = VaadinSession.getCurrent();
        if (sitzung == null) {
            return null;
        }
        return sitzung.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }

    /** Deutsche Beschriftungen des Anmeldeformulars wie im Mockup. */
    static LoginI18n deutscheTexte(String fehlermeldung) {
        LoginI18n texte = LoginI18n.createDefault();
        texte.getForm().setTitle("");
        texte.getForm().setUsername("Benutzername");
        texte.getForm().setPassword("Passwort");
        texte.getForm().setSubmit("Anmelden");
        texte.getErrorMessage().setTitle("");
        texte.getErrorMessage().setMessage(fehlermeldung);
        texte.setAdditionalInformation(HINWEIS_PASSWORT_VERGESSEN);
        return texte;
    }
}
