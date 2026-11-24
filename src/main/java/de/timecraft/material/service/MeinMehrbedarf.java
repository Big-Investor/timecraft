package de.timecraft.material.service;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Materialanpassungsantrag;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Eigener Mehrbedarfsantrag fuer die Liste "Meine Mehrbedarfe", ohne Preise.
 *
 * @param antragId Antrag
 * @param projekt Projektname
 * @param bezeichnung Material
 * @param einheit Einheit
 * @param zusatzmenge beantragte Menge
 * @param eingereichtAm Zeitpunkt des Antrags
 * @param status Status
 * @param begruendung Begruendung
 * @param entscheider Name der entscheidenden Person, leer wenn offen
 * @param entscheidungshinweis Kommentar der Entscheidung
 *
 * @author Tharsan Arumuganathar
 */
public record MeinMehrbedarf(long antragId, String projekt, String bezeichnung, Einheit einheit,
        BigDecimal zusatzmenge, Instant eingereichtAm, Antragsstatus status, String begruendung,
        String entscheider, String entscheidungshinweis) {

    /**
     * @param antrag eigener Antrag
     * @param projekt Projektname
     * @param entscheider Name der entscheidenden Person, leer wenn noch offen
     * @return Anzeige ohne Preise
     */
    static MeinMehrbedarf aus(Materialanpassungsantrag antrag, String projekt, String entscheider) {
        return new MeinMehrbedarf(antrag.getId(), projekt,
                antrag.getMaterialposition().getMaterial().getBezeichnung(),
                antrag.getMaterialposition().getMaterial().getEinheit(), antrag.getZusatzmenge(),
                antrag.getEingereichtAm(), antrag.getStatus(), antrag.getBegruendung(),
                entscheider, antrag.getEntscheidungshinweis());
    }
}
