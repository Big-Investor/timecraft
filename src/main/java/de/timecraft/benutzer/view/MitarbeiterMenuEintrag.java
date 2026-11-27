package de.timecraft.benutzer.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Menüpunkt „Mitarbeiter“ für Projektleitung und Admin, erster Punkt der Gruppe „Verwaltung“
 * (Mockup „Mitarbeiterliste“).
 *
 * @author Leon-Selim Kannenberg
 */
@Component
public class MitarbeiterMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Mitarbeiter";
    }

    @Override
    public String route() {
        return "mitarbeiter";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.PROJEKTLEITUNG, Rolle.ADMIN);
    }

    @Override
    public int reihenfolge() {
        return 300;
    }
}
