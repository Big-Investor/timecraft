package de.timecraft.kalender.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtAngemeldetException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.kalender.model.KalenderEintrag;
import de.timecraft.kalender.model.KalenderFilter;
import de.timecraft.kalender.model.KalenderInhalt;
import de.timecraft.kalender.model.Kalenderansicht;
import de.timecraft.kalender.model.WaehlbarePerson;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Liest den Kalender einer Person ueber die api von M2 (Buchungen), M3 (Projekte, Zuordnungen)
 * und M1 (Personen). Aendert nichts. Prueft, wer welchen Kalender sehen darf (FA-M6-07, 11).
 * Die Zeitzone kommt aus der Clock des Geruests (Europe/Berlin).
 */
@Service
public class KalenderService {

    static final String OHNE_PROJEKT = "Ohne Projekt";
    static final String UNBEKANNTES_PROJEKT = "Unbekanntes Projekt";
    private static final String NUR_EIGENER_KALENDER = "Sie dürfen nur Ihren eigenen Kalender sehen.";

    private final BuchungsAuskunft buchungsAuskunft;
    private final ProjektAuskunft projektAuskunft;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final Clock clock;

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

    /** Bezugstag des naechsten Zeitraums („Vor“). */
    public LocalDate vor(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag.plusDays(1);
            case WOCHE -> bezugstag.plusWeeks(1);
            case MONAT -> bezugstag.plusMonths(1);
        };
    }

    /** Bezugstag des vorigen Zeitraums („Zurück“). */
    public LocalDate zurueck(Kalenderansicht ansicht, LocalDate bezugstag) {
        return switch (ansicht) {
            case TAG -> bezugstag.minusDays(1);
            case WOCHE -> bezugstag.minusWeeks(1);
            case MONAT -> bezugstag.minusMonths(1);
        };
    }

    /**
     * Kalender der angemeldeten Person mit ihren eigenen Buchungen (FA-M6-01, 03, 04, 06, 11).
     * Jede angemeldete Rolle darf ihren eigenen Kalender sehen. Der Filter gilt auch fuer die Summen;
     * null wirkt wie KalenderFilter.KEINER.
     */
    public KalenderInhalt eigenerKalender(long mitarbeiterId, Rolle rolle, Kalenderansicht ansicht,
            LocalDate bezugstag, KalenderFilter filter) {
        pruefeAngemeldet(rolle);
        return kalender(mitarbeiterId, ansicht, bezugstag, filter, null);
    }

    /**
     * Personen fuer „Person wählen“ (FA-M6-07, UC-M6-04), sortiert nach Nachname und Vorname.
     * Projektleitung: aktive Mitarbeiter, die einem ihrer aktiven verantworteten Projekte zugeordnet sind.
     * Admin: alle Benutzer, auch ehemalige. Die angemeldete Person selbst fehlt („Mein Kalender“).
     * Eine leere Liste heisst: den Projekten ist niemand zugeordnet (UC-M6-04 2a).
     */
    public List<WaehlbarePerson> waehlbarePersonen(long mitarbeiterId, Rolle rolle) {
        pruefeAngemeldet(rolle);
        List<MitarbeiterInfo> personen = switch (rolle) {
            case MITARBEITER -> throw new ZugriffVerweigertException(NUR_EIGENER_KALENDER);
            case PROJEKTLEITUNG -> teamDerProjektleitung(mitarbeiterId);
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
        pruefeAngemeldet(rolle);
        if (personId == mitarbeiterId) {
            throw new FachlicheException("Ihren eigenen Kalender öffnen Sie über „Mein Kalender“.");
        }
        return switch (rolle) {
            case MITARBEITER -> throw new ZugriffVerweigertException(NUR_EIGENER_KALENDER);
            case PROJEKTLEITUNG -> kalender(personId, ansicht, bezugstag, filter,
                    sichtbareProjekteFuerProjektleitung(mitarbeiterId, personId));
            case ADMIN -> {
                if (mitarbeiterAuskunft.finde(personId).isEmpty()) {
                    throw new NichtGefundenException("Diese Person gibt es nicht.");
                }
                yield kalender(personId, ansicht, bezugstag, filter, null);
            }
        };
    }

    /** Aktive Mitarbeiter, die mindestens einem aktiven verantworteten Projekt zugeordnet sind. */
    private List<MitarbeiterInfo> teamDerProjektleitung(long projektleitungId) {
        Set<Long> ids = new HashSet<>();
        for (ProjektInfo projekt : aktiveVerantworteteProjekte(projektleitungId)) {
            ids.addAll(projektAuskunft.zugeordneteMitarbeiter(projekt.projektId()));
        }
        List<MitarbeiterInfo> team = new ArrayList<>();
        for (long id : ids) {
            mitarbeiterAuskunft.finde(id).filter(MitarbeiterInfo::aktiv).ifPresent(team::add);
        }
        return team;
    }

    /**
     * Projekte, deren Buchungen die Projektleitung im Kalender der Person sieht: ihre aktiven Projekte,
     * denen die Person noch zugeordnet ist. Ist die Person nicht (mehr) waehlbar, wird der Zugriff verweigert.
     */
    private Set<Long> sichtbareProjekteFuerProjektleitung(long projektleitungId, long personId) {
        boolean personAktiv = mitarbeiterAuskunft.finde(personId).map(MitarbeiterInfo::aktiv).orElse(false);
        Set<Long> projekte = new HashSet<>();
        for (ProjektInfo projekt : aktiveVerantworteteProjekte(projektleitungId)) {
            if (projektAuskunft.istZugeordnet(personId, projekt.projektId())) {
                projekte.add(projekt.projektId());
            }
        }
        if (!personAktiv || projekte.isEmpty()) {
            throw new ZugriffVerweigertException(
                    "Sie dürfen nur Kalender von Mitarbeitern Ihrer aktiven Projekte öffnen.");
        }
        return projekte;
    }

    private List<ProjektInfo> aktiveVerantworteteProjekte(long projektleitungId) {
        return projektAuskunft.verantworteteProjekte(projektleitungId).stream().filter(ProjektInfo::aktiv).toList();
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
        List<KalenderEintrag> eintraege = eintraegeLaden(personId, ersterTag, letzterTag).stream()
                .filter(eintrag -> erlaubteProjekte == null || erlaubteProjekte.contains(eintrag.projektId()))
                .filter(eintrag -> erfuelltFilter(eintrag, wirksamerFilter))
                .toList();
        return mitSummen(ersterTag, letzterTag, eintraege);
    }

    /** Ein Eintrag passt, wenn er jeden gesetzten Filter erfuellt. */
    private boolean erfuelltFilter(KalenderEintrag eintrag, KalenderFilter filter) {
        boolean projektPasst = filter.projektId() == null || filter.projektId().equals(eintrag.projektId());
        boolean standortPasst = filter.standort() == null || filter.standort() == eintrag.standort();
        return projektPasst && standortPasst;
    }

    private void pruefeAngemeldet(Rolle rolle) {
        if (rolle == null) {
            throw new NichtAngemeldetException("Bitte melden Sie sich an.");
        }
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
    private KalenderInhalt mitSummen(LocalDate ersterTag, LocalDate letzterTag, List<KalenderEintrag> eintraege) {
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
                Collections.unmodifiableMap(tagessummen), gesamt);
    }
}
