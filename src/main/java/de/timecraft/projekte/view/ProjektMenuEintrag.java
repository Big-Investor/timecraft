package de.timecraft.projekte.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Menüpunkt "Projekte" für alle Rollen. Die Reihenfolge 220 setzt ihn in die Gruppe Betrieb
 * (200 bis 299), wie im Mockup nach "Anwesenheit" (M2) und vor "Material" (M4).
 *
 * @author Tim Stukenbröker
 */
@Component
public class ProjektMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Projekte";
    }

    @Override
    public String route() {
        return ProjektListeView.ROUTE;
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.values());
    }

    @Override
    public int reihenfolge() {
        return 220;
    }
}
