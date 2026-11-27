package de.timecraft.projekte.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.TimeCraftBenutzer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Testhilfe für Views ohne Browser: sucht Oberflächenelemente im Komponentenbaum und meldet Personen an.
 *
 * @author Tim Stukenbröker
 */
final class Komponenten {

    private Komponenten() {
    }

    /** Alle Komponenten dieses Typs unterhalb der Wurzel, in der Reihenfolge der Anzeige. */
    static <T extends Component> List<T> alle(Component wurzel, Class<T> typ) {
        List<T> treffer = new ArrayList<>();
        sammle(wurzel, typ, treffer);
        return treffer;
    }

    /** Wie oben, für einen Dialog: durchsucht auch die Fußzeile mit den Schaltflächen. */
    static <T extends Component> List<T> alle(Dialog dialog, Class<T> typ) {
        List<T> treffer = new ArrayList<>(alle((Component) dialog, typ));
        dialog.getFooter().getElement().getChildren()
                .forEach(element -> element.getComponent().ifPresent(kind -> sammle(kind, typ, treffer)));
        return treffer.stream().distinct().toList();
    }

    /** Die Komponente mit dieser Beschriftung (z. B. das Feld "Projektname"). */
    static <T extends Component & HasLabel> T mitBeschriftung(Component wurzel, Class<T> typ, String beschriftung) {
        return alle(wurzel, typ).stream()
                .filter(feld -> beschriftung.equals(feld.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein " + typ.getSimpleName() + " mit Beschriftung " + beschriftung));
    }

    /** Die Schaltfläche mit diesem Text; bei einem Dialog auch aus der Fußzeile. */
    static Button knopf(Component wurzel, String text) {
        List<Button> knoepfe = wurzel instanceof Dialog dialog ? alle(dialog, Button.class) : alle(wurzel, Button.class);
        return knoepfe.stream().filter(knopf -> text.equals(knopf.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("Kein Knopf " + text));
    }

    /** Meldet für den Test eine Person an. Aufräumen mit SecurityContextHolder.clearContext(). */
    static void anmelden(long mitarbeiterId, Rolle rolle) {
        TimeCraftBenutzer benutzer = new TimeCraftBenutzer(mitarbeiterId, "benutzer", "hash", rolle);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(benutzer, null, benutzer.getAuthorities()));
    }

    private static <T extends Component> void sammle(Component komponente, Class<T> typ, List<T> treffer) {
        if (typ.isInstance(komponente)) {
            treffer.add(typ.cast(komponente));
        }
        komponente.getChildren().forEach(kind -> sammle(kind, typ, treffer));
    }
}
