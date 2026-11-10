package de.timecraft.geruest.layout;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import de.timecraft.benutzer.api.Rolle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Seitenmenue: zeigt nur die Eintraege der Rolle, nach Reihenfolge sortiert und in Gruppen. */
public class SeitenMenue extends Div {

    public SeitenMenue(Rolle rolle, List<MenuEintrag> eintraege) {
        addClassName("seitenmenue");
        Map<String, List<MenuEintrag>> gruppen = eintraege.stream()
                .filter(eintrag -> eintrag.erlaubteRollen().contains(rolle))
                .sorted(Comparator.comparingInt(MenuEintrag::reihenfolge))
                .collect(Collectors.groupingBy(eintrag -> gruppenname(eintrag.reihenfolge()),
                        LinkedHashMap::new, Collectors.toList()));
        gruppen.values().forEach(this::gruppeHinzufuegen);
    }

    /** Name der Menuegruppe zu einer Reihenfolge, siehe {@link MenuEintrag}. */
    static String gruppenname(int reihenfolge) {
        if (reihenfolge < 200) {
            return "Zeit";
        }
        return reihenfolge < 300 ? "Betrieb" : "Verwaltung";
    }

    private void gruppeHinzufuegen(List<MenuEintrag> eintraege) {
        Span ueberschrift = new Span(gruppenname(eintraege.get(0).reihenfolge()));
        ueberschrift.addClassName("menue-gruppe");
        SideNav navigation = new SideNav();
        eintraege.forEach(eintrag -> navigation.addItem(new SideNavItem(eintrag.bezeichnung(), eintrag.route())));
        add(ueberschrift, navigation);
    }
}
