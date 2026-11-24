package de.timecraft.material.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "Materialstamm" nur fuer den Admin (Gruppe Verwaltung).
 *
 * @author Tharsan Arumuganathar
 */
@Component
public class MaterialstammMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Materialstamm";
    }

    @Override
    public String route() {
        return MaterialListeView.ROUTE;
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.ADMIN);
    }

    @Override
    public int reihenfolge() {
        return 330;
    }
}
