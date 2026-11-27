package de.timecraft.benutzer.view;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import de.timecraft.benutzer.service.BenutzerService;
import de.timecraft.geruest.common.AktuelleSitzung;
import org.springframework.stereotype.Component;

/**
 * Sperrt nach einer Anmeldung mit Einmalpasswort alle anderen Seiten, bis die Person ein eigenes
 * Passwort festgelegt hat (US-M1-02: „Ohne neues Passwort ist kein Zugriff auf andere Seiten möglich“).
 *
 * <p>Vaadin ruft {@link #serviceInit} beim Start einmal auf. Wir melden uns dort für jeden
 * Seitenwechsel an (auch für die Seiten anderer Module) und leiten bei Bedarf auf
 * {@link PasswortFestlegenView} um. Ob ein neues Passwort nötig ist, sagt der {@link BenutzerService}.
 *
 * @author Leon-Selim Kannenberg
 */
@Component
public class PasswortPflicht implements VaadinServiceInitListener {

    private final BenutzerService benutzerService;
    private final AktuelleSitzung sitzung;

    public PasswortPflicht(BenutzerService benutzerService, AktuelleSitzung sitzung) {
        this.benutzerService = benutzerService;
        this.sitzung = sitzung;
    }

    @Override
    public void serviceInit(ServiceInitEvent ereignis) {
        ereignis.getSource().addUIInitListener(
                neuesFenster -> neuesFenster.getUI().addBeforeEnterListener(this::vorDemSeitenwechsel));
    }

    private void vorDemSeitenwechsel(BeforeEnterEvent ereignis) {
        if (mussUmleiten(ereignis.getNavigationTarget())) {
            ereignis.forwardTo(PasswortFestlegenView.class);
        }
    }

    /**
     * True, wenn die angemeldete Person zuerst ein eigenes Passwort festlegen muss und eine andere
     * Seite als „Passwort festlegen“ oder „Anmelden“ öffnen will.
     */
    boolean mussUmleiten(Class<?> zielseite) {
        if (zielseite == PasswortFestlegenView.class || zielseite == AnmeldeView.class) {
            return false;
        }
        if (!sitzung.istAngemeldet()) {
            return false;
        }
        return benutzerService.mussPasswortAendern(sitzung.mitarbeiterId(), sitzung.rolle());
    }
}
