package de.timecraft.zeiterfassung.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.zeiterfassung.api.ZeitkontoAuskunft;
import de.timecraft.zeiterfassung.api.ZeitkontoInfo;
import de.timecraft.zeiterfassung.model.ZeitkontoMonat;
import de.timecraft.zeiterfassung.model.ZeitkontoZeile;
import de.timecraft.zeiterfassung.model.Zeitkontouebersicht;

/**
 * Soll, Ist und Saldo je Monat. Arbeitszeitmodell, Startsaldo und Startdatum kommen aus M1.
 *
 * @author Noah Cipa
 */
@Service
public class ZeitkontoService implements ZeitkontoAuskunft {

    private final ZeiteintragService zeiteintragService;
    private final AbwesenheitService abwesenheitService;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final ZugriffsPruefung zugriffsPruefung;
    private final Clock clock;

    public ZeitkontoService(ZeiteintragService zeiteintragService, AbwesenheitService abwesenheitService,
            MitarbeiterAuskunft mitarbeiterAuskunft, ZugriffsPruefung zugriffsPruefung, Clock clock) {
        this.zeiteintragService = zeiteintragService;
        this.abwesenheitService = abwesenheitService;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.zugriffsPruefung = zugriffsPruefung;
        this.clock = clock;
    }

    public YearMonth aktuellerMonat() {
        return YearMonth.now(clock);
    }

    /** Eigenes Soll der laufenden Woche fuer die Startseite. */
    @Transactional(readOnly = true)
    public int wochenSollMinuten(long angemeldeteId, Rolle rolle) {
        zugriffsPruefung.pruefeZeiterfassung(rolle);
        LocalDate montag = LocalDate.now(clock).with(DayOfWeek.MONDAY);
        return sollZwischen(mitarbeiter(angemeldeteId), montag, montag.plusDays(6));
    }

    /**
     * Eigenes Zeitkonto oder als Projektleitung das der Mitarbeiter ihrer Projekte (FA-M2-17). Die Oberflaeche
     * rechnet den laufenden Monat bis gestern (Mockup).
     */
    @Transactional(readOnly = true)
    public ZeitkontoMonat monat(long angemeldeteId, Rolle rolle, long mitarbeiterId, YearMonth monat) {
        zugriffsPruefung.pruefeLesen(angemeldeteId, rolle, mitarbeiterId);
        if (istZukunft(monat)) {
            throw new FachlicheException("Für zukünftige Monate gibt es noch kein Zeitkonto.");
        }
        return berechne(mitarbeiter(mitarbeiterId), monat, LocalDate.now(clock).minusDays(1));
    }

    /**
     * Monate des laufenden Jahres mit dem Saldo davor (Startsaldo aus M1 samt Vorjahren).
     * Leserecht wie bei {@link #monat(long, Rolle, long, YearMonth)}.
     */
    @Transactional(readOnly = true)
    public Zeitkontouebersicht jahresuebersicht(long angemeldeteId, Rolle rolle, long mitarbeiterId) {
        zugriffsPruefung.pruefeLesen(angemeldeteId, rolle, mitarbeiterId);
        MitarbeiterInfo mitarbeiter = mitarbeiter(mitarbeiterId);
        LocalDate heute = LocalDate.now(clock);
        LocalDate gestern = heute.minusDays(1);
        LocalDate kontoBeginn = kontoBeginn(mitarbeiter);
        LocalDate jahresAnfang = LocalDate.of(heute.getYear(), 1, 1);

        int anfangssaldo = mitarbeiter.startsaldoMinuten();
        YearMonth ersterMonat = YearMonth.from(jahresAnfang);
        if (kontoBeginn.isBefore(jahresAnfang)) {
            LocalDate vorjahresEnde = jahresAnfang.minusDays(1);
            anfangssaldo = anfangssaldo + istZwischen(mitarbeiterId, kontoBeginn, vorjahresEnde)
                    - sollZwischen(mitarbeiter, kontoBeginn, vorjahresEnde);
        } else if (!kontoBeginn.isAfter(heute)) {
            ersterMonat = YearMonth.from(kontoBeginn);
        } else {
            // Konto beginnt erst in der Zukunft: laufender Monat mit Soll 0, Ist 0 und Startsaldo
            ersterMonat = YearMonth.from(heute);
        }

        int gesamtsaldo = anfangssaldo;
        List<ZeitkontoZeile> zeilen = new ArrayList<>();
        for (YearMonth monat = ersterMonat; !monat.isAfter(YearMonth.from(heute)); monat = monat.plusMonths(1)) {
            LocalDate von = spaeteresDatum(monat.atDay(1), kontoBeginn);
            LocalDate bis = frueheresDatum(monat.atEndOfMonth(), gestern);
            int soll = sollZwischen(mitarbeiter, von, bis);
            int ist = istZwischen(mitarbeiterId, von, bis);
            gesamtsaldo = gesamtsaldo + ist - soll;
            ZeitkontoMonat konto = new ZeitkontoMonat(monat, soll, ist, ist - soll, gesamtsaldo);
            boolean laufend = monat.equals(YearMonth.from(heute));
            LocalDate ab = monat.equals(YearMonth.from(kontoBeginn)) ? kontoBeginn : null;
            LocalDate gerechnetBis = laufend && !von.isAfter(bis) ? bis : null;
            zeilen.add(new ZeitkontoZeile(konto, laufend, ab, gerechnetBis));
        }
        LocalDate anfangssaldoDatum = kontoBeginn.getYear() == heute.getYear() ? kontoBeginn : jahresAnfang;
        return new Zeitkontouebersicht(anfangssaldoDatum, anfangssaldo, zeilen);
    }

