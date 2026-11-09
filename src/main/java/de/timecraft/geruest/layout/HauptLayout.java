package de.timecraft.geruest.layout;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.timecraft.geruest.common.AktuelleSitzung;
import java.util.List;
import java.util.Locale;

/**
 * Rahmen aller Seiten: Seitenmenue links, Kopfzeile mit Seitentitel und angemeldeter Person.
 * Eine View benutzt ihn mit {@code @Route(value = "...", layout = HauptLayout.class)}.
 * Der Seitentitel der Kopfzeile kommt aus {@code @PageTitle} der View.
 */
public class HauptLayout extends AppLayout implements AfterNavigationObserver {

    private final List<MenuEintrag> eintraege;
    private final Span gruppe = new Span();
    private final H1 titel = new H1();

    public HauptLayout(AktuelleSitzung sitzung, AuthenticationContext anmeldung, List<MenuEintrag> eintraege) {
        this.eintraege = eintraege;
        setPrimarySection(Section.DRAWER);
        addToDrawer(seitenleiste(sitzung, anmeldung));
        addToNavbar(new DrawerToggle(), kopfzeile(sitzung));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent ereignis) {
        String pfad = ereignis.getLocation().getPath();
        gruppe.setText(eintraege.stream()
                .filter(eintrag -> eintrag.route().equals(pfad))
                .map(eintrag -> SeitenMenue.gruppenname(eintrag.reihenfolge()))
                .findFirst()
                .orElse(""));
        titel.setText(seitentitel());
    }

    private String seitentitel() {
        PageTitle seitentitel = getContent().getClass().getAnnotation(PageTitle.class);
        return seitentitel == null ? "" : seitentitel.value();
    }

    private Div seitenleiste(AktuelleSitzung sitzung, AuthenticationContext anmeldung) {
        Span logo = new Span(VaadinIcon.CLOCK.create(), new Span("TimeCraft"));
        logo.addClassName("seitenleiste-logo");
        Button abmelden = new Button("Abmelden", ereignis -> anmeldung.logout());
        abmelden.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        abmelden.addClassName("seitenleiste-abmelden");

        Div leiste = new Div(logo, new SeitenMenue(sitzung.rolle(), eintraege), abmelden);
        leiste.addClassName("seitenleiste");
        return leiste;
    }

    private Div kopfzeile(AktuelleSitzung sitzung) {
        gruppe.addClassName("kopfzeile-gruppe");
        Div seite = new Div(gruppe, titel);
        seite.addClassName("kopfzeile-seite");

        String benutzername = sitzung.benutzername();
        Span initialen = new Span(benutzername.substring(0, Math.min(2, benutzername.length())).toUpperCase(Locale.GERMAN));
        initialen.addClassName("kopfzeile-initialen");
        Span name = new Span(benutzername);
        name.addClassName("kopfzeile-name");
        Span rolle = new Span(Rollenanzeige.text(sitzung.rolle()));
        rolle.addClassName("kopfzeile-rolle");
        Div person = new Div(initialen, new Div(name, rolle));
        person.addClassName("kopfzeile-person");

        Div kopfzeile = new Div(seite, person);
        kopfzeile.addClassName("kopfzeile");
        return kopfzeile;
    }
}
