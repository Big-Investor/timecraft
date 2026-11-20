package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.material.api.MaterialAuskunft;
import de.timecraft.material.api.MaterialInfo;
import de.timecraft.material.model.Material;
import de.timecraft.material.repository.MaterialRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialstamm (FA-M4-01). Pflegen darf nur der Admin, Preise sehen Projektleitung und Admin.
 * Materialien werden nie geloescht, nur deaktiviert.
 */
@Service
public class MaterialService implements MaterialAuskunft {

    private final MaterialRepository repository;

    public MaterialService(MaterialRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Material anlegen(Rolle rolle, MaterialDaten daten) {
        Rechte.pruefeStammpflege(rolle);
        MaterialDaten geprueft = pruefe(daten);
        if (repository.existsByArtikelnummer(geprueft.artikelnummer())) {
            throw artikelnummerVergeben(geprueft.artikelnummer());
        }
        return repository.save(new Material(geprueft.artikelnummer(), geprueft.bezeichnung(), geprueft.kategorie(),
                geprueft.einheit(), geprueft.einkaufspreis(), geprueft.verkaufspreis()));
    }

    @Transactional
    public Material bearbeiten(Rolle rolle, long materialId, MaterialDaten daten) {
        Rechte.pruefeStammpflege(rolle);
        Material material = finde(materialId);
        MaterialDaten geprueft = pruefe(daten);
        if (repository.existsByArtikelnummerAndIdNot(geprueft.artikelnummer(), materialId)) {
            throw artikelnummerVergeben(geprueft.artikelnummer());
        }
        material.aendern(geprueft.artikelnummer(), geprueft.bezeichnung(), geprueft.kategorie(),
                geprueft.einheit(), geprueft.einkaufspreis(), geprueft.verkaufspreis());
        return material;
    }

    @Transactional
    public void deaktivieren(Rolle rolle, long materialId) {
        Rechte.pruefeStammpflege(rolle);
        finde(materialId).deaktivieren();
    }

    @Transactional
    public void aktivieren(Rolle rolle, long materialId) {
        Rechte.pruefeStammpflege(rolle);
        finde(materialId).aktivieren();
    }

    /** Ganzer Stamm mit Preisen, auch deaktivierte Materialien. */
    @Transactional(readOnly = true)
    public List<Material> alle(Rolle rolle) {
        Rechte.pruefePreissicht(rolle);
        return repository.findAllByOrderByArtikelnummerAsc();
    }

    /** Katalog fuer die Materialplanung: nur aktive Materialien, mit Preisen. */
    @Transactional(readOnly = true)
    public List<Material> aktive(Rolle rolle) {
        Rechte.pruefePreissicht(rolle);
        return repository.findByAktivTrueOrderByArtikelnummerAsc();
    }

    /** Schnittstelle fuer andere Module (Kann-Ziel K2), ohne Preise und ohne Rechtepruefung. */
    @Override
    @Transactional(readOnly = true)
    public List<MaterialInfo> aktiveMaterialien() {
        return repository.findByAktivTrueOrderByArtikelnummerAsc().stream()
                .map(m -> new MaterialInfo(m.getId(), m.getBezeichnung(), m.getEinheit().kuerzel()))
                .toList();
    }

    private Material finde(long materialId) {
        return repository.findById(materialId)
                .orElseThrow(() -> new NichtGefundenException("Das Material wurde nicht gefunden."));
    }

    private static FachlicheException artikelnummerVergeben(String artikelnummer) {
        return new FachlicheException("Die Artikelnummer " + artikelnummer + " ist bereits vergeben.");
    }

    private static MaterialDaten pruefe(MaterialDaten daten) {
        if (daten == null) {
            throw new FachlicheException("Bitte füllen Sie das Formular aus.");
        }
        String artikelnummer = pflichttext(daten.artikelnummer(), "die Artikelnummer", 20);
        String bezeichnung = pflichttext(daten.bezeichnung(), "die Bezeichnung", 100);
        String kategorie = kategorie(daten.kategorie());
        if (daten.einheit() == null) {
            throw new FachlicheException("Bitte wählen Sie eine Einheit.");
        }
        BigDecimal einkaufspreis = preis(daten.einkaufspreis(), "den Einkaufspreis");
        BigDecimal verkaufspreis = preis(daten.verkaufspreis(), "den Verkaufspreis");
        return new MaterialDaten(artikelnummer, bezeichnung, kategorie, daten.einheit(), einkaufspreis, verkaufspreis);
    }

    private static String pflichttext(String text, String feld, int maxLaenge) {
        if (text == null || text.isBlank()) {
            throw new FachlicheException("Bitte geben Sie " + feld + " ein.");
        }
        String bereinigt = text.strip();
        if (bereinigt.length() > maxLaenge) {
            throw new FachlicheException("Bitte kürzen Sie " + feld + " auf höchstens " + maxLaenge + " Zeichen.");
        }
        return bereinigt;
    }

    private static String kategorie(String kategorie) {
        if (kategorie == null || kategorie.isBlank()) {
            return null;
        }
        String bereinigt = kategorie.strip();
        if (bereinigt.length() > 50) {
            throw new FachlicheException("Bitte kürzen Sie die Kategorie auf höchstens 50 Zeichen.");
        }
        return bereinigt;
    }

    private static BigDecimal preis(BigDecimal preis, String feld) {
        if (preis == null) {
            throw new FachlicheException("Bitte geben Sie " + feld + " ein.");
        }
        BigDecimal gerundet = Betraege.runden(preis);
        if (gerundet.signum() < 0) {
            throw new FachlicheException("Bitte geben Sie " + feld + " ohne Minuszeichen ein.");
        }
        if (gerundet.compareTo(Betraege.HOECHSTWERT) > 0) {
            throw new FachlicheException("Bitte geben Sie " + feld + " kleiner als 100.000.000 € ein.");
        }
        return gerundet;
    }
}
