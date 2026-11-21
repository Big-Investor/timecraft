package de.timecraft.projekte.fake;

import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ersatz für die BuchungsAuskunft von Modul M2, nur im Profil "fake". Es laufen zwei erfundene Buchungen
 * auf das Projekt 1 (das erste Demoprojekt aus {@link FakeDemoDaten}): Jonas (ID 1) unterwegs seit 07:02
 * und Mehmet (ID 4) unterwegs seit 06:58, jeweils heute. Alles andere ist leer.
 */
@Component
@Profile("fake")
public class FakeBuchungsAuskunft implements BuchungsAuskunft {

    private static final long DEMOPROJEKT_ID = 1L;

    private final List<BuchungInfo> laufendeBuchungen;

    public FakeBuchungsAuskunft(Clock clock) {
        LocalDate heute = LocalDate.now(clock);
        Instant jonasBeginn = heute.atTime(7, 2).atZone(clock.getZone()).toInstant();
        Instant mehmetBeginn = heute.atTime(6, 58).atZone(clock.getZone()).toInstant();
        laufendeBuchungen = List.of(
                new BuchungInfo(1, 1, DEMOPROJEKT_ID, Standort.UNTERWEGS, jonasBeginn, null, 0, 0, false),
                new BuchungInfo(2, 4, DEMOPROJEKT_ID, Standort.UNTERWEGS, mehmetBeginn, null, 0, 0, false));
    }

    @Override
    public Optional<BuchungInfo> buchung(long zeiteintragId) {
        return laufendeBuchungen.stream().filter(buchung -> buchung.zeiteintragId() == zeiteintragId).findFirst();
    }

    @Override
    public Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId) {
        return laufendeBuchungen.stream().filter(buchung -> buchung.mitarbeiterId() == mitarbeiterId).findFirst();
    }

    @Override
    public List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId) {
        return laufendeBuchungen.stream()
                .filter(buchung -> buchung.projektId() != null && buchung.projektId() == projektId)
                .toList();
    }

    @Override
    public List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis) {
        return List.of();
    }

    @Override
    public List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat) {
        return List.of();
    }

    @Override
    public List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat) {
        return List.of();
    }

    @Override
    public List<AenderungInfo> historie(long zeiteintragId) {
        return List.of();
    }
}
