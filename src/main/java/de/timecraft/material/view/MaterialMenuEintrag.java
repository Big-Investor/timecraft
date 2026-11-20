package de.timecraft.material.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/** "Material" fuer Projektleitung und Admin: Projektliste mit Materialplanung und Kosten (Gruppe Betrieb). */
@Component
public class MaterialMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Material";
    }

    @Override
    public String route() {
        return MaterialUebersichtView.ROUTE;
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.PROJEKTLEITUNG, Rolle.ADMIN);
    }

    @Override
    public int reihenfolge() {
        return 230;
    }
}
