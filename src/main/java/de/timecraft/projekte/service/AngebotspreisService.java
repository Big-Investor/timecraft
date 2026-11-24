package de.timecraft.projekte.service;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.projekte.api.AngebotspreisPflege;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.repository.ProjektRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die schreibende Schnittstelle von M3 für M4: Sie setzt nur die Spalte projekt.angebotspreis.
 * <p>
 * Diese Klasse prüft bewusst keine Rechte (so steht es im Vertrag): M4 stellt vorher sicher, dass die
 * angemeldete Person die verantwortliche Projektleitung des aktiven Projekts ist. Geprüft wird nur, was die
 * Datenbank braucht, damit nie ein technischer Fehler entsteht: Das Projekt muss es geben, und der Preis
 * muss in die Spalte NUMERIC(10,2) passen.
 *
 * @author Tim Stukenbröker
 */
@Service
public class AngebotspreisService implements AngebotspreisPflege {

    /** NUMERIC(10,2): 10 Stellen insgesamt, davon 2 Nachkommastellen, also 8 Vorkommastellen. */
    private static final int MAXIMALE_VORKOMMASTELLEN = 8;

    private final ProjektRepository repository;

    public AngebotspreisService(ProjektRepository repository) {
        this.repository = repository;
    }

    /**
     * Setzt den Angebotspreis des Projekts. Mehr als zwei Nachkommastellen werden kaufmännisch auf
     * Cent gerundet. Ein Preis null entfernt den Angebotspreis. Bei einem Fehler wird nichts gespeichert.
     */
    @Override
    @Transactional
    public void setzeAngebotspreis(long projektId, BigDecimal angebotspreis) {
        Projekt projekt = repository.findById(projektId)
                .orElseThrow(() -> new NichtGefundenException("Das Projekt wurde nicht gefunden."));

        projekt.setAngebotspreis(pruefeUndRunde(angebotspreis));
        repository.save(projekt);
    }

    /** Liefert den Preis auf zwei Nachkommastellen gerundet, oder wirft eine Meldung, wenn er nicht passt. */
    private BigDecimal pruefeUndRunde(BigDecimal angebotspreis) {
        if (angebotspreis == null) {
            return null;
        }
        if (angebotspreis.signum() < 0) {
            throw new FachlicheException("Der Angebotspreis darf nicht negativ sein.");
        }
        // Erst runden, dann die Größe prüfen: 99999999.995 wird beim Runden zu 100000000.00 und passt nicht mehr.
        BigDecimal gerundet = angebotspreis.setScale(2, RoundingMode.HALF_UP);
        if (gerundet.precision() - gerundet.scale() > MAXIMALE_VORKOMMASTELLEN) {
            throw new FachlicheException("Der Angebotspreis ist zu groß (höchstens 99.999.999,99 Euro).");
        }
        return gerundet;
    }
}
