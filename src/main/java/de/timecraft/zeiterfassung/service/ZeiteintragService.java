package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.Standort;
import de.timecraft.zeiterfassung.model.AbwesenheitsTag;
import de.timecraft.zeiterfassung.model.Arbeitsblock;
import de.timecraft.zeiterfassung.model.Feiertag;
import de.timecraft.zeiterfassung.model.Pause;
import de.timecraft.zeiterfassung.model.StempelStatus;
import de.timecraft.zeiterfassung.model.UebersichtEintrag;
import de.timecraft.zeiterfassung.model.UebersichtTag;
import de.timecraft.zeiterfassung.model.UebersichtWoche;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.model.Zeitenuebersicht;
import de.timecraft.zeiterfassung.repository.PauseRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;
import de.timecraft.zeiterfassung.service.EingabeFehler.Feld;

/**
 * Stempeln, Nachtragen und Zeiten. Schreibende Methoden wirken immer auf die angemeldete Person
 * ({@code angemeldeteId}). Wer fremde Zeiten lesen will, braucht das Leserecht ({@link ZugriffsPruefung}).
 *
 * @author Noah Cipa
 */
@Service
public class ZeiteintragService {

    /** Ein Eintrag, bewertet zusammen mit den anderen Eintraegen seines Tages. */
    private record Tageswert(Zeiteintrag eintrag, int arbeitsMinuten, int abzugMinuten) {

        /** Netto-Arbeitszeit; bei laufendem Eintrag die bisherige Dauer ohne Pflichtabzug. */
        int nettoMinuten() {
            return eintrag.getEnde() == null ? arbeitsMinuten : nettoMinutenBeiEndeJetzt();
        }

        /** Netto-Arbeitszeit, die entsteht, wenn der Eintrag jetzt beendet wuerde. */
        int nettoMinutenBeiEndeJetzt() {
            return arbeitsMinuten - abzugMinuten;
        }
    }

    private static final DateTimeFormatter DATUM_UHRZEIT_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ZeiteintragRepository zeiteintragRepository;
    private final PauseRepository pauseRepository;
    private final PausenRegelService pausenRegelService;
    private final HoechstarbeitszeitTexte texte;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final ProjektAuskunft projektAuskunft;
    private final AbwesenheitService abwesenheitService;
    private final FeiertagService feiertagService;
    private final ZugriffsPruefung zugriffsPruefung;
    private final Clock clock;

    public ZeiteintragService(ZeiteintragRepository zeiteintragRepository, PauseRepository pauseRepository,
            PausenRegelService pausenRegelService, MitarbeiterAuskunft mitarbeiterAuskunft,
            ProjektAuskunft projektAuskunft, AbwesenheitService abwesenheitService, FeiertagService feiertagService,
            ZugriffsPruefung zugriffsPruefung, Clock clock) {
        this.zeiteintragRepository = zeiteintragRepository;
        this.pauseRepository = pauseRepository;
        this.pausenRegelService = pausenRegelService;
        this.texte = new HoechstarbeitszeitTexte(pausenRegelService);
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.projektAuskunft = projektAuskunft;
        this.abwesenheitService = abwesenheitService;
        this.feiertagService = feiertagService;
        this.zugriffsPruefung = zugriffsPruefung;
        this.clock = clock;
    }

    /** Datensaetze verweisen nur auf vorhandene Benutzer (QA-A-04). Ohne Rechtepruefung. */
    void pruefeMitarbeiterBekannt(long mitarbeiterId) {
        if (mitarbeiterAuskunft.finde(mitarbeiterId).isEmpty()) {
            throw new NichtGefundenException("Der Mitarbeiter wurde nicht gefunden.");
        }
    }

    /**
     * Sperrt bis zum Ende der Transaktion den neuesten Zeiteintrag des Mitarbeiters (Teamentscheidung
     * 04.11.2026, Umsetzung nach Entscheidung Noah 27.11.2026). Ein zweites Fenster wartet hier, bis die
     * erste Aktion gespeichert ist, und sieht danach deren Ergebnis. Luecke: Vor dem allerersten Eintrag
     * eines Mitarbeiters gibt es keine Zeile zum Sperren.
     */
    void sperreMitarbeiter(long mitarbeiterId) {
        zeiteintragRepository.findFirstByMitarbeiterIdOrderByBeginnDesc(mitarbeiterId);
    }

