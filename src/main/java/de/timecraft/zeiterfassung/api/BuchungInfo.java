// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

/** ende == null: Buchung läuft noch (nettoMinuten dann 0). */
public record BuchungInfo(
        long zeiteintragId, long mitarbeiterId, Long projektId, Standort standort,
        Instant beginn, Instant ende, int pausenMinuten, int nettoMinuten, boolean geaendert) {

    public boolean laeuft() { return ende == null; }
}
