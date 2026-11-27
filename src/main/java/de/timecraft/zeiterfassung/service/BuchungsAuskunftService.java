package de.timecraft.zeiterfassung.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import de.timecraft.zeiterfassung.model.Pause;
import de.timecraft.zeiterfassung.model.Zeiteintrag;
import de.timecraft.zeiterfassung.repository.AenderungsprotokollRepository;
import de.timecraft.zeiterfassung.repository.PauseRepository;
import de.timecraft.zeiterfassung.repository.ZeiteintragRepository;

/** @author Noah Cipa */
@Service
public class BuchungsAuskunftService implements BuchungsAuskunft {

    private final ZeiteintragRepository zeiteintragRepository;
    private final PauseRepository pauseRepository;
    private final AenderungsprotokollRepository aenderungsprotokollRepository;
    private final ZeiteintragService zeiteintragService;
    private final Clock clock;

    public BuchungsAuskunftService(ZeiteintragRepository zeiteintragRepository, PauseRepository pauseRepository,
            AenderungsprotokollRepository aenderungsprotokollRepository, ZeiteintragService zeiteintragService,
            Clock clock) {
        this.zeiteintragRepository = zeiteintragRepository;
        this.pauseRepository = pauseRepository;
        this.aenderungsprotokollRepository = aenderungsprotokollRepository;
        this.zeiteintragService = zeiteintragService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BuchungInfo> buchung(long zeiteintragId) {
        return zeiteintragRepository.findById(zeiteintragId).map(this::alsBuchungInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId) {
        return zeiteintragRepository.findByMitarbeiterIdAndEndeIsNull(mitarbeiterId).map(this::alsBuchungInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId) {
        return zeiteintragRepository.findByProjektIdAndEndeIsNull(projektId).stream().map(this::alsBuchungInfo)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis) {
        Instant jetzt = clock.instant();
        List<BuchungInfo> ergebnis = new ArrayList<>();
        for (Zeiteintrag eintrag : zeiteintragRepository.findeUeberschneidende(mitarbeiterId, von, bis)) {
            // Ein laufender Eintrag reicht nur bis jetzt
            if (eintrag.getEnde() != null || jetzt.isAfter(von)) {
                ergebnis.add(alsBuchungInfo(eintrag));
            }
        }
        return ergebnis;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat) {
        return segmenteImMonat(zeiteintragRepository.findeUeberschneidende(mitarbeiterId,
                monatsBeginn(monat), monatsBeginn(monat.plusMonths(1))), monat);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat) {
        return segmenteImMonat(zeiteintragRepository.findeUeberschneidendeAufProjekt(projektId,
                monatsBeginn(monat), monatsBeginn(monat.plusMonths(1))), monat);
    }

    private List<ZeitSegment> segmenteImMonat(List<Zeiteintrag> eintraege, YearMonth monat) {
        List<ZeitSegment> ergebnis = new ArrayList<>();
        for (Zeiteintrag eintrag : eintraege) {
            if (eintrag.getEnde() == null) {
                continue;
            }
            for (ZeitSegment segment : zerlegeInTage(eintrag)) {
                if (YearMonth.from(segment.tag()).equals(monat)) {
                    ergebnis.add(segment);
                }
            }
        }
        return ergebnis;
    }

    /**
     * Zerlegt an jeder Mitternacht (Europe/Berlin), auch an Tagen mit 23 oder 25 Stunden. Erfasste Pausen
     * zaehlen an dem Tag, an dem sie liegen. Die fehlende Pflichtpause wird danach vom Ende der Buchung her
     * abgezogen: erst vom letzten Teilstueck, reicht dieses nicht, vom Teilstueck davor. Kein Teilstueck wird
     * negativ, und die Summe aller Teilstuecke ist die Netto-Arbeitszeit der Buchung.
     */
    private List<ZeitSegment> zerlegeInTage(Zeiteintrag eintrag) {
        List<Pause> pausen = pauseRepository.findByZeiteintrag_Id(eintrag.getId());
        List<ZeitSegment> segmente = new ArrayList<>();
        Instant teilBeginn = eintrag.getBeginn();
        while (teilBeginn.isBefore(eintrag.getEnde())) {
            LocalDate tag = tag(teilBeginn);
            Instant mitternacht = tagesbeginn(tag.plusDays(1));
            Instant teilEnde = mitternacht.isBefore(eintrag.getEnde()) ? mitternacht : eintrag.getEnde();
            int minuten = (int) (Duration.between(teilBeginn, teilEnde).toMinutes()
                    - pausenMinutenZwischen(pausen, teilBeginn, teilEnde));
            segmente.add(new ZeitSegment(eintrag.getId(), eintrag.getMitarbeiterId(), eintrag.getProjektId(),
                    eintrag.getStandort(), tag, teilBeginn, teilEnde, minuten));
            teilBeginn = teilEnde;
        }
        int offen = zeiteintragService.fehlendePauseMinuten(eintrag);
        for (int i = segmente.size() - 1; i >= 0 && offen > 0; i--) {
            ZeitSegment segment = segmente.get(i);
            int abzug = Math.min(offen, segment.anrechenbareMinuten());
            segmente.set(i, mitMinuten(segment, segment.anrechenbareMinuten() - abzug));
            offen = offen - abzug;
        }
        return segmente;
    }

    private ZeitSegment mitMinuten(ZeitSegment s, int minuten) {
        return new ZeitSegment(s.zeiteintragId(), s.mitarbeiterId(), s.projektId(), s.standort(), s.tag(), s.beginn(),
                s.ende(), minuten);
    }

    private long pausenMinutenZwischen(List<Pause> pausen, Instant von, Instant bis) {
        long summe = 0;
        for (Pause pause : pausen) {
            if (pause.getEnde() == null) {
                continue;
            }
            Instant anfang = pause.getBeginn().isAfter(von) ? pause.getBeginn() : von;
            Instant ende = pause.getEnde().isBefore(bis) ? pause.getEnde() : bis;
            if (ende.isAfter(anfang)) {
                summe = summe + Duration.between(anfang, ende).toMinutes();
            }
        }
        return summe;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AenderungInfo> historie(long zeiteintragId) {
        return aenderungsprotokollRepository.findByZeiteintrag_IdOrderByZeitpunkt(zeiteintragId).stream()
                .map(eintrag -> new AenderungInfo(eintrag.getZeitpunkt(), eintrag.getGeaendertVonId(),
                        eintrag.getArt(), eintrag.getFeld(), eintrag.getAlterWert(), eintrag.getNeuerWert(),
                        eintrag.getBegruendung()))
                .toList();
    }

    private BuchungInfo alsBuchungInfo(Zeiteintrag eintrag) {
        int nettoMinuten = 0;
        if (eintrag.getEnde() != null) {
            nettoMinuten = (int) zeiteintragService.nettoArbeitszeit(eintrag).toMinutes();
        }
        int pausenMinuten = (int) zeiteintragService.pausen(eintrag).toMinutes();
        return new BuchungInfo(eintrag.getId(), eintrag.getMitarbeiterId(), eintrag.getProjektId(),
                eintrag.getStandort(), eintrag.getBeginn(), eintrag.getEnde(),
                pausenMinuten, nettoMinuten, eintrag.isGeaendert());
    }

    private LocalDate tag(Instant zeitpunkt) {
        return LocalDate.ofInstant(zeitpunkt, clock.getZone());
    }

    private Instant monatsBeginn(YearMonth monat) {
        return tagesbeginn(monat.atDay(1));
    }

    private Instant tagesbeginn(LocalDate tag) {
        return tag.atStartOfDay(clock.getZone()).toInstant();
    }
}
