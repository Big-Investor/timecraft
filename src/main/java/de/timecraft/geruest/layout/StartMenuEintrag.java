package de.timecraft.geruest.layout;

import de.timecraft.benutzer.api.Rolle;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Menuepunkt "Start" fuer alle Rollen, immer der erste Punkt der Gruppe Zeit. */
@Component
public class StartMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Start";
    }

    @Override
    public String route() {
        return "";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.values());
    }

    @Override
    public int reihenfolge() {
        return 100;
    }
}
