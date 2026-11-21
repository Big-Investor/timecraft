package de.timecraft.projekte.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import java.util.ArrayList;
import java.util.List;

/** Testhilfe: sucht Oberflächenelemente im Komponentenbaum einer View, ohne Browser. */
final class Komponenten {

    private Komponenten() {
    }

    /** Alle Komponenten dieses Typs unterhalb der Wurzel, in der Reihenfolge der Anzeige. */
    static <T extends Component> List<T> alle(Component wurzel, Class<T> typ) {
        List<T> treffer = new ArrayList<>();
        sammle(wurzel, typ, treffer);
        return treffer;
    }

    /** Die Komponente mit dieser Beschriftung (z. B. das Feld "Projektname"). */
    static <T extends Component & HasLabel> T mitBeschriftung(Component wurzel, Class<T> typ, String beschriftung) {
        return alle(wurzel, typ).stream()
                .filter(feld -> beschriftung.equals(feld.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein " + typ.getSimpleName() + " mit Beschriftung " + beschriftung));
    }

    private static <T extends Component> void sammle(Component komponente, Class<T> typ, List<T> treffer) {
        if (typ.isInstance(komponente)) {
            treffer.add(typ.cast(komponente));
        }
        komponente.getChildren().forEach(kind -> sammle(kind, typ, treffer));
    }
}
