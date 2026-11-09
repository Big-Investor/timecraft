package de.timecraft.geruest.layout;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.timecraft.geruest.common.AktuelleSitzung;
import jakarta.annotation.security.PermitAll;

/** Startseite: Begruessung mit Name und Rolle der angemeldeten Person. */
@Route(value = "", layout = HauptLayout.class)
@PageTitle("Start")
@PermitAll
public class StartView extends Div {

    public StartView(AktuelleSitzung sitzung) {
        addClassName("karte");
        add(new H2("Willkommen, " + sitzung.benutzername()),
                new Paragraph("Sie sind als " + Rollenanzeige.text(sitzung.rolle()) + " angemeldet."));
    }
}
