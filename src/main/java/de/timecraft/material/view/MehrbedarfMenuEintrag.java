package de.timecraft.material.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "Material" fuer Mitarbeiter: fuehrt direkt zu "Mehrbedarf anfordern" (Mockup). Gleiche Bezeichnung und
 * Stelle wie {@link MaterialMenuEintrag}, aber andere Rolle; jede Person sieht genau einen Eintrag "Material".
 *
 * @author Tharsan Arumuganathar
 */
@Component
public class MehrbedarfMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Material";
    }

    @Override
    public String route() {
        return AnpassungsantragView.ROUTE;
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.MITARBEITER);
    }

    @Override
    public int reihenfolge() {
        return 230;
    }
}
