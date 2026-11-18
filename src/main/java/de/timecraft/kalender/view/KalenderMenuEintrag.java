package de.timecraft.kalender.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Menüpunkt „Kalender“ für alle Rollen (FA-M6-01), Gruppe Zeit, im Mockup nach „Zeitkonto“. */
@Component
public class KalenderMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Kalender";
    }

    @Override
    public String route() {
        return KalenderView.ROUTE;
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.values());
    }

    @Override
    public int reihenfolge() {
        return 130;
    }
}
