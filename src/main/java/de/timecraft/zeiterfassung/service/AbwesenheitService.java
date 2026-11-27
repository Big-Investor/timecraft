package de.timecraft.zeiterfassung.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.model.Abwesenheit;
import de.timecraft.zeiterfassung.model.AbwesenheitImKalender;
import de.timecraft.zeiterfassung.model.AbwesenheitsTag;
import de.timecraft.zeiterfassung.model.Urlaubskonto;
import de.timecraft.zeiterfassung.repository.AbwesenheitRepository;

/** @author Noah Cipa */
@Service
public class AbwesenheitService implements AbwesenheitsUebernahme {

    private final AbwesenheitRepository abwesenheitRepository;
    private final FeiertagService feiertagService;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final ZugriffsPruefung zugriffsPruefung;

    public AbwesenheitService(AbwesenheitRepository abwesenheitRepository, FeiertagService feiertagService,
            MitarbeiterAuskunft mitarbeiterAuskunft, ZugriffsPruefung zugriffsPruefung) {
        this.abwesenheitRepository = abwesenheitRepository;
        this.feiertagService = feiertagService;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.zugriffsPruefung = zugriffsPruefung;
    }

    /** Arbeitstag laut Arbeitszeitmodell aus M1 und kein Feiertag. Ohne Rechtepruefung. */
    boolean istArbeitstag(MitarbeiterInfo mitarbeiter, LocalDate tag) {
        return mitarbeiter.arbeitstage().contains(tag.getDayOfWeek()) && !feiertagService.istFeiertag(tag);
    }

    /**
     * Tage mit Soll im Zeitkonto: Arbeitstage ohne Feiertag und ohne genehmigte Abwesenheit. Feiertage und
     * Abwesenheiten werden einmal fuer den ganzen Zeitraum geladen (QA-A-03). Ohne Rechtepruefung.
     */
    int anzahlSollTage(MitarbeiterInfo mitarbeiter, LocalDate von, LocalDate bis) {
        if (von.isAfter(bis)) {
            return 0;
        }
        Set<LocalDate> feiertage = feiertagService.datenImZeitraum(von, bis);
        List<Abwesenheit> abwesenheiten = beruehrende(mitarbeiter.mitarbeiterId(), von, bis);
        int tage = 0;
        for (LocalDate tag = von; !tag.isAfter(bis); tag = tag.plusDays(1)) {
            if (mitarbeiter.arbeitstage().contains(tag.getDayOfWeek()) && !feiertage.contains(tag)
                    && !liegtIn(abwesenheiten, tag)) {
                tage++;
            }
        }
        return tage;
    }

    private boolean liegtIn(List<Abwesenheit> abwesenheiten, LocalDate tag) {
        return abwesenheiten.stream()
                .anyMatch(abwesenheit -> !tag.isBefore(abwesenheit.getVon()) && !tag.isAfter(abwesenheit.getBis()));
    }

    @Override
    @Transactional(readOnly = true)
    public int urlaubstage(long mitarbeiterId, LocalDate von, LocalDate bis) {
        pruefeZeitraum(von, bis);
        MitarbeiterInfo mitarbeiter = mitarbeiter(mitarbeiterId);
        return (int) von.datesUntil(bis.plusDays(1)).filter(tag -> istArbeitstag(mitarbeiter, tag)).count();
    }

