package de.timecraft.geruest.layout;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Anmelden | TimeCraft")
@AnonymousAllowed
public class LoginView extends Div implements BeforeEnterObserver {

    private final LoginForm anmeldeformular = new LoginForm(deutscheTexte());

    public LoginView() {
        addClassName("login-view");
        anmeldeformular.setAction("login");
        anmeldeformular.setForgotPasswordButtonVisible(false);
        add(markenbereich(), formularbereich());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent ereignis) {
        boolean fehlgeschlagen = ereignis.getLocation().getQueryParameters().getParameters().containsKey("error");
        anmeldeformular.setError(fehlgeschlagen);
    }

    private Div markenbereich() {
        Span logo = new Span(VaadinIcon.CLOCK.create(), new Span("TimeCraft"));
        logo.addClassName("login-logo");
        Div titelbereich = new Div(
                new H1("Zeiterfassung für die Tischlerei."),
                new Paragraph("Stempeln, Projekte buchen, Urlaub beantragen – in der Werkstatt am Tablet oder im Büro."));
        titelbereich.addClassName("login-titel");
        Span fusszeile = new Span("Tischlerei Hartmann · Demo-Umgebung");
        fusszeile.addClassName("login-fusszeile");

        Div bereich = new Div(logo, titelbereich, fusszeile);
        bereich.addClassName("login-marke");
        return bereich;
    }

    private Div formularbereich() {
        Div karte = new Div(new H2("Anmelden"), new Paragraph("Mit Benutzername und Passwort."), anmeldeformular);
        karte.addClassName("login-karte");
        Div bereich = new Div(karte);
        bereich.addClassName("login-formular");
        return bereich;
    }

    private static LoginI18n deutscheTexte() {
        LoginI18n texte = LoginI18n.createDefault();
        texte.getForm().setTitle("");
        texte.getForm().setUsername("Benutzername");
        texte.getForm().setPassword("Passwort");
        texte.getForm().setSubmit("Anmelden");
        texte.getErrorMessage().setTitle("");
        texte.getErrorMessage().setMessage("Benutzername oder Passwort ist falsch.");
        texte.setAdditionalInformation(
                "Passwort vergessen? Wende dich an die Verwaltung – sie sendet dir ein Einmalpasswort per E-Mail.");
        return texte;
    }
}
