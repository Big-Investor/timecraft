package de.timecraft.beispiel.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Menuepunkt des Beispielmoduls: Jedes Modul stellt seine Eintraege als Bean bereit. */
@Component
public class NotizMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Notizen";
    }

    @Override
    public String route() {
        return "notizen";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.values());
    }

    @Override
    public int reihenfolge() {
        return 190;
    }
}
