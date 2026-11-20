package de.timecraft.material.service;

import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import java.math.BigDecimal;
import java.time.Instant;

/** Offener Mehrbedarf fuer die Projektleitung: Alt-/Neuwert und Kostendifferenz (EK) samt Begruendung (FA-M4-05). */
public record AntragZurPruefung(long antragId, long antragstellerId, String bezeichnung, Einheit einheit,
        BigDecimal alteMenge, BigDecimal zusatzmenge, BigDecimal neueMenge, BigDecimal kostendifferenz,
        String begruendung, Instant eingereichtAm) {

    static AntragZurPruefung aus(Materialanpassungsantrag antrag) {
        Materialposition position = antrag.getMaterialposition();
        BigDecimal zusatz = antrag.getZusatzmenge();
        return new AntragZurPruefung(antrag.getId(), antrag.getAntragstellerId(),
                position.getMaterial().getBezeichnung(), position.getMaterial().getEinheit(),
                position.getSollMenge(), zusatz, position.getSollMenge().add(zusatz),
                Betraege.runden(zusatz.multiply(position.getMaterial().getEinkaufspreis())),
                antrag.getBegruendung(), antrag.getEingereichtAm());
    }
}
