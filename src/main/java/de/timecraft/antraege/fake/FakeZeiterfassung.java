package de.timecraft.antraege.fake;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Aufzeichnender Ersatz fuer M2 (nur Tests und Profil "fake"): Buchungen und Abwesenheiten im
 * Speicher. Im Fehlermodus werfen alle schreibenden Methoden eine Fachausnahme, ohne etwas zu
 * aendern; so laesst sich der Rollback in M5 pruefen. Urlaubstage: Montag bis Freitag ohne
 * feste Feiertage (Neujahr, 1. Mai, 3. Oktober, Weihnachten).
 */
public class FakeZeiterfassung implements BuchungsAuskunft, AbwesenheitsUebernahme, BuchungsAenderung {

    private static final Set<MonthDay> FEIERTAGE = Set.of(MonthDay.of(1, 1), MonthDay.of(5, 1),
            MonthDay.of(10, 3), MonthDay.of(12, 25), MonthDay.of(12, 26));

    /** Eine genehmigte Abwesenheit in M2. */
    public record Abwesenheit(long abwesenheitId, long antragId, long mitarbeiterId, AbwesenheitsArt art,
            LocalDate von, LocalDate bis) {
    }

    private final Map<Long, BuchungInfo> buchungen = new LinkedHashMap<>();
    private final Map<Long, Abwesenheit> abwesenheiten = new LinkedHashMap<>();
    private final List<String> aufrufe = new ArrayList<>();
    private long naechsteId = 1;
    private boolean fehlermodus;

    public void buchungHinzufuegen(BuchungInfo buchung) {
        buchungen.put(buchung.zeiteintragId(), buchung);
    }

    /** Legt eine Abwesenheit an, als waere sie frueher genehmigt worden. */
    public long abwesenheitHinzufuegen(long antragId, long mitarbeiterId, AbwesenheitsArt art, LocalDate von,
            LocalDate bis) {
        long id = naechsteId++;
        abwesenheiten.put(id, new Abwesenheit(id, antragId, mitarbeiterId, art, von, bis));
        return id;
    }

    public void setzeFehlermodus(boolean fehlermodus) {
        this.fehlermodus = fehlermodus;
    }

    /** Schreibende Aufrufe in der Reihenfolge, z. B. "uebernehmen 5". */
    public List<String> aufrufe() {
        return List.copyOf(aufrufe);
    }

    public List<Abwesenheit> abwesenheiten() {
        return List.copyOf(abwesenheiten.values());
    }

    // ---------- BuchungsAuskunft ----------

    @Override
    public Optional<BuchungInfo> buchung(long zeiteintragId) {
        return Optional.ofNullable(buchungen.get(zeiteintragId));
    }

    @Override
    public Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId) {
        return buchungen.values().stream()
                .filter(buchung -> buchung.mitarbeiterId() == mitarbeiterId && buchung.laeuft()).findFirst();
    }

    @Override
    public List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId) {
        return buchungen.values().stream()
                .filter(buchung -> buchung.laeuft() && Long.valueOf(projektId).equals(buchung.projektId())).toList();
    }

    @Override
    public List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis) {
        return buchungen.values().stream()
                .filter(buchung -> buchung.mitarbeiterId() == mitarbeiterId)
                .filter(buchung -> buchung.beginn().isBefore(bis)
                        && (buchung.laeuft() || buchung.ende().isAfter(von)))
                .toList();
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

    // ---------- AbwesenheitsUebernahme ----------

    @Override
    public int urlaubstage(long mitarbeiterId, LocalDate von, LocalDate bis) {
        int tage = 0;
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            boolean wochenende = tag.getDayOfWeek() == DayOfWeek.SATURDAY || tag.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!wochenende && !FEIERTAGE.contains(MonthDay.from(tag))) {
                tage++;
            }
        }
        return tage;
    }

    @Override
    public boolean ueberschneidetAbwesenheit(long mitarbeiterId, LocalDate von, LocalDate bis) {
        return abwesenheiten.values().stream().anyMatch(abwesenheit -> abwesenheit.mitarbeiterId() == mitarbeiterId
                && !abwesenheit.von().isAfter(bis) && !abwesenheit.bis().isBefore(von));
    }

    @Override
    public long uebernehmen(long antragId, long mitarbeiterId, AbwesenheitsArt art, LocalDate von, LocalDate bis) {
        pruefeFehlermodus();
        long id = abwesenheitHinzufuegen(antragId, mitarbeiterId, art, von, bis);
        aufrufe.add("uebernehmen " + antragId);
        return id;
    }

    @Override
    public void aufheben(long abwesenheitId) {
        pruefeFehlermodus();
        abwesenheiten.remove(abwesenheitId);
        aufrufe.add("aufheben " + abwesenheitId);
    }

    @Override
    public Optional<Long> abwesenheitZuAntrag(long antragId) {
        return abwesenheiten.values().stream().filter(abwesenheit -> abwesenheit.antragId() == antragId)
                .map(Abwesenheit::abwesenheitId).findFirst();
    }

    // ---------- BuchungsAenderung ----------

    @Override
    public void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) {
        pruefeFehlermodus();
        BuchungInfo alt = buchung(zeiteintragId)
                .orElseThrow(() -> new NichtGefundenException("Die Buchung wurde nicht gefunden."));
        int netto = (int) Duration.between(beginn, ende).toMinutes() - alt.pausenMinuten();
        buchungen.put(zeiteintragId, new BuchungInfo(zeiteintragId, alt.mitarbeiterId(), projektId, standort,
                beginn, ende, alt.pausenMinuten(), netto, true));
        aufrufe.add("aendern " + zeiteintragId);
    }

    @Override
    public long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
            long geaendertVonMitarbeiterId, String begruendung) {
        pruefeFehlermodus();
        long id = naechsteId++;
        int netto = (int) Duration.between(beginn, ende).toMinutes();
        buchungHinzufuegen(new BuchungInfo(id, mitarbeiterId, projektId, standort, beginn, ende, 0, netto, true));
        aufrufe.add("nachtragen " + id);
        return id;
    }

    private void pruefeFehlermodus() {
        if (fehlermodus) {
            throw new FachlicheException("Die Daten konnten in der Zeiterfassung nicht gespeichert werden.");
        }
    }
}
