package de.timecraft.geruest.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import de.timecraft.benutzer.api.Rolle;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeitenMenueTest {

    private final List<MenuEintrag> eintraege = List.of(
            eintrag("Projekte", "projekte", 210, Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG, Rolle.ADMIN),
            eintrag("Mitarbeiter", "mitarbeiter", 310, Rolle.ADMIN),
            eintrag("Meine Zeiten", "zeiten", 120, Rolle.MITARBEITER, Rolle.PROJEKTLEITUNG, Rolle.ADMIN),
            eintrag("Anträge prüfen", "antraege-pruefen", 320, Rolle.PROJEKTLEITUNG, Rolle.ADMIN),
            eintrag("Start", "", 100, Rolle.values()));

    @Test
    void mitarbeiterSiehtNurEintraegeSeinerRolle() {
        assertThat(bezeichnungen(new SeitenMenue(Rolle.MITARBEITER, eintraege)))
                .containsExactly("Start", "Meine Zeiten", "Projekte");
    }

    @Test
    void projektleitungSiehtAuchDieAntraegeAberNichtDieMitarbeiterverwaltung() {
        assertThat(bezeichnungen(new SeitenMenue(Rolle.PROJEKTLEITUNG, eintraege)))
                .containsExactly("Start", "Meine Zeiten", "Projekte", "Anträge prüfen");
    }

    @Test
    void adminSiehtAlleEintraegeNachReihenfolgeSortiert() {
        assertThat(bezeichnungen(new SeitenMenue(Rolle.ADMIN, eintraege)))
                .containsExactly("Start", "Meine Zeiten", "Projekte", "Mitarbeiter", "Anträge prüfen");
    }

    @Test
    void gruppenerscheinenNurWennSieEintraegeHaben() {
        assertThat(gruppen(new SeitenMenue(Rolle.MITARBEITER, eintraege))).containsExactly("Zeit", "Betrieb");
        assertThat(gruppen(new SeitenMenue(Rolle.ADMIN, eintraege))).containsExactly("Zeit", "Betrieb", "Verwaltung");
    }

    @Test
    void gruppeErgibtSichAusDerReihenfolge() {
        assertThat(SeitenMenue.gruppenname(100)).isEqualTo("Zeit");
        assertThat(SeitenMenue.gruppenname(199)).isEqualTo("Zeit");
        assertThat(SeitenMenue.gruppenname(200)).isEqualTo("Betrieb");
        assertThat(SeitenMenue.gruppenname(299)).isEqualTo("Betrieb");
        assertThat(SeitenMenue.gruppenname(300)).isEqualTo("Verwaltung");
    }

    @Test
    void rollenAnzeigeTextIstLesbar() {
        assertThat(Rollenanzeige.text(Rolle.MITARBEITER)).isEqualTo("Mitarbeiter");
        assertThat(Rollenanzeige.text(Rolle.PROJEKTLEITUNG)).isEqualTo("Projektleitung");
        assertThat(Rollenanzeige.text(Rolle.ADMIN)).isEqualTo("Admin");
    }

    @Test
    void ohneEintraegeBleibtDasMenueLeer() {
        assertThat(bezeichnungen(new SeitenMenue(Rolle.ADMIN, List.of()))).isEmpty();
    }

    private static List<String> bezeichnungen(SeitenMenue menue) {
        return menue.getChildren()
                .filter(SideNav.class::isInstance)
                .map(SideNav.class::cast)
                .flatMap(navigation -> navigation.getItems().stream())
                .map(SideNavItem::getLabel)
                .toList();
    }

    private static List<String> gruppen(SeitenMenue menue) {
        return menue.getChildren()
                .filter(Span.class::isInstance)
                .map(komponente -> komponente.getElement().getText())
                .toList();
    }

    private static MenuEintrag eintrag(String bezeichnung, String route, int reihenfolge, Rolle... rollen) {
        return new MenuEintrag() {
            @Override
            public String bezeichnung() {
                return bezeichnung;
            }

            @Override
            public String route() {
                return route;
            }

            @Override
            public Set<Rolle> erlaubteRollen() {
                return Set.of(rollen);
            }

            @Override
            public int reihenfolge() {
                return reihenfolge;
            }
        };
    }
}
