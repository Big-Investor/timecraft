package de.timecraft.benutzer.view;

import com.vaadin.flow.component.UI;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Hilfen für die View-Tests: eine Person anmelden (Spring-Security-Kontext) und ein leeres
 * Vaadin-Fenster bereitstellen, damit Benachrichtigungen ({@code Meldungen}) angezeigt werden können.
 *
 * @author Leon-Selim Kannenberg
 */
final class ViewTestHilfe {

    /**
     * Vaadin merkt sich das aktuelle Fenster nur schwach (es kann vom Garbage Collector entfernt werden).
     * Dieses Feld hält es fest, solange ein Test läuft.
     */
    private static UI fenster;

    private ViewTestHilfe() {
    }

    static void anmelden(Mitarbeiter mitarbeiter) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(mitarbeiter.getMitarbeiterId(),
                mitarbeiter.getBenutzer().getBenutzername(), "", mitarbeiter.getBenutzer().getRolle(),
                mitarbeiter.getName());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }

    static void fensterOeffnen() {
        fenster = new UI();
        UI.setCurrent(fenster);
    }

    static void aufraeumen() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
        fenster = null;
    }
}
