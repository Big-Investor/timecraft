package de.timecraft.antraege.view;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.layout.MenuEintrag;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Menuepunkt "Anträge prüfen" nur fuer die Projektleitung (Gruppe ab 300, Vorschlag aus dem Geruest-Status). */
@Component
public class AntragPruefungMenuEintrag implements MenuEintrag {

    @Override
    public String bezeichnung() {
        return "Anträge prüfen";
    }

    @Override
    public String route() {
        return "antraege/pruefen";
    }

    @Override
    public Set<Rolle> erlaubteRollen() {
        return Set.of(Rolle.PROJEKTLEITUNG);
    }

    @Override
    public int reihenfolge() {
        return 310;
    }
}
