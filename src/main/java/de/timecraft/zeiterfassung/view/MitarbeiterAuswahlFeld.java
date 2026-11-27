package de.timecraft.zeiterfassung.view;

import java.util.List;

import com.vaadin.flow.component.select.Select;

import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.Meldungen;
import de.timecraft.zeiterfassung.model.MitarbeiterEintrag;
import de.timecraft.zeiterfassung.service.MitarbeiterAuswahlService;

/**
 * Auswahlfeld "Mitarbeiter". Sichtbar nur, wenn es ausser der eigenen Person jemanden zu waehlen gibt.
 *
 * @author Noah Cipa
 */
class MitarbeiterAuswahlFeld extends Select<MitarbeiterEintrag> {

    MitarbeiterAuswahlFeld() {
        setLabel("Mitarbeiter");
        setItemLabelGenerator(eintrag -> eintrag == null ? "" : eintrag.name());
        setVisible(false);
    }

    /** Laedt die lesbaren Mitarbeiter, vorausgewaehlt ist die angemeldete Person. */
    void laden(MitarbeiterAuswahlService auswahlService, AktuelleSitzung sitzung) {
        try {
            List<MitarbeiterEintrag> eintraege = auswahlService.lesbareMitarbeiter(sitzung.mitarbeiterId(),
                    sitzung.rolle());
            setItems(eintraege);
            eintraege.stream().filter(eintrag -> eintrag.mitarbeiterId() == sitzung.mitarbeiterId()).findFirst()
                    .ifPresent(this::setValue);
            setVisible(eintraege.size() > 1);
        } catch (RuntimeException fehler) {
            Meldungen.fehler(fehler);
        }
    }

    long gewaehlteId(long angemeldeteId) {
        return getValue() == null ? angemeldeteId : getValue().mitarbeiterId();
    }
}
