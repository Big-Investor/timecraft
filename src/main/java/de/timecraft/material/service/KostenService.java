package de.timecraft.material.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.material.model.Kostendeckung;
import de.timecraft.material.model.Kostenuebersicht;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.projekte.api.AngebotspreisPflege;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import de.timecraft.zeiterfassung.api.ZeitSegment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kostenaggregation und Unterdeckungswarnung (FA-M4-07, FA-M4-08) sowie Pflege des Angebotspreises.
 * Personalkosten: je Person anrechenbare Minuten aller Monate vom Projektstart bis heute x Stundensatz / 60.
 * Die fremden Schnittstellen werden {@code @Lazy} injiziert, weil es M1, M2 und M3 im M4-Branch noch nicht gibt.
 */
@Service
public class KostenService {

    private static final BigDecimal MINUTEN_JE_STUNDE = BigDecimal.valueOf(60);

    private final MaterialpositionRepository positionRepository;
    private final ProjektZugriff projektZugriff;
    private final BuchungsAuskunft buchungsAuskunft;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final AngebotspreisPflege angebotspreisPflege;
    private final Clock clock;

    public KostenService(MaterialpositionRepository positionRepository, ProjektZugriff projektZugriff,
            @Lazy BuchungsAuskunft buchungsAuskunft, @Lazy MitarbeiterAuskunft mitarbeiterAuskunft,
            @Lazy AngebotspreisPflege angebotspreisPflege, Clock clock) {
        this.positionRepository = positionRepository;
        this.projektZugriff = projektZugriff;
        this.buchungsAuskunft = buchungsAuskunft;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.angebotspreisPflege = angebotspreisPflege;
        this.clock = clock;
    }

    /** Kostenuebersicht: verantwortliche Projektleitung und Admin. */
    @Transactional(readOnly = true)
    public Kostenuebersicht kosten(long mitarbeiterId, Rolle rolle, long projektId) {
        ProjektInfo projekt = projektZugriff.pruefeKostensicht(mitarbeiterId, rolle, projektId);
        BigDecimal material = Vorkalkulation.berechne(positionRepository.findByProjektIdOrderByIdAsc(projektId))
                .summeKosten();
        Map<Long, Integer> minutenJePerson = minutenJePerson(projekt);
        BigDecimal personal = BigDecimal.ZERO;
        List<String> ohneStundensatz = new ArrayList<>();
        for (Map.Entry<Long, Integer> eintrag : minutenJePerson.entrySet()) {
            BigDecimal stundensatz = mitarbeiterAuskunft.stundensatz(eintrag.getKey()).orElse(null);
            if (stundensatz == null) {
                ohneStundensatz.add(name(eintrag.getKey()));
            } else {
                personal = personal.add(BigDecimal.valueOf(eintrag.getValue()).multiply(stundensatz)
                        .divide(MINUTEN_JE_STUNDE, 2, RoundingMode.HALF_UP));
            }
        }
        personal = Betraege.runden(personal);
        int minuten = minutenJePerson.values().stream().mapToInt(Integer::intValue).sum();
        return uebersicht(material, personal, minuten, projekt.angebotspreis(), ohneStundensatz);
    }

    /** Angebotspreis setzen: nur die verantwortliche Projektleitung eines aktiven Projekts. */
    @Transactional
    public void setzeAngebotspreis(long mitarbeiterId, Rolle rolle, long projektId, BigDecimal angebotspreis) {
        ProjektZugriff.pruefeAktiv(projektZugriff.pruefeVerantwortlich(mitarbeiterId, rolle, projektId));
        if (angebotspreis == null) {
            throw new FachlicheException("Bitte geben Sie den Angebotspreis ein.");
        }
        BigDecimal gerundet = Betraege.runden(angebotspreis);
        if (gerundet.signum() < 0) {
            throw new FachlicheException("Bitte geben Sie den Angebotspreis ohne Minuszeichen ein.");
        }
        if (gerundet.compareTo(Betraege.HOECHSTWERT) > 0) {
            throw new FachlicheException("Bitte geben Sie den Angebotspreis kleiner als 100.000.000 € ein.");
        }
        angebotspreisPflege.setzeAngebotspreis(projektId, gerundet);
    }

    private static Kostenuebersicht uebersicht(BigDecimal material, BigDecimal personal, int minuten,
            BigDecimal angebotspreis, List<String> ohneStundensatz) {
        BigDecimal gesamt = material.add(personal);
        if (angebotspreis == null) {
            return new Kostenuebersicht(material, personal, minuten, gesamt, null, null,
                    Kostendeckung.ANGEBOTSPREIS_FEHLT, ohneStundensatz);
        }
        BigDecimal differenz = angebotspreis.subtract(gesamt);
        Kostendeckung deckung = differenz.signum() < 0 ? Kostendeckung.UNTERDECKUNG : Kostendeckung.GEDECKT;
        return new Kostenuebersicht(material, personal, minuten, gesamt, angebotspreis, differenz, deckung,
                ohneStundensatz);
    }

    /** Sortiert nach Mitarbeiter-ID, damit die Rechnung immer gleich laeuft. */
    private Map<Long, Integer> minutenJePerson(ProjektInfo projekt) {
        Map<Long, Integer> minuten = new TreeMap<>();
        YearMonth bis = YearMonth.now(clock);
        YearMonth monat = projekt.startdatum() == null ? bis : YearMonth.from(projekt.startdatum());
        for (; !monat.isAfter(bis); monat = monat.plusMonths(1)) {
            for (ZeitSegment segment : buchungsAuskunft.segmenteProjekt(projekt.projektId(), monat)) {
                minuten.merge(segment.mitarbeiterId(), segment.anrechenbareMinuten(), Integer::sum);
            }
        }
        return minuten;
    }

    private String name(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::name)
                .orElse("Mitarbeiter " + mitarbeiterId);
    }
}
