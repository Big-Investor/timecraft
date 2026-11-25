package de.timecraft.kalender.fake;

import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fake fuer die BuchungsAuskunft von M2: Buchungen liegen in einer Liste im Speicher.
 * Segmente werden wie im Vertrag je Kalendertag (Europe/Berlin) zerlegt. Die Pause zieht
 * dieser Fake vom ersten Segment ab (reicht es nicht, vom naechsten); wie M2 die Pause
 * wirklich verteilt, ist Sache von M2 – der Kalender uebernimmt nur die gelieferten Minuten.
 *
 * @author Bennet Antonio Brinkmeier
 */
public class FakeBuchungsAuskunft implements BuchungsAuskunft {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final List<BuchungInfo> buchungen = new ArrayList<>();
    private final Map<Long, List<AenderungInfo>> historien = new HashMap<>();

    public void hinzufuegen(BuchungInfo buchung) {
        buchungen.add(buchung);
    }

    /** Ersetzt die Buchung mit derselben ID, z. B. wenn jemand anderes sie inzwischen geaendert hat. */
    public void ersetzen(BuchungInfo buchung) {
        buchungen.replaceAll(b -> b.zeiteintragId() == buchung.zeiteintragId() ? buchung : b);
    }

    public void historieHinzufuegen(long zeiteintragId, AenderungInfo aenderung) {
        historien.computeIfAbsent(zeiteintragId, id -> new ArrayList<>()).add(aenderung);
    }

    @Override
    public Optional<BuchungInfo> buchung(long zeiteintragId) {
        return buchungen.stream().filter(b -> b.zeiteintragId() == zeiteintragId).findFirst();
    }

    @Override
    public Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId) {
        return buchungen.stream().filter(b -> b.mitarbeiterId() == mitarbeiterId && b.laeuft()).findFirst();
    }

    @Override
    public List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId) {
        return buchungen.stream()
                .filter(b -> b.laeuft() && b.projektId() != null && b.projektId() == projektId)
                .toList();
    }

    @Override
    public List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis) {
        return buchungen.stream()
                .filter(b -> b.mitarbeiterId() == mitarbeiterId)
                .filter(b -> b.beginn().isBefore(bis))
                .filter(b -> b.laeuft() || b.ende().isAfter(von))
                .toList();
    }

    @Override
    public List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat) {
        List<ZeitSegment> ergebnis = new ArrayList<>();
        for (BuchungInfo buchung : buchungen) {
            if (buchung.mitarbeiterId() == mitarbeiterId && !buchung.laeuft()) {
                ergebnis.addAll(segmenteImMonat(buchung, monat));
            }
        }
        return ergebnis;
    }

    @Override
    public List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat) {
        List<ZeitSegment> ergebnis = new ArrayList<>();
        for (BuchungInfo buchung : buchungen) {
            if (!buchung.laeuft() && buchung.projektId() != null && buchung.projektId() == projektId) {
                ergebnis.addAll(segmenteImMonat(buchung, monat));
            }
        }
        return ergebnis;
    }

    @Override
    public List<AenderungInfo> historie(long zeiteintragId) {
        return List.copyOf(historien.getOrDefault(zeiteintragId, List.of()));
    }

    private List<ZeitSegment> segmenteImMonat(BuchungInfo buchung, YearMonth monat) {
        List<ZeitSegment> ergebnis = new ArrayList<>();
        int restPause = buchung.pausenMinuten();
        LocalDate tag = LocalDate.ofInstant(buchung.beginn(), BERLIN);
        Instant beginn = buchung.beginn();
        while (beginn.isBefore(buchung.ende())) {
            Instant tagesende = tag.plusDays(1).atStartOfDay(BERLIN).toInstant();
            Instant ende = tagesende.isBefore(buchung.ende()) ? tagesende : buchung.ende();
            int minuten = (int) Duration.between(beginn, ende).toMinutes();
            int abzug = Math.min(restPause, minuten);
            restPause -= abzug;
            if (YearMonth.from(tag).equals(monat)) {
                ergebnis.add(new ZeitSegment(buchung.zeiteintragId(), buchung.mitarbeiterId(), buchung.projektId(),
                        buchung.standort(), tag, beginn, ende, minuten - abzug));
            }
            beginn = ende;
            tag = tag.plusDays(1);
        }
        return ergebnis;
    }
}