    /**
     * Eigenes Urlaubskonto: Resturlaub ist Anspruch plus Uebertrag aus M1 minus alle genehmigten Urlaubstage
     * des Jahres, auch kuenftige (FA-M2-K5). Fremde Konten gibt es nicht, daraus ginge die Art "Urlaub"
     * hervor (FA-M2-20).
     */
    @Transactional(readOnly = true)
    public Urlaubskonto urlaubskonto(long angemeldeteId, Rolle rolle, int jahr) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        int genommen = genehmigteUrlaubstage(angemeldeteId, jahr);
        Optional<UrlaubsDaten> gefunden = mitarbeiterAuskunft.urlaubsdaten(angemeldeteId, jahr);
        if (gefunden.isEmpty()) {
            return new Urlaubskonto(jahr, null, null, genommen, null);
        }
        UrlaubsDaten daten = gefunden.get();
        int rest = daten.anspruchTage() + daten.uebertragTage() - genommen;
        return new Urlaubskonto(jahr, daten.anspruchTage(), daten.uebertragTage(), genommen, rest);
    }

    private int genehmigteUrlaubstage(long mitarbeiterId, int jahr) {
        LocalDate jahresAnfang = LocalDate.of(jahr, 1, 1);
        LocalDate jahresEnde = LocalDate.of(jahr, 12, 31);
        int tage = 0;
        for (Abwesenheit abwesenheit : beruehrende(mitarbeiterId, jahresAnfang, jahresEnde)) {
            if (abwesenheit.getArt() != AbwesenheitsArt.URLAUB) {
                continue;
            }
            LocalDate von = abwesenheit.getVon().isBefore(jahresAnfang) ? jahresAnfang : abwesenheit.getVon();
            LocalDate bis = abwesenheit.getBis().isAfter(jahresEnde) ? jahresEnde : abwesenheit.getBis();
            tage = tage + urlaubstage(mitarbeiterId, von, bis);
        }
        return tage;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ueberschneidetAbwesenheit(long mitarbeiterId, LocalDate von, LocalDate bis) {
        pruefeZeitraum(von, bis);
        return !beruehrende(mitarbeiterId, von, bis).isEmpty();
    }

    /** Ohne Rechtepruefung, nur fuer andere Services dieses Moduls. */
    boolean istAbwesend(long mitarbeiterId, LocalDate tag) {
        return !beruehrende(mitarbeiterId, tag, tag).isEmpty();
    }

    @Override
    @Transactional
    public long uebernehmen(long antragId, long mitarbeiterId, AbwesenheitsArt art, LocalDate von, LocalDate bis) {
        if (art == null) {
            throw new FachlicheException("Bitte geben Sie die Art der Abwesenheit an.");
        }
        pruefeZeitraum(von, bis);
        mitarbeiter(mitarbeiterId);
        // Ein zweiter Aufruf fuer dieselbe Person wartet hier; vor der ersten Abwesenheit gibt es nichts zu sperren
        abwesenheitRepository.findWithLockByMitarbeiterId(mitarbeiterId);
        if (ueberschneidetAbwesenheit(mitarbeiterId, von, bis)) {
            throw new FachlicheException("In diesem Zeitraum gibt es schon eine Abwesenheit.");
        }
        Abwesenheit gespeichert = abwesenheitRepository.save(new Abwesenheit(mitarbeiterId, antragId, art, von, bis));
        return gespeichert.getId();
    }

    @Override
    @Transactional
    public void aufheben(long abwesenheitId) {
        Abwesenheit abwesenheit = abwesenheitRepository.findById(abwesenheitId)
                .orElseThrow(() -> new NichtGefundenException("Die Abwesenheit wurde nicht gefunden."));
        abwesenheitRepository.delete(abwesenheit);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> abwesenheitZuAntrag(long antragId) {
        return abwesenheitRepository.findByAntragId(antragId).map(Abwesenheit::getId);
    }

    /**
     * Die Art sehen nur der Betroffene und sein Vorgesetzter, alle anderen sehen "abwesend" (FA-M2-20).
     * Abwesenheiten von Mitarbeitern, die M1 nicht kennt, fehlen im Kalender, statt ihn scheitern zu lassen.
     */
    @Transactional(readOnly = true)
    public List<AbwesenheitImKalender> kalender(long angemeldeteId, Rolle rolle, YearMonth monat) {
        ZugriffsPruefung.pruefeAngemeldet(rolle);
        List<AbwesenheitImKalender> ergebnis = new ArrayList<>();
        for (Abwesenheit abwesenheit : abwesenheitRepository.findByVonLessThanEqualAndBisGreaterThanEqualOrderByVon(
                monat.atEndOfMonth(), monat.atDay(1))) {
            Optional<MitarbeiterInfo> gefunden = mitarbeiterAuskunft.finde(abwesenheit.getMitarbeiterId());
            if (gefunden.isEmpty()) {
                continue;
            }
            MitarbeiterInfo abwesender = gefunden.get();
            int arbeitstage = arbeitstage(abwesender, abwesenheit, monat.atDay(1), monat.atEndOfMonth()).size();
            ergebnis.add(new AbwesenheitImKalender(abwesender.mitarbeiterId(), abwesender.name(),
                    abwesenheit.getVon(), abwesenheit.getBis(), sichtbareArt(angemeldeteId, abwesender, abwesenheit),
                    arbeitstage));
        }
        return ergebnis;
    }

    /**
     * Arbeitstage mit genehmigter Abwesenheit im Zeitraum fuer die Zeitenuebersicht. Das Leserecht prueft der
     * Aufrufer. Die Art ist nur fuer den Betroffenen und seinen Vorgesetzten gesetzt.
     */
    List<AbwesenheitsTag> abwesenheitsTage(long betrachterId, long mitarbeiterId, LocalDate von, LocalDate bis) {
        List<AbwesenheitsTag> ergebnis = new ArrayList<>();
        for (Abwesenheit abwesenheit : beruehrende(mitarbeiterId, von, bis)) {
            MitarbeiterInfo abwesender = mitarbeiter(mitarbeiterId);
            AbwesenheitsArt art = sichtbareArt(betrachterId, abwesender, abwesenheit);
            for (LocalDate tag : arbeitstage(abwesender, abwesenheit, von, bis)) {
                ergebnis.add(new AbwesenheitsTag(tag, art));
            }
        }
        return ergebnis;
    }

    /** Arbeitstage der Abwesenheit von {@code von} bis einschliesslich {@code bis}. */
    private List<LocalDate> arbeitstage(MitarbeiterInfo mitarbeiter, Abwesenheit abwesenheit, LocalDate von,
            LocalDate bis) {
        LocalDate erster = abwesenheit.getVon().isBefore(von) ? von : abwesenheit.getVon();
        LocalDate letzter = abwesenheit.getBis().isAfter(bis) ? bis : abwesenheit.getBis();
        return erster.datesUntil(letzter.plusDays(1)).filter(tag -> istArbeitstag(mitarbeiter, tag)).toList();
    }

    /** Null, wenn der Betrachter die Art nicht sehen darf. */
    private AbwesenheitsArt sichtbareArt(long betrachterId, MitarbeiterInfo abwesender, Abwesenheit abwesenheit) {
        boolean darfArtSehen = abwesender.mitarbeiterId() == betrachterId
                || Long.valueOf(betrachterId).equals(abwesender.vorgesetzterId());
        return darfArtSehen ? abwesenheit.getArt() : null;
    }

    /** Abwesenheiten der Person, die den Zeitraum von bis einschliesslich beruehren. */
    private List<Abwesenheit> beruehrende(long mitarbeiterId, LocalDate von, LocalDate bis) {
        return abwesenheitRepository.findByMitarbeiterIdAndVonLessThanEqualAndBisGreaterThanEqual(mitarbeiterId, bis,
                von);
    }

    private MitarbeiterInfo mitarbeiter(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId)
                .orElseThrow(() -> new NichtGefundenException("Der Mitarbeiter wurde nicht gefunden."));
    }

    private void pruefeZeitraum(LocalDate von, LocalDate bis) {
        if (von == null || bis == null) {
            throw new FachlicheException("Bitte geben Sie einen Zeitraum ein.");
        }
        if (bis.isBefore(von)) {
            throw new FachlicheException("Das Bis-Datum darf nicht vor dem Von-Datum liegen.");
        }
    }
}
