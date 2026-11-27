package de.timecraft.kalender.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.model.BuchungDetails;
import de.timecraft.kalender.model.HistorienEintrag;
import de.timecraft.kalender.model.KalenderEintrag;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.kalender.model.ProjektAuswahl;
import de.timecraft.kalender.model.WaehlbarePerson;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Liest den Kalender einer Person ueber die api von M2 (Buchungen), M3 (Projekte, Zuordnungen)
 * und M1 (Personen). Aendert nichts. Prueft, wer welchen Kalender sehen darf (FA-M6-07, 11).
 * Die Zeitzone kommt aus der Clock des Geruests (Europe/Berlin).
 *
 * @author Bennet Antonio Brinkmeier
 */
@Service
public class KalenderService {

    static final String OHNE_PROJEKT = "Ohne Projekt";
    static final String UNBEKANNTES_PROJEKT = "Unbekanntes Projekt";
    static final String KEINE_ANGABE = "–";
    static final String UNBEKANNTE_PERSON = "Unbekannte Person";
    private static final String NUR_EIGENER_KALENDER = "Sie dürfen nur Ihren eigenen Kalender sehen.";

    private final BuchungsAuskunft buchungsAuskunft;
    private final ProjektAuskunft projektAuskunft;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final Clock clock;
    private final KalenderRechte rechte;

    /**
     * "@Lazy": Spring holt BuchungsAuskunft (M2), ProjektAuskunft (M3) und MitarbeiterAuskunft (M1)
     * erst beim ersten Aufruf. So startet die Anwendung auch in diesem Branch, in dem es M1, M2 und M3
     * noch nicht gibt (gleiche Loesung wie in M3).
     */
    public KalenderService(@Lazy BuchungsAuskunft buchungsAuskunft, @Lazy ProjektAuskunft projektAuskunft,
            @Lazy MitarbeiterAuskunft mitarbeiterAuskunft, Clock clock) {
        this.buchungsAuskunft = buchungsAuskunft;
        this.projektAuskunft = projektAuskunft;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.clock = clock;
        this.rechte = new KalenderRechte(projektAuskunft, mitarbeiterAuskunft);
    }

    /** Heutiges Datum in der Zeitzone der Clock (Europe/Berlin). */
    public LocalDate heute() {
        return LocalDate.now(clock);
    }

    /** Erster Tag des Zeitraums, in dem der Bezugstag liegt. Eine Woche beginnt am Montag. */
    public LocalDate ersterTag(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag;
            case WOCHE -> bezugstag.minusDays(bezugstag.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            case MONAT -> bezugstag.withDayOfMonth(1);
        };
    }

