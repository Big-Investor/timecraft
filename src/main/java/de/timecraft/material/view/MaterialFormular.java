package de.timecraft.material.view;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Material;
import de.timecraft.material.service.MaterialDaten;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.function.Predicate;

/**
 * Dialog zum Anlegen und Bearbeiten eines Materials. Sammelt nur die Eingaben;
 * pruefen und speichern macht der MaterialService (ueber den Rueckruf der View, true = gespeichert).
 *
 * @author Tharsan Arumuganathar
 */
class MaterialFormular extends Dialog {

    private final TextField artikelnummer = new TextField("Artikelnummer");
    private final TextField bezeichnung = new TextField("Bezeichnung");
    private final TextField kategorie = new TextField("Kategorie");
    private final Select<Einheit> einheit = new Select<>();
    private final BigDecimalField einkaufspreis = new BigDecimalField("Einkaufspreis (EK) in €");
    private final BigDecimalField verkaufspreis = new BigDecimalField("Verkaufspreis (VK) in €");

    /**
     * @param material zu bearbeitendes Material, null fuer ein neues
     * @param speichern Rueckruf zum Speichern, true = gespeichert (Dialog schliesst)
     */
    MaterialFormular(Material material, Predicate<MaterialDaten> speichern) {
        setHeaderTitle(material == null ? "Neues Material" : "Material bearbeiten");
        artikelnummer.setMaxLength(20);
        artikelnummer.setRequiredIndicatorVisible(true);
        bezeichnung.setMaxLength(100);
        bezeichnung.setRequiredIndicatorVisible(true);
        kategorie.setMaxLength(50);
        einheit.setLabel("Einheit");
        einheit.setItems(Einheit.values());
        einheit.setItemLabelGenerator(Einheit::kuerzel);
        einheit.setRequiredIndicatorVisible(true);
        einkaufspreis.setRequiredIndicatorVisible(true);
        verkaufspreis.setRequiredIndicatorVisible(true);
        if (material != null) {
            artikelnummer.setValue(material.getArtikelnummer());
            bezeichnung.setValue(material.getBezeichnung());
            kategorie.setValue(material.getKategorie() == null ? "" : material.getKategorie());
            einheit.setValue(material.getEinheit());
            einkaufspreis.setValue(material.getEinkaufspreis());
            verkaufspreis.setValue(material.getVerkaufspreis());
        }
        FormLayout felder = new FormLayout(artikelnummer, bezeichnung, kategorie, einheit, einkaufspreis,
                verkaufspreis);
        add(felder);

        Button speichernKnopf = new Button("Speichern", ereignis -> {
            if (speichern.test(eingaben())) {
                close();
            }
        });
        speichernKnopf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button abbrechen = new Button("Abbrechen", ereignis -> close());
        getFooter().add(abbrechen, speichernKnopf);
    }

    private MaterialDaten eingaben() {
        return new MaterialDaten(artikelnummer.getValue(), bezeichnung.getValue(), kategorie.getValue(),
                einheit.getValue(), einkaufspreis.getValue(), verkaufspreis.getValue());
    }
}
