package de.timecraft.benutzer.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.benutzer.service.BenutzerService;
import de.timecraft.benutzer.service.EingabeFehlerException;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.geruest.layout.HauptLayout;
import jakarta.annotation.security.PermitAll;

/**
 * Eigenes Passwort ändern (UC-M1-03, FA-M1-06, US-M1-04) für alle Rollen. Das bisherige Passwort
 * dient zur Bestätigung. Es gibt kein Mockup: einfache weiße Karte wie im übrigen Programm,
 * erreichbar über den Menüpunkt „Passwort ändern“ (Entscheidung Leon, 2026-11-15).
 *
 * @author Leon-Selim Kannenberg
 */
@Route(value = "passwort", layout = HauptLayout.class)
@PageTitle("Passwort ändern")
@PermitAll
public class PasswortAendernView extends Div {

    static final String ERFOLG = "Ihr Passwort wurde geändert.";

    private final BenutzerService benutzerService;
    private final AktuelleSitzung sitzung;

    final PasswordField altesPasswort = Eingabefelder.passwortFeld("Bisheriges Passwort");
    final PasswordField neuesPasswort = Eingabefelder.passwortFeld("Neues Passwort");
    final PasswordField wiederholung = Eingabefelder.passwortFeld("Neues Passwort wiederholen");

    public PasswortAendernView(BenutzerService benutzerService, AktuelleSitzung sitzung) {
        this.benutzerService = benutzerService;
        this.sitzung = sitzung;
        addClassName("karte");
        setMaxWidth("32rem");
        neuesPasswort.setHelperText(Eingabefelder.PASSWORTREGELN);

        Button aendern = new Button("Passwort ändern", ereignis -> aendern());
        aendern.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new H2("Eigenes Passwort ändern"),
                new Paragraph("Zur Bestätigung geben Sie bitte Ihr bisheriges Passwort ein."),
                altesPasswort, neuesPasswort, wiederholung, aendern);
    }

    /** Ändert das Passwort über den Service. Fehler erscheinen am betroffenen Feld. */
    void aendern() {
        try {
            benutzerService.passwortAendern(sitzung.mitarbeiterId(), sitzung.rolle(),
                    altesPasswort.getValue(), neuesPasswort.getValue(), wiederholung.getValue());
            altesPasswort.clear();
            neuesPasswort.clear();
            wiederholung.clear();
            fehlerZeigen(null);
            Meldungen.erfolg(ERFOLG);
        } catch (EingabeFehlerException fehler) {
            fehlerZeigen(fehler);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    /** Zeigt die Meldungen an den Feldern; ohne Fehler ({@code null}) werden alle Meldungen entfernt. */
    private void fehlerZeigen(EingabeFehlerException fehler) {
        String meldungAlt = null;
        String meldungNeu = null;
        String meldungWiederholung = null;
        if (fehler != null) {
            meldungAlt = fehler.meldungZu(BenutzerService.FELD_ALTES_PASSWORT);
            meldungNeu = fehler.meldungZu(BenutzerService.FELD_NEUES_PASSWORT);
            meldungWiederholung = fehler.meldungZu(BenutzerService.FELD_WIEDERHOLUNG);
        }
        Eingabefelder.fehlerZeigen(altesPasswort, meldungAlt);
        Eingabefelder.fehlerZeigen(neuesPasswort, meldungNeu);
        Eingabefelder.fehlerZeigen(wiederholung, meldungWiederholung);
    }
}