    /** Nach dem Schnittstellenvertrag rechnet der laufende Monat bis einschliesslich heute. */
    @Override
    @Transactional(readOnly = true)
    public Optional<ZeitkontoInfo> monat(long mitarbeiterId, YearMonth monat) {
        Optional<MitarbeiterInfo> mitarbeiter = mitarbeiterAuskunft.finde(mitarbeiterId);
        if (mitarbeiter.isEmpty() || istZukunft(monat)) {
            return Optional.empty();
        }
        ZeitkontoMonat konto = berechne(mitarbeiter.get(), monat, LocalDate.now(clock));
        return Optional.of(new ZeitkontoInfo(mitarbeiterId, monat, konto.sollMinuten(), konto.istMinuten(),
                konto.saldoMonatMinuten(), konto.gesamtsaldoMinuten()));
    }

    /** Wochenstunden geteilt durch die Zahl der Arbeitstage, auf ganze Minuten gerundet (FA-M2-11). */
    private int tagesSollMinuten(MitarbeiterInfo mitarbeiter) {
        int arbeitstage = mitarbeiter.arbeitstage().size();
        if (arbeitstage == 0 || mitarbeiter.wochenstunden() == null) {
            return 0;
        }
        return mitarbeiter.wochenstunden().multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(arbeitstage), 0, RoundingMode.HALF_UP).intValue();
    }

    /** Startdatum des Zeitkontos aus M1. Fehlt es, beginnt das Konto heute (noch kein Saldo). */
    private LocalDate kontoBeginn(MitarbeiterInfo mitarbeiter) {
        if (mitarbeiter.zeitkontoStartdatum() == null) {
            return LocalDate.now(clock);
        }
        return mitarbeiter.zeitkontoStartdatum();
    }

    private boolean istZukunft(YearMonth monat) {
        return monat.atDay(1).isAfter(LocalDate.now(clock));
    }

    private ZeitkontoMonat berechne(MitarbeiterInfo mitarbeiter, YearMonth monat, LocalDate stichtag) {
        long mitarbeiterId = mitarbeiter.mitarbeiterId();
        LocalDate kontoBeginn = kontoBeginn(mitarbeiter);
        LocalDate von = spaeteresDatum(monat.atDay(1), kontoBeginn);
        LocalDate bis = frueheresDatum(monat.atEndOfMonth(), stichtag);

        int sollMonat = sollZwischen(mitarbeiter, von, bis);
        int istMonat = istZwischen(mitarbeiterId, von, bis);
        int sollGesamt = sollZwischen(mitarbeiter, kontoBeginn, bis);
        int istGesamt = istZwischen(mitarbeiterId, kontoBeginn, bis);
        return new ZeitkontoMonat(monat, sollMonat, istMonat, istMonat - sollMonat,
                mitarbeiter.startsaldoMinuten() + istGesamt - sollGesamt);
    }

    private LocalDate spaeteresDatum(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate frueheresDatum(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** Feiertage, arbeitsfreie Tage und Tage mit genehmigter Abwesenheit haben kein offenes Soll (FA-M2-16). */
    private int sollZwischen(MitarbeiterInfo mitarbeiter, LocalDate von, LocalDate bis) {
        return abwesenheitService.anzahlSollTage(mitarbeiter, von, bis) * tagesSollMinuten(mitarbeiter);
    }

    private int istZwischen(long mitarbeiterId, LocalDate von, LocalDate bis) {
        if (von.isAfter(bis)) {
            return 0;
        }
        return zeiteintragService.istMinuten(mitarbeiterId, von, bis);
    }

    private MitarbeiterInfo mitarbeiter(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId)
                .orElseThrow(() -> new NichtGefundenException("Der Mitarbeiter wurde nicht gefunden."));
    }
}
