package de.timecraft.benutzer.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.timecraft.benutzer.service.BenutzerService;
import de.timecraft.benutzer.service.EingabeFehlerException;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import jakarta.annotation.security.PermitAll;

/**
 * Nach der Anmeldung mit einem Einmalpasswort legt die Person hier ihr eigenes Passwort fest
 * (FA-M1-09, US-M1-02). Bis dahin leitet {@link PasswortPflicht} jede andere Seite hierher um.
 *
 * <p>Die Seite sieht aus wie die Anmeldeseite (ohne Menü), weil die Person noch keine anderen
 * Seiten nutzen darf (Entscheidung Leon, 2026-11-15; es gibt kein Mockup dafür).
 *
 * @author Leon-Selim Kannenberg
 */
@Route("passwort-festlegen")
@PageTitle("Passwort festlegen | TimeCraft")
@PermitAll
public class PasswortFestlegenView extends Div implements BeforeEnterObserver {

    static final String ERKLAERUNG = "Sie haben sich mit einem Einmalpasswort angemeldet. "
            + "Bitte legen Sie jetzt Ihr eigenes Passwort fest.";
    static final String ERFOLG = "Ihr Passwort ist festgelegt.";

    private final BenutzerService benutzerService;
    private final AktuelleSitzung sitzung;

    final PasswordField neuesPasswort = Eingabefelder.passwortFeld("Neues Passwort");
    final PasswordField wiederholung = Eingabefelder.passwortFeld("Neues Passwort wiederholen");

    public PasswortFestlegenView(BenutzerService benutzerService, AktuelleSitzung sitzung,
            AuthenticationContext anmeldung) {
        this.benutzerService = benutzerService;
        this.sitzung = sitzung;
        addClassName("login-view");
        neuesPasswort.setHelperText(Eingabefelder.PASSWORTREGELN);
        add(new Markenbereich(), formularbereich(anmeldung));
    }

    /** Wer kein neues Passwort braucht, landet direkt auf der Startseite. */
    @Override
    public void beforeEnter(BeforeEnterEvent ereignis) {
        if (!benutzerService.mussPasswortAendern(sitzung.mitarbeiterId(), sitzung.rolle())) {
            ereignis.forwardTo("");
        }
    }

    private Div formularbereich(AuthenticationContext anmeldung) {
        Button festlegen = new Button("Passwort festlegen", ereignis -> festlegen());
        festlegen.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        festlegen.setWidthFull();

        Button abmelden = new Button("Abmelden", ereignis -> anmeldung.logout());
        abmelden.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        abmelden.setWidthFull();

        Div karte = new Div(new H2("Eigenes Passwort festlegen"), new Paragraph(ERKLAERUNG),
                neuesPasswort, wiederholung, festlegen, abmelden);
        karte.addClassName("login-karte");
        Div bereich = new Div(karte);
        bereich.addClassName("login-formular");
        return bereich;
    }

    /** Speichert das neue Passwort über den Service und öffnet danach die Startseite. */
    void festlegen() {
        try {
            benutzerService.erstesPasswortFestlegen(sitzung.mitarbeiterId(), sitzung.rolle(),
                    neuesPasswort.getValue(), wiederholung.getValue());
            Eingabefelder.fehlerZeigen(neuesPasswort, null);
            Eingabefelder.fehlerZeigen(wiederholung, null);
            Meldungen.erfolg(ERFOLG);
            getUI().ifPresent(ui -> ui.navigate(""));
        } catch (EingabeFehlerException fehler) {
            Eingabefelder.fehlerZeigen(neuesPasswort, fehler.meldungZu(BenutzerService.FELD_NEUES_PASSWORT));
            Eingabefelder.fehlerZeigen(wiederholung, fehler.meldungZu(BenutzerService.FELD_WIEDERHOLUNG));
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }
}
