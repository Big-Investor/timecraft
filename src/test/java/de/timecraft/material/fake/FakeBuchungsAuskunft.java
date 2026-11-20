package de.timecraft.material.fake;

import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test-Fake fuer die BuchungsAuskunft von M2. M4 braucht nur {@code segmenteProjekt};
 * die uebrigen Methoden liefern leere Ergebnisse wie bei einem Mitarbeiter ohne Buchungen.
 */
public class FakeBuchungsAuskunft implements BuchungsAuskunft {

    private final List<ZeitSegment> segmente = new ArrayList<>();
    private long naechsteId = 1;

    /** Abgeschlossene Buchung an einem Tag mit anrechenbaren Minuten (Pause schon abgezogen). */
    public void segment(long mitarbeiterId, long projektId, LocalDate tag, int anrechenbareMinuten) {
        Instant beginn = tag.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        segmente.add(new ZeitSegment(naechsteId++, mitarbeiterId, projektId, Standort.UNTERWEGS, tag, beginn,
                beginn.plusSeconds(anrechenbareMinuten * 60L), anrechenbareMinuten));
    }

    @Override
    public List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat) {
        return segmente.stream()
                .filter(s -> s.projektId() != null && s.projektId() == projektId)
                .filter(s -> YearMonth.from(s.tag()).equals(monat))
                .toList();
    }

    @Override
    public List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat) {
        return segmente.stream()
                .filter(s -> s.mitarbeiterId() == mitarbeiterId)
                .filter(s -> YearMonth.from(s.tag()).equals(monat))
                .toList();
    }

    @Override
    public Optional<BuchungInfo> buchung(long zeiteintragId) {
        return Optional.empty();
    }

    @Override
    public Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId) {
        return Optional.empty();
    }

    @Override
    public List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId) {
        return List.of();
    }

    @Override
    public List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis) {
        return List.of();
    }

    @Override
    public List<AenderungInfo> historie(long zeiteintragId) {
        return List.of();
    }
}
