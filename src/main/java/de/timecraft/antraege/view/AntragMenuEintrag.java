package de.timecraft.antraege.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Menuepunkt "Anträge" in der Gruppe Zeit (Mockup: nach Kalender, vor Zeitauswertung). */
@Component
public class AntragMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Anträge";
    }

    @Override
    public String route() {
        return "antraege";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG);
    }

    @Override
    public int reihenfolge() {
        return 150;
    }
}