    @Transactional
    public Zeiteintrag einstempeln(long angemeldeteId, Rolle rolle, Standort standort, Long projektId) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        if (standort == null) {
            throw new EingabeFehler("Bitte wählen Sie einen Standort aus.", Feld.STANDORT);
        }
        pruefeProjekt(angemeldeteId, projektId);
        sperreMitarbeiter(angemeldeteId);
        if (zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(angemeldeteId).isPresent()) {
            throw new FachlicheException("Sie sind bereits eingestempelt.");
        }
        return zeiteintragRepository.save(new Zeiteintrag(angemeldeteId, jetzt(), standort, projektId));
    }

    /** Auswahl beim Einstempeln und Nachtragen: aktive Projekte, denen die Person zugeordnet ist (FA-M2-01). */
    @Transactional(readOnly = true)
    public List<ProjektInfo> aktiveProjekte(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return projektAuskunft.aktiveProjekteVon(angemeldeteId);
    }

    /** Projekt des eigenen laufenden Eintrags fuer die Startseite (FA-M2-05). */
    @Transactional(readOnly = true)
    public Optional<ProjektInfo> projektDesLaufendenEintrags(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return laufenderEintragVon(angemeldeteId).map(Zeiteintrag::getProjektId).flatMap(projektAuskunft::finde);
    }

    /** Ein gewaehltes Projekt muss aktiv und dem Mitarbeiter zugeordnet sein (FA-M2-01, FA-M2-07, UC-ZE-01 3b). */
    private void pruefeProjekt(long mitarbeiterId, Long projektId) {
        if (projektId == null) {
            return;
        }
        Optional<ProjektInfo> projekt = projektAuskunft.finde(projektId);
        if (projekt.isEmpty() || !projekt.get().aktiv() || !projektAuskunft.istZugeordnet(mitarbeiterId, projektId)) {
            throw new EingabeFehler("Das gewählte Projekt ist nicht mehr aktiv oder Ihnen nicht zugeordnet. "
                    + "Bitte wählen Sie ein anderes Projekt.", Feld.PROJEKT);
        }
    }

    /**
     * Hinweis beim Einstempeln an einem Feiertag oder einem Tag mit genehmigter Abwesenheit
     * (FA-M2-27, UC-ZE-01 3c). Leer, wenn der Tag ein normaler Tag ist. Eingestempelt wird trotzdem.
     */
    @Transactional(readOnly = true)
    public String hinweisZumHeutigenTag(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        LocalDate heute = heute();
        if (feiertagService.istFeiertag(heute)) {
            return "Heute ist ein Feiertag. Ihre Arbeitszeit wird trotzdem erfasst.";
        }
        if (abwesenheitService.istAbwesend(angemeldeteId, heute)) {
            return "Für heute ist eine genehmigte Abwesenheit eingetragen. Ihre Arbeitszeit wird trotzdem erfasst.";
        }
        return "";
    }

    @Transactional
    public Zeiteintrag ausstempeln(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        Zeiteintrag laufenderEintrag = gesperrterLaufenderEintrag(angemeldeteId);
        Instant jetzt = jetzt();
        offenePause(laufenderEintrag).ifPresent(pause -> pause.pauseBeenden(jetzt));
        laufenderEintrag.ausstempeln(jetzt);
        return laufenderEintrag;
    }

    @Transactional
    public Pause pauseStarten(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        Zeiteintrag laufenderEintrag = gesperrterLaufenderEintrag(angemeldeteId);
        if (offenePause(laufenderEintrag).isPresent()) {
            throw new FachlicheException("Sie sind bereits in einer Pause.");
        }
        return pauseRepository.save(new Pause(laufenderEintrag, jetzt()));
    }

    @Transactional
    public Pause pauseBeenden(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        Pause laufendePause = offenePause(gesperrterLaufenderEintrag(angemeldeteId))
                .orElseThrow(() -> new FachlicheException("Sie sind nicht in einer Pause."));
        laufendePause.pauseBeenden(jetzt());
        return laufendePause;
    }

    /** Sperrt und laedt den laufenden Eintrag, den es fuer Pause und Ausstempeln geben muss. */
    private Zeiteintrag gesperrterLaufenderEintrag(long mitarbeiterId) {
        sperreMitarbeiter(mitarbeiterId);
        return laufenderEintragVon(mitarbeiterId)
                .orElseThrow(() -> new FachlicheException("Sie sind nicht eingestempelt."));
    }

    private Optional<Pause> offenePause(Zeiteintrag eintrag) {
        return pauseRepository.findByZeiteintrag_IdAndEndeIsNull(eintrag.getId());
    }

    /** Eigener Status fuer die Startseite (FA-M2-05). */
    @Transactional(readOnly = true)
    public StempelStatus status(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return statusVon(angemeldeteId);
    }

    /**
     * Ein laufender Eintrag geht vor: Wer trotz Abwesenheit eingestempelt ist, ist eingestempelt.
     * Ohne laufenden Eintrag und mit genehmigter Abwesenheit heute: abwesend (FA-M2-05, FA-M2-21).
     * Ohne Rechtepruefung, nur fuer andere Services dieses Moduls (Anwesenheit).
     */
    StempelStatus statusVon(long mitarbeiterId) {
        Optional<Zeiteintrag> laufenderEintrag = laufenderEintragVon(mitarbeiterId);
        if (laufenderEintrag.isEmpty()) {
            if (abwesenheitService.istAbwesend(mitarbeiterId, heute())) {
                return StempelStatus.ABWESEND;
            }
            return StempelStatus.NICHT_EINGESTEMPELT;
        }
        if (offenePause(laufenderEintrag.get()).isPresent()) {
            return StempelStatus.IN_PAUSE;
        }
        return StempelStatus.EINGESTEMPELT;
    }

    @Transactional(readOnly = true)
    public Optional<Zeiteintrag> laufenderEintrag(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return laufenderEintragVon(angemeldeteId);
    }

    /** Ohne Rechtepruefung, nur fuer andere Services dieses Moduls (Anwesenheit). */
    Optional<Zeiteintrag> laufenderEintragVon(long mitarbeiterId) {
        return zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(mitarbeiterId);
    }

    @Transactional(readOnly = true)
    public Optional<Pause> laufendePause(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return laufenderEintragVon(angemeldeteId).flatMap(this::offenePause);
    }

    /**
     * Nur der Teil der Pause, der im Zeiteintrag liegt. Nach einer Korrektur (BuchungsAenderung) kann eine
     * Pause teilweise oder ganz ausserhalb liegen. Offene Pause und offener Eintrag reichen bis jetzt.
     */
    private Duration pausenDauer(Zeiteintrag eintrag, Pause pause, Instant jetzt) {
        Instant eintragsEnde = eintrag.getEnde() != null ? eintrag.getEnde() : jetzt;
        Instant pausenEnde = pause.getEnde() != null ? pause.getEnde() : jetzt;
        Instant von = pause.getBeginn().isAfter(eintrag.getBeginn()) ? pause.getBeginn() : eintrag.getBeginn();
        Instant bis = pausenEnde.isBefore(eintragsEnde) ? pausenEnde : eintragsEnde;
        return bis.isAfter(von) ? Duration.between(von, bis) : Duration.ZERO;
    }

    private List<Duration> pausenDauern(Zeiteintrag eintrag, Instant jetzt) {
        List<Duration> dauern = new ArrayList<>();
        for (Pause pause : pauseRepository.findByZeiteintrag_Id(eintrag.getId())) {
            dauern.add(pausenDauer(eintrag, pause, jetzt));
        }
        return dauern;
    }

    private Arbeitsblock arbeitsblock(Zeiteintrag eintrag, Instant jetzt) {
        Instant ende = eintrag.getEnde() != null ? eintrag.getEnde() : jetzt;
        return new Arbeitsblock(eintrag.getBeginn(), ende, pausenDauern(eintrag, jetzt));
    }

    /** Bewertet alle Eintraege einer Person an einem Tag gemeinsam, sortiert nach Beginn. */
    private List<Tageswert> bewerteTag(List<Zeiteintrag> eintraegeDesTages) {
        if (eintraegeDesTages.isEmpty()) {
            return List.of();
        }
        Instant jetzt = jetzt();
        List<Arbeitsblock> bloecke = new ArrayList<>();
        for (Zeiteintrag eintrag : eintraegeDesTages) {
            bloecke.add(arbeitsblock(eintrag, jetzt));
        }
        Zeiteintrag erster = eintraegeDesTages.get(0);
        List<Integer> abzuege = pausenRegelService.pflichtpausenAbzuege(bloecke,
                istMinderjaehrig(erster.getMitarbeiterId(), tagDesBeginns(erster)));
        List<Tageswert> werte = new ArrayList<>();
        for (int i = 0; i < bloecke.size(); i++) {
            werte.add(new Tageswert(eintraegeDesTages.get(i), bloecke.get(i).arbeitsMinuten(), abzuege.get(i)));
        }
        return werte;
    }

    /** Wie {@link #bewerteTag} fuer Eintraege mehrerer Tage einer Person; jeder Tag muss vollstaendig sein. */
    private List<Tageswert> bewerte(List<Zeiteintrag> eintraege) {
        Map<LocalDate, List<Zeiteintrag>> jeTag = new LinkedHashMap<>();
        for (Zeiteintrag eintrag : eintraege) {
            jeTag.computeIfAbsent(tagDesBeginns(eintrag), tag -> new ArrayList<>()).add(eintrag);
        }
        List<Tageswert> werte = new ArrayList<>();
        for (List<Zeiteintrag> eintraegeDesTages : jeTag.values()) {
            werte.addAll(bewerteTag(eintraegeDesTages));
        }
        return werte;
    }

    private List<Tageswert> bewerteTagVon(Zeiteintrag eintrag) {
        LocalDate tag = tagDesBeginns(eintrag);
        return bewerteTag(eintraegeImZeitraum(eintrag.getMitarbeiterId(), tag, tag));
    }

    private Tageswert bewerteEintrag(Zeiteintrag eintrag) {
        return bewerteTagVon(eintrag).stream()
                .filter(wert -> Objects.equals(wert.eintrag().getId(), eintrag.getId()))
                .findFirst()
                .orElseThrow(() -> new NichtGefundenException("Der Zeiteintrag wurde nicht gefunden."));
    }

    /** Anteil dieses Eintrags an der fehlenden Pflichtpause seines Tages, 0 bei laufendem Eintrag. */
    int fehlendePauseMinuten(Zeiteintrag eintrag) {
        if (eintrag.getEnde() == null) {
            return 0;
        }
        return bewerteEintrag(eintrag).abzugMinuten();
    }

    Duration nettoArbeitszeit(Zeiteintrag eintrag) {
        return Duration.ofMinutes(bewerteEintrag(eintrag).nettoMinuten());
    }

    /**
     * Hinweise sofort nach dem Ausstempeln oder Nachtragen (FA-M2-13 bis 15). Jede Regel hat eine eigene
     * kleine Methode, damit weitere Regeln (z. B. Ruhezeit, K3) dazukommen koennen.
     */
    @Transactional(readOnly = true)
    public String hinweis(long angemeldeteId, Rolle rolle, Zeiteintrag eintrag) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        if (eintrag.getMitarbeiterId() != angemeldeteId) {
            throw new ZugriffVerweigertException("Hinweise gibt es nur zu Ihren eigenen Zeiteinträgen.");
        }
        LocalDate tag = tagDesBeginns(eintrag);
        long mitarbeiterId = eintrag.getMitarbeiterId();
        boolean minderjaehrig = istMinderjaehrig(mitarbeiterId, tag);
        LocalDate montag = tag.with(DayOfWeek.MONDAY);
        int amTag = nettoMinutenBeiEndeJetzt(mitarbeiterId, tag, tag);
        int inDerWoche = nettoMinutenBeiEndeJetzt(mitarbeiterId, montag, montag.plusDays(6));
        String hinweis = pausenHinweis(eintrag) + texte.tagUeberschritten(amTag, minderjaehrig)
                + texte.wocheUeberschritten(inDerWoche, minderjaehrig);
        return hinweis.trim();
    }

    private String pausenHinweis(Zeiteintrag eintrag) {
        int fehlendePause = bewerteTagVon(eintrag).stream().mapToInt(Tageswert::abzugMinuten).sum();
        if (fehlendePause == 0) {
            return "";
        }
        return "An diesem Tag fehlen " + fehlendePause
                + " Minuten Pause, diese Zeit wird von der Arbeitszeit abgezogen. ";
    }

    /**
     * Warnung bei laufendem Eintrag ab 30 Minuten vor der Grenze, danach Hinweis auf die Ueberschreitung
     * (FA-M2-15). Gerechnet wird mit der Netto-Arbeitszeit, die beim Ausstempeln jetzt entstuende. Die
     * Startseite zeigt die Warnung beim Aufruf und nach jeder Stempelaktion. Leer ohne Warnung.
     */
    @Transactional(readOnly = true)
    public String warnungHoechstarbeitszeit(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        Optional<Zeiteintrag> laufenderEintrag = laufenderEintragVon(angemeldeteId);
        if (laufenderEintrag.isEmpty()) {
            return "";
        }
        // Ein Eintrag ueber Mitternacht zaehlt zum Tag seines Beginns (FA-M2-08), auch beim vergessenen Ausstempeln
        LocalDate tag = tagDesBeginns(laufenderEintrag.get());
        boolean minderjaehrig = istMinderjaehrig(angemeldeteId, tag);
        int amTag = nettoMinutenBeiEndeJetzt(angemeldeteId, tag, tag);
        int inDerWoche = nettoMinutenBeiEndeJetzt(angemeldeteId, tag.with(DayOfWeek.MONDAY), tag);
        String warnung = texte.tagesWarnung(amTag, minderjaehrig) + texte.wochenWarnung(inDerWoche, minderjaehrig);
        return warnung.trim();
    }

    private int nettoMinutenBeiEndeJetzt(long mitarbeiterId, LocalDate von, LocalDate bis) {
        return bewerte(eintraegeImZeitraum(mitarbeiterId, von, bis)).stream()
                .mapToInt(Tageswert::nettoMinutenBeiEndeJetzt).sum();
    }

    /** Netto-Minuten der abgeschlossenen Eintraege (Ist im Zeitkonto). Ohne Rechtepruefung. */
    int istMinuten(long mitarbeiterId, LocalDate von, LocalDate bis) {
        return bewerte(eintraegeImZeitraum(mitarbeiterId, von, bis)).stream()
                .filter(wert -> wert.eintrag().getEnde() != null)
                .mapToInt(Tageswert::nettoMinuten).sum();
    }

    private Duration nettoSumme(List<Zeiteintrag> eintraege) {
        return Duration.ofMinutes(bewerte(eintraege).stream().mapToInt(Tageswert::nettoMinuten).sum());
    }

    /**
     * Geburtsdatum aus M1 (FA-M2-12). Ist der Mitarbeiter in M1 unbekannt, gelten wie bei fehlendem
     * Geburtsdatum die Regeln fuer Erwachsene, damit Sammelabfragen (z. B. je Projekt) nicht scheitern.
     */
    private boolean istMinderjaehrig(long mitarbeiterId, LocalDate tag) {
        Optional<MitarbeiterInfo> mitarbeiter = mitarbeiterAuskunft.finde(mitarbeiterId);
        if (mitarbeiter.isEmpty()) {
            return false;
        }
        return pausenRegelService.istMinderjaehrig(mitarbeiter.get().geburtsdatum(), tag);
    }

    /** Heutige Arbeitszeit; ein noch laufender Eintrag von frueher zaehlt mit (FA-M2-05). */
    @Transactional(readOnly = true)
    public Duration arbeitszeitHeute(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return nettoSumme(eintraegeHeuteUndLaufender(angemeldeteId));
    }

    /** Bisherige Dauer des eigenen laufenden Eintrags ohne seine Pausen (FA-M2-05). Leer ohne laufenden Eintrag. */
    @Transactional(readOnly = true)
    public Optional<Duration> dauerLaufenderEintrag(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        return laufenderEintragVon(angemeldeteId).map(this::nettoArbeitszeit);
    }

    @Transactional(readOnly = true)
    public Duration pausenHeute(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        Duration summe = Duration.ZERO;
        for (Zeiteintrag eintrag : eintraegeHeuteUndLaufender(angemeldeteId)) {
            summe = summe.plus(pausen(eintrag));
        }
        return summe;
    }

    private List<Zeiteintrag> eintraegeHeuteUndLaufender(long mitarbeiterId) {
        LocalDate heute = heute();
        List<Zeiteintrag> eintraege = new ArrayList<>(eintraegeImZeitraum(mitarbeiterId, heute, heute));
        laufenderEintragVon(mitarbeiterId)
                .filter(laufend -> tagDesBeginns(laufend).isBefore(heute))
                .ifPresent(laufend -> eintraege.add(0, laufend));
        return eintraege;
    }

    @Transactional(readOnly = true)
    public Duration arbeitszeitDieseWoche(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        LocalDate heute = heute();
        return nettoSumme(eintraegeImZeitraum(angemeldeteId, heute.with(DayOfWeek.MONDAY), heute));
    }

    /**
     * Vergessene Arbeitszeit nachtragen (FA-M2-06, FA-M2-07). Beginn und Ende liegen am selben Kalendertag
     * (Entscheidung Noah 27.11.2026). Die Pause ist optional, dann aber mit Beginn und Ende, und liegt
     * innerhalb des Eintrags. Bei einem Fehler wird nichts gespeichert.
     */
    @Transactional
    public Zeiteintrag nachtragen(long angemeldeteId, Rolle rolle, LocalDate datum, LocalTime beginn, LocalTime ende,
            LocalTime pauseBeginn, LocalTime pauseEnde, Standort standort, Long projektId) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        pruefePflichtfelder(datum, beginn, ende, standort);
        Instant startzeit = zeitpunkt(datum, beginn);
        Instant endzeit = zeitpunkt(datum, ende);
        if (!endzeit.isAfter(startzeit)) {
            throw new EingabeFehler("Das Ende muss nach dem Beginn liegen.", Feld.ENDE);
        }
        if (endzeit.isAfter(clock.instant())) {
            throw new EingabeFehler("Das Ende darf nicht in der Zukunft liegen.", Feld.ENDE);
        }
        if (datum.getYear() != heute().getYear()) {
            throw new EingabeFehler("Sie können nur Zeiten aus dem laufenden Jahr nachtragen.", Feld.DATUM);
        }
        pruefePause(datum, startzeit, endzeit, pauseBeginn, pauseEnde);
        pruefeProjekt(angemeldeteId, projektId);
        sperreMitarbeiter(angemeldeteId);
        pruefeUeberschneidung(angemeldeteId, startzeit, endzeit, null);

        Zeiteintrag eintrag = new Zeiteintrag(angemeldeteId, startzeit, standort, projektId);
        eintrag.ausstempeln(endzeit);
        Zeiteintrag gespeichert = zeiteintragRepository.save(eintrag);
        if (pauseBeginn != null) {
            Pause pause = new Pause(gespeichert, zeitpunkt(datum, pauseBeginn));
            pause.pauseBeenden(zeitpunkt(datum, pauseEnde));
            pauseRepository.save(pause);
        }
        return gespeichert;
    }

    private void pruefePflichtfelder(LocalDate datum, LocalTime beginn, LocalTime ende, Standort standort) {
        Set<Feld> fehlende = EnumSet.noneOf(Feld.class);
        if (datum == null) {
            fehlende.add(Feld.DATUM);
        }
        if (beginn == null) {
            fehlende.add(Feld.BEGINN);
        }
        if (ende == null) {
            fehlende.add(Feld.ENDE);
        }
        if (standort == null) {
            fehlende.add(Feld.STANDORT);
        }
        if (!fehlende.isEmpty()) {
            throw new EingabeFehler("Bitte füllen Sie Datum, Beginn, Ende und Standort aus.", fehlende);
        }
    }

    /** Pause beim Nachtragen: beide Zeiten oder keine, Ende nach Beginn, echt innerhalb des Eintrags (FA-M2-07). */
    private void pruefePause(LocalDate datum, Instant startzeit, Instant endzeit, LocalTime pauseBeginn,
            LocalTime pauseEnde) {
        if (pauseBeginn == null && pauseEnde == null) {
            return;
        }
        if (pauseBeginn == null || pauseEnde == null) {
            throw new EingabeFehler("Bitte geben Sie Beginn und Ende der Pause an.",
                    pauseBeginn == null ? Feld.PAUSE_VON : Feld.PAUSE_BIS);
        }
        Instant pausenStart = zeitpunkt(datum, pauseBeginn);
        Instant pausenEnde = zeitpunkt(datum, pauseEnde);
        if (!pausenEnde.isAfter(pausenStart)) {
            throw new EingabeFehler("Das Ende der Pause muss nach ihrem Beginn liegen.", Feld.PAUSE_BIS);
        }
        if (!pausenStart.isAfter(startzeit) || !pausenEnde.isBefore(endzeit)) {
            throw new EingabeFehler("Die Pause muss innerhalb des Zeiteintrags liegen.", Feld.PAUSE_VON,
                    Feld.PAUSE_BIS);
        }
    }

    /** Ohne Rechtepruefung, nur in einer schreibenden Transaktion nach {@link #sperreMitarbeiter}. */
    void pruefeUeberschneidung(long mitarbeiterId, Instant startzeit, Instant endzeit,
            Long ausgenommeneEintragId) {
        for (Zeiteintrag anderer : zeiteintragRepository.findeUeberschneidende(mitarbeiterId, startzeit, endzeit)) {
            if (anderer.getId() != null && anderer.getId().equals(ausgenommeneEintragId)) {
                continue;
            }
            throw new EingabeFehler("Der Eintrag überschneidet sich mit Ihrem Eintrag vom "
                    + DATUM_UHRZEIT_FORMATTER.format(anderer.getBeginn().atZone(clock.getZone())) + " Uhr.",
                    Feld.BEGINN, Feld.ENDE);
        }
    }

    /** Eigene Zeiteintraege oder als Projektleitung die der Mitarbeiter ihrer Projekte (FA-M2-10). */
    @Transactional(readOnly = true)
    public List<Zeiteintrag> zeiten(long angemeldeteId, Rolle rolle, long mitarbeiterId, LocalDate von, LocalDate bis) {
        zugriffsPruefung.pruefeLesen(angemeldeteId, rolle, mitarbeiterId);
        pruefeZeitraum(von, bis);
        return eintraegeImZeitraum(mitarbeiterId, von, bis);
    }

    private void pruefeZeitraum(LocalDate von, LocalDate bis) {
        Set<Feld> fehlende = EnumSet.noneOf(Feld.class);
        if (von == null) {
            fehlende.add(Feld.VON);
        }
        if (bis == null) {
            fehlende.add(Feld.BIS);
        }
        if (!fehlende.isEmpty()) {
            throw new EingabeFehler("Bitte geben Sie einen Zeitraum ein.", fehlende);
        }
        if (bis.isBefore(von)) {
            throw new EingabeFehler("Das Bis-Datum darf nicht vor dem Von-Datum liegen.", Feld.BIS);
        }
    }

    /**
     * Eintraege je Woche und Tag mit Summen, Feiertagen und genehmigten Abwesenheiten (FA-M2-08, FA-M2-09,
     * FA-M2-27). Leserecht wie bei {@link #zeiten}.
     */
    @Transactional(readOnly = true)
    public Zeitenuebersicht uebersicht(long angemeldeteId, Rolle rolle, long mitarbeiterId, LocalDate von,
            LocalDate bis) {
        List<Zeiteintrag> eintraege = zeiten(angemeldeteId, rolle, mitarbeiterId, von, bis);
        Map<LocalDate, List<UebersichtEintrag>> eintraegeJeTag = uebersichtEintraege(eintraege);
        Map<LocalDate, String> feiertage = new HashMap<>();
        for (Feiertag feiertag : feiertagService.imZeitraum(rolle, von, bis)) {
            feiertage.put(feiertag.getDatum(), feiertag.getName());
        }
        Map<LocalDate, AbwesenheitsTag> abwesenheiten = new HashMap<>();
        for (AbwesenheitsTag tag : abwesenheitService.abwesenheitsTage(angemeldeteId, mitarbeiterId, von, bis)) {
            abwesenheiten.put(tag.datum(), tag);
        }
        SortedSet<LocalDate> tage = new TreeSet<>(eintraegeJeTag.keySet());
        tage.addAll(feiertage.keySet());
        tage.addAll(abwesenheiten.keySet());

        SortedMap<LocalDate, List<UebersichtTag>> tageJeWoche = new TreeMap<>();
        for (LocalDate datum : tage) {
            List<UebersichtEintrag> eintraegeDesTages = eintraegeJeTag.getOrDefault(datum, List.of());
            Duration tagesSumme = eintraegeDesTages.stream().map(UebersichtEintrag::netto)
                    .reduce(Duration.ZERO, Duration::plus);
            tageJeWoche.computeIfAbsent(datum.with(DayOfWeek.MONDAY), montag -> new ArrayList<>())
                    .add(new UebersichtTag(datum, feiertage.get(datum), abwesenheiten.get(datum), eintraegeDesTages,
                            tagesSumme));
        }
        return zusammenfassen(tageJeWoche);
    }

    private Zeitenuebersicht zusammenfassen(SortedMap<LocalDate, List<UebersichtTag>> tageJeWoche) {
        List<UebersichtWoche> wochen = new ArrayList<>();
        Duration summe = Duration.ZERO;
        for (Map.Entry<LocalDate, List<UebersichtTag>> woche : tageJeWoche.entrySet()) {
            Duration wochenSumme = woche.getValue().stream().map(UebersichtTag::summe)
                    .reduce(Duration.ZERO, Duration::plus);
            wochen.add(new UebersichtWoche(woche.getKey().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), woche.getValue(),
                    wochenSumme));
            summe = summe.plus(wochenSumme);
        }
        return new Zeitenuebersicht(wochen, summe);
    }

    private Map<LocalDate, List<UebersichtEintrag>> uebersichtEintraege(List<Zeiteintrag> eintraege) {
        Map<Long, Optional<ProjektInfo>> projekte = new HashMap<>();
        Map<LocalDate, List<UebersichtEintrag>> jeTag = new HashMap<>();
        for (Tageswert wert : bewerte(eintraege)) {
            Zeiteintrag eintrag = wert.eintrag();
            String projektName = null;
            if (eintrag.getProjektId() != null) {
                projektName = projekte.computeIfAbsent(eintrag.getProjektId(), projektAuskunft::finde)
                        .map(ProjektInfo::name).orElse(null);
            }
            int abzug = eintrag.getEnde() == null ? 0 : wert.abzugMinuten();
            jeTag.computeIfAbsent(tagDesBeginns(eintrag), tag -> new ArrayList<>()).add(new UebersichtEintrag(
                    eintrag, projektName, pausen(eintrag), Duration.ofMinutes(wert.nettoMinuten()), abzug));
        }
        return jeTag;
    }

    Duration pausen(Zeiteintrag eintrag) {
        return pausenDauern(eintrag, jetzt()).stream().reduce(Duration.ZERO, Duration::plus);
    }

    /** Serverzeit, minutengenau (RB-M2-01). */
    private Instant jetzt() {
        return clock.instant().truncatedTo(ChronoUnit.MINUTES);
    }

    public LocalDate heute() {
        return LocalDate.now(clock);
    }

    /** Ein Eintrag ueber Mitternacht zaehlt zum Tag seines Beginns (FA-M2-08). */
    LocalDate tagDesBeginns(Zeiteintrag eintrag) {
        return tag(eintrag.getBeginn());
    }

    /**
     * Eintraege, deren Beginn an einem Tag von {@code von} bis einschliesslich {@code bis} liegt (Europe/Berlin).
     * Ohne Rechtepruefung, die prueft der Aufrufer.
     */
    List<Zeiteintrag> eintraegeImZeitraum(long mitarbeiterId, LocalDate von, LocalDate bis) {
        return zeiteintragRepository.findByMitarbeiterIdAndBeginnGreaterThanEqualAndBeginnLessThanOrderByBeginn(
                mitarbeiterId, tagesbeginn(von), tagesbeginn(bis.plusDays(1)));
    }

    private LocalDate tag(Instant zeitpunkt) {
        return LocalDate.ofInstant(zeitpunkt, clock.getZone());
    }

    private Instant tagesbeginn(LocalDate tag) {
        return tag.atStartOfDay(clock.getZone()).toInstant();
    }

    private Instant zeitpunkt(LocalDate datum, LocalTime uhrzeit) {
        return datum.atTime(uhrzeit.truncatedTo(ChronoUnit.MINUTES)).atZone(clock.getZone()).toInstant();
    }
}
