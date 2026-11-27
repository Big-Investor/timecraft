package de.timecraft.benutzer.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Menüpunkt „Passwort ändern“ für alle Rollen, als letzter Punkt der Gruppe „Verwaltung“
 * (Entscheidung Leon, 2026-11-15; das Mockup zeigt dafür keinen Menüpunkt).
 *
 * @author Leon-Selim Kannenberg
 */
@Component
public class PasswortAendernMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Passwort ändern";
    }

    @Override
    public String route() {
        return "passwort";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.values());
    }

    @Override
    public int reihenfolge() {
        return 390;
    }
}