    /** Letzter Tag des Zeitraums (einschliesslich). Eine Woche endet am Sonntag. */
    public LocalDate letzterTag(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag;
            case WOCHE -> ersterTag(ansicht, bezugstag).plusDays(6);
            case MONAT -> bezugstag.withDayOfMonth(bezugstag.lengthOfMonth());
        };
    }

    /** Bezugstag nach „Vor“ (schritte = 1) oder „Zurück“ (schritte = -1). */
    public LocalDate blaettern(Kalenderansicht ansicht, LocalDate bezugstag, int schritte) {
        return switch (ansicht) {
            case TAG -> bezugstag.plusDays(schritte);
            case WOCHE -> bezugstag.plusWeeks(schritte);
            case MONAT -> bezugstag.plusMonths(schritte);
        };
    }

    /**
     * Kalender der angemeldeten Person mit ihren eigenen Buchungen (FA-M6-01, 03, 04, 06, 11).
     * Jede angemeldete Rolle darf ihren eigenen Kalender sehen. Der Filter gilt auch fuer die Summen;
     * null wirkt wie KalenderFilter.KEINER.
     */
    public KalenderInhalt eigenerKalender(long mitarbeiterId, Rolle rolle, Kalenderansicht ansicht,
            LocalDate bezugstag, KalenderFilter filter) {
        KalenderRechte.pruefeAngemeldet(rolle);
        return kalender(mitarbeiterId, ansicht, bezugstag, filter, null);
    }

    /**
     * Personen fuer „Person wählen“ (FA-M6-07, UC-M6-04), sortiert nach Nachname und Vorname.
     * Projektleitung: aktive Mitarbeiter, die einem ihrer aktiven verantworteten Projekte zugeordnet sind.
     * Admin: alle Benutzer, auch ehemalige. Die angemeldete Person selbst fehlt („Mein Kalender“).
     * Eine leere Liste heisst: den Projekten ist niemand zugeordnet (UC-M6-04 2a).
     */
    public List<WaehlbarePerson> waehlbarePersonen(long mitarbeiterId, Rolle rolle) {
        KalenderRechte.pruefeAngemeldet(rolle);
        List<MitarbeiterInfo> personen = switch (rolle) {
            case MITARBEITER -> throw new ZugriffVerweigertException(NUR_EIGENER_KALENDER);
            case PROJEKTLEITUNG -> rechte.teamDerProjektleitung(mitarbeiterId);
            case ADMIN -> mitarbeiterAuskunft.alle();
        };
        return personen.stream()
                .filter(person -> person.mitarbeiterId() != mitarbeiterId)
                .sorted(Comparator.comparing(MitarbeiterInfo::nachname).thenComparing(MitarbeiterInfo::vorname))
                .map(person -> new WaehlbarePerson(person.mitarbeiterId(), person.name()))
                .toList();
    }

    /**
     * Kalender einer anderen Person (FA-M6-07, 11, QA-M6-04). Die Rechte gelten auch bei direktem Aufruf:
     * Ein Mitarbeiter darf keinen fremden Kalender sehen. Die Projektleitung nur von Personen aus
     * „Person wählen“ und nur deren Buchungen auf ihre aktiven Projekte, denen die Person zugeordnet ist.
     * Der Admin sieht alle Buchungen jeder vorhandenen Person.
     */
    public KalenderInhalt fremderKalender(long mitarbeiterId, Rolle rolle, long personId, Kalenderansicht ansicht,
            LocalDate bezugstag, KalenderFilter filter) {
        KalenderRechte.pruefeAngemeldet(rolle);
        if (rolle == Rolle.MITARBEITER) {
            throw new ZugriffVerweigertException(NUR_EIGENER_KALENDER);
        }
        if (personId == mitarbeiterId) {
            throw new FachlicheException("Ihren eigenen Kalender öffnen Sie über „Mein Kalender“.");
        }
        if (rolle == Rolle.PROJEKTLEITUNG) {
            return kalender(personId, ansicht, bezugstag, filter,
                    sichtbareProjekteFuerProjektleitung(mitarbeiterId, personId));
        }
        if (mitarbeiterAuskunft.finde(personId).isEmpty()) {
            throw new NichtGefundenException("Diese Person gibt es nicht.");
        }
        return kalender(personId, ansicht, bezugstag, filter, null);
    }

    /**
     * Detailfenster einer Buchung mit Projektleitung, Dauer und Aenderungshistorie (FA-M6-05, 10).
     * Sichtbar fuer jede Person die eigenen Buchungen, fuer die Projektleitung die Buchungen, die sie
     * im fremden Kalender sieht, fuer den Admin alle (FA-M6-07, 11, QA-M6-04), auch bei direktem Aufruf.
     */
    public BuchungDetails details(long mitarbeiterId, Rolle rolle, long zeiteintragId) {
        KalenderRechte.pruefeAngemeldet(rolle);
        Optional<BuchungInfo> gefunden = buchungsAuskunft.buchung(zeiteintragId);
        if (gefunden.isEmpty() && rolle == Rolle.ADMIN) {
            throw new NichtGefundenException("Diese Buchung gibt es nicht.");
        }
        BuchungInfo buchung = gefunden.filter(b -> darfSehen(mitarbeiterId, rolle, b))
                .orElseThrow(() -> new ZugriffVerweigertException("Diese Buchung dürfen Sie nicht sehen."));

        Optional<ProjektInfo> projekt = buchung.projektId() == null
                ? Optional.empty() : projektAuskunft.finde(buchung.projektId());
        String projektName = buchung.projektId() == null
                ? OHNE_PROJEKT : projekt.map(ProjektInfo::name).orElse(UNBEKANNTES_PROJEKT);
        String projektleitung = projekt.map(p -> personName(p.projektleitungId())).orElse(KEINE_ANGABE);
        int dauer = buchung.laeuft()
                ? (int) Duration.between(buchung.beginn(), clock.instant()).toMinutes()
                : buchung.nettoMinuten();
        return new BuchungDetails(buchung.zeiteintragId(), buchung.projektId(), projektName, projektleitung,
                buchung.standort(), ortszeit(buchung.beginn()), buchung.laeuft() ? null : ortszeit(buchung.ende()),
                dauer, buchung.laeuft(), buchung.geaendert(), historie(buchung.zeiteintragId()));
    }

    private boolean darfSehen(long mitarbeiterId, Rolle rolle, BuchungInfo buchung) {
        if (buchung.mitarbeiterId() == mitarbeiterId) {
            return true;
        }
        return switch (rolle) {
            case MITARBEITER -> false;
            case PROJEKTLEITUNG -> rechte.sichtbareProjekteFuerProjektleitung(mitarbeiterId, buchung.mitarbeiterId())
                    .contains(buchung.projektId());
            case ADMIN -> true;
        };
    }

    /** Aenderungshistorie von M2, aelteste Aenderung zuerst, mit dem Namen der aendernden Person. */
    private List<HistorienEintrag> historie(long zeiteintragId) {
        return buchungsAuskunft.historie(zeiteintragId).stream()
                .sorted(Comparator.comparing(AenderungInfo::zeitpunkt))
                .map(a -> new HistorienEintrag(ortszeit(a.zeitpunkt()), personName(a.geaendertVonMitarbeiterId()),
                        a.feld(), a.alterWert(), a.neuerWert(), a.begruendung()))
                .toList();
    }

    private String personName(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::name).orElse(UNBEKANNTE_PERSON);
    }

    /**
     * Projekte, deren Buchungen die Projektleitung im Kalender der Person sieht.
     * Ist die Person nicht (mehr) waehlbar, wird der Zugriff verweigert.
     */
    private Set<Long> sichtbareProjekteFuerProjektleitung(long projektleitungId, long personId) {
        Set<Long> projekte = rechte.sichtbareProjekteFuerProjektleitung(projektleitungId, personId);
        if (projekte.isEmpty()) {
            throw new ZugriffVerweigertException(
                    "Sie dürfen nur Kalender von Mitarbeitern Ihrer aktiven Projekte öffnen.");
        }
        return projekte;
    }

    /**
     * Eintraege und Summen eines Zeitraums. erlaubteProjekte == null heisst: alle Buchungen;
     * sonst nur Buchungen auf diese Projekte. Der Filter gilt zusaetzlich, auch fuer die Summen.
     */
    private KalenderInhalt kalender(long personId, Kalenderansicht ansicht, LocalDate bezugstag,
            KalenderFilter filter, Set<Long> erlaubteProjekte) {
        KalenderFilter wirksamerFilter = filter == null ? KalenderFilter.KEINER : filter;
        LocalDate ersterTag = ersterTag(ansicht, bezugstag);
        LocalDate letzterTag = letzterTag(ansicht, bezugstag);
        List<KalenderEintrag> sichtbar = eintraegeLaden(personId, ersterTag, letzterTag).stream()
                .filter(eintrag -> erlaubteProjekte == null || erlaubteProjekte.contains(eintrag.projektId()))
                .toList();
        List<KalenderEintrag> eintraege = sichtbar.stream()
                .filter(eintrag -> erfuelltFilter(eintrag, wirksamerFilter))
                .toList();
        return mitSummen(ersterTag, letzterTag, eintraege, projekteFuerFilter(sichtbar));
    }

    /** Projekte der sichtbaren Eintraege (ohne „Ohne Projekt“), je Projekt einmal, sortiert nach Name. */
    private List<ProjektAuswahl> projekteFuerFilter(List<KalenderEintrag> eintraege) {
        Map<Long, String> projekte = new HashMap<>();
        for (KalenderEintrag eintrag : eintraege) {
            if (eintrag.projektId() != null) {
                projekte.put(eintrag.projektId(), eintrag.projektName());
            }
        }
        return projekte.entrySet().stream()
                .map(projekt -> new ProjektAuswahl(projekt.getKey(), projekt.getValue()))
                .sorted(Comparator.comparing(ProjektAuswahl::name))
                .toList();
    }

    /** Ein Eintrag passt, wenn er jeden gesetzten Filter erfuellt. */
    private boolean erfuelltFilter(KalenderEintrag eintrag, KalenderFilter filter) {
        boolean projektPasst = filter.projektId() == null || filter.projektId().equals(eintrag.projektId());
        boolean standortPasst = filter.standort() == null || filter.standort() == eintrag.standort();
        return projektPasst && standortPasst;
    }

    /**
     * Abgeschlossene Buchungen kommen als Tagesteile (ZeitSegment) von M2, mit Pausenabzug durch M2;
     * so stimmen die Summen mit dem Monatsbericht ueberein. Eine laufende Buchung zerlegt der
     * Kalender selbst je Tag bis zur aktuellen Uhrzeit.
     */
    private List<KalenderEintrag> eintraegeLaden(long mitarbeiterId, LocalDate ersterTag, LocalDate letzterTag) {
        ZoneId zone = clock.getZone();
        Instant von = ersterTag.atStartOfDay(zone).toInstant();
        Instant bis = letzterTag.plusDays(1).atStartOfDay(zone).toInstant();
        List<BuchungInfo> buchungen = buchungsAuskunft.buchungen(mitarbeiterId, von, bis);
        Map<Long, String> projektNamen = new HashMap<>();
        List<KalenderEintrag> eintraege = new ArrayList<>();

        for (ZeitSegment segment : segmenteImZeitraum(mitarbeiterId, ersterTag, letzterTag)) {
            eintraege.add(new KalenderEintrag(segment.zeiteintragId(), segment.tag(),
                    ortszeit(segment.beginn()), ortszeit(segment.ende()), segment.anrechenbareMinuten(),
                    segment.projektId(), projektName(segment.projektId(), projektNamen), segment.standort(),
                    false, istGeaendert(segment.zeiteintragId(), buchungen)));
        }
        for (BuchungInfo buchung : buchungen) {
            if (buchung.laeuft()) {
                eintraege.addAll(laufendeEintraege(buchung, ersterTag, letzterTag, projektNamen));
            }
        }
        eintraege.sort(Comparator.comparing(KalenderEintrag::beginn));
        return eintraege;
    }

    private List<ZeitSegment> segmenteImZeitraum(long mitarbeiterId, LocalDate ersterTag, LocalDate letzterTag) {
        List<ZeitSegment> ergebnis = new ArrayList<>();
        YearMonth letzterMonat = YearMonth.from(letzterTag);
        for (YearMonth monat = YearMonth.from(ersterTag); !monat.isAfter(letzterMonat); monat = monat.plusMonths(1)) {
            for (ZeitSegment segment : buchungsAuskunft.segmente(mitarbeiterId, monat)) {
                if (!segment.tag().isBefore(ersterTag) && !segment.tag().isAfter(letzterTag)) {
                    ergebnis.add(segment);
                }
            }
        }
        return ergebnis;
    }

    private boolean istGeaendert(long zeiteintragId, List<BuchungInfo> buchungen) {
        return buchungen.stream().anyMatch(b -> b.zeiteintragId() == zeiteintragId && b.geaendert());
    }

    /** Laufende Buchung: je Tag ein Eintrag von Beginn (oder 0 Uhr) bis jetzt (oder 24 Uhr). */
    private List<KalenderEintrag> laufendeEintraege(BuchungInfo buchung, LocalDate ersterTag, LocalDate letzterTag,
            Map<Long, String> projektNamen) {
        ZoneId zone = clock.getZone();
        Instant jetzt = clock.instant();
        List<KalenderEintrag> ergebnis = new ArrayList<>();
        for (LocalDate tag = ersterTag; !tag.isAfter(letzterTag); tag = tag.plusDays(1)) {
            Instant tagesbeginn = tag.atStartOfDay(zone).toInstant();
            Instant tagesende = tag.plusDays(1).atStartOfDay(zone).toInstant();
            Instant beginn = buchung.beginn().isAfter(tagesbeginn) ? buchung.beginn() : tagesbeginn;
            Instant ende = jetzt.isBefore(tagesende) ? jetzt : tagesende;
            if (beginn.isBefore(ende)) {
                int minuten = (int) Duration.between(beginn, ende).toMinutes();
                ergebnis.add(new KalenderEintrag(buchung.zeiteintragId(), tag, ortszeit(beginn), ortszeit(ende),
                        minuten, buchung.projektId(), projektName(buchung.projektId(), projektNamen),
                        buchung.standort(), true, buchung.geaendert()));
            }
        }
        return ergebnis;
    }

    /** Projektname aus M3; jedes Projekt wird je Aufruf nur einmal nachgeschlagen. */
    private String projektName(Long projektId, Map<Long, String> projektNamen) {
        if (projektId == null) {
            return OHNE_PROJEKT;
        }
        return projektNamen.computeIfAbsent(projektId, id ->
                projektAuskunft.finde(id).map(ProjektInfo::name).orElse(UNBEKANNTES_PROJEKT));
    }

    private LocalDateTime ortszeit(Instant zeitpunkt) {
        return LocalDateTime.ofInstant(zeitpunkt, clock.getZone());
    }

    /** Summe je Tag (jeder Tag des Zeitraums, auch 0) und Summe des Zeitraums, aus genau diesen Eintraegen. */
    private KalenderInhalt mitSummen(LocalDate ersterTag, LocalDate letzterTag, List<KalenderEintrag> eintraege,
            List<ProjektAuswahl> projekte) {
        Map<LocalDate, Integer> tagessummen = new LinkedHashMap<>();
        for (LocalDate tag = ersterTag; !tag.isAfter(letzterTag); tag = tag.plusDays(1)) {
            tagessummen.put(tag, 0);
        }
        int gesamt = 0;
        for (KalenderEintrag eintrag : eintraege) {
            tagessummen.merge(eintrag.tag(), eintrag.minuten(), Integer::sum);
            gesamt += eintrag.minuten();
        }
        return new KalenderInhalt(ersterTag, letzterTag, List.copyOf(eintraege),
                Collections.unmodifiableMap(tagessummen), gesamt, projekte);
    }
}
