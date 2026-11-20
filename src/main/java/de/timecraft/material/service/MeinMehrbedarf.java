package de.timecraft.material.service;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.material.model.Einheit;
import de.timecraft.material.model.Materialanpassungsantrag;
import java.math.BigDecimal;
import java.time.Instant;

/** Eigener Mehrbedarfsantrag fuer die Liste "Meine Mehrbedarfe", ohne Preise. */
public record MeinMehrbedarf(long antragId, long projektId, String bezeichnung, Einheit einheit,
        BigDecimal zusatzmenge, Instant eingereichtAm, Antragsstatus status, String begruendung,
        Long entscheiderId, String entscheidungshinweis) {

    static MeinMehrbedarf aus(Materialanpassungsantrag antrag) {
        return new MeinMehrbedarf(antrag.getId(), antrag.getMaterialposition().getProjektId(),
                antrag.getMaterialposition().getMaterial().getBezeichnung(),
                antrag.getMaterialposition().getMaterial().getEinheit(), antrag.getZusatzmenge(),
                antrag.getEingereichtAm(), antrag.getStatus(), antrag.getBegruendung(),
                antrag.getEntscheiderId(), antrag.getEntscheidungshinweis());
    }
}
