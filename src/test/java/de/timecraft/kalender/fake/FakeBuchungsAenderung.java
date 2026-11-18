package de.timecraft.kalender.fake;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake fuer die BuchungsAenderung von M2: zeichnet jeden erfolgreichen Aufruf auf. Im Modus
 * „wirft Fehler“ meldet er wie M2 eine Fachausnahme (z. B. Ueberschneidung) und speichert nichts.
 */
public class FakeBuchungsAenderung implements BuchungsAenderung {

    /** Ein gespeicherter Aufruf von aendern. */
    public record Aufruf(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) { }

    private final List<Aufruf> aufrufe = new ArrayList<>();
    private String fehlermeldung;

    /** Ab jetzt wirft aendern eine Fachausnahme mit dieser Meldung. */
    public void wirftFehler(String meldung) {
        this.fehlermeldung = meldung;
    }

    public List<Aufruf> aufrufe() {
        return List.copyOf(aufrufe);
    }

    @Override
    public void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) {
        if (fehlermeldung != null) {
            throw new FachlicheException(fehlermeldung);
        }
        aufrufe.add(new Aufruf(zeiteintragId, beginn, ende, standort, projektId, art, geaendertVonMitarbeiterId,
                begruendung));
    }

    /** Nachtragen (K2) ist zurueckgestellt; der Kalender ruft es nicht auf. */
    @Override
    public long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
            long geaendertVonMitarbeiterId, String begruendung) {
        throw new UnsupportedOperationException("Buchung nachtragen (K2) ist zurückgestellt.");
    }
}
