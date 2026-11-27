package de.timecraft.benutzer.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.benutzer.model.Arbeitszeitmodell;
import de.timecraft.benutzer.model.Mitarbeiter;
import de.timecraft.benutzer.model.Urlaubskonto;
import de.timecraft.benutzer.model.Zeitkonto;
import de.timecraft.benutzer.repository.ArbeitszeitmodellRepository;
import de.timecraft.benutzer.repository.MitarbeiterRepository;
import de.timecraft.benutzer.repository.UrlaubskontoRepository;
import de.timecraft.benutzer.repository.ZeitkontoRepository;
import de.timecraft.geruest.common.AktuelleSitzung;
import de.timecraft.geruest.common.NichtAngemeldetException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auskunft über Mitarbeiter für die anderen Module (Schnittstellenvertrag, FA-M1-14).
 * Liefert Stammdaten, Rolle, Vorgesetzten, Arbeitszeitmodell, Startsaldo und Urlaubsdaten.
 *
 * <p>Hier werden <b>keine Rechte</b> geprüft: Das macht laut Vertrag der aufrufende Service.
 * Alle Methoden lesen nur.
 *
 * @author Leon-Selim Kannenberg
 */
@Service
@Transactional(readOnly = true)
public class MitarbeiterAuskunftService implements MitarbeiterAuskunft {

    static final String NICHT_ANGEMELDET = "Bitte melden Sie sich an.";

    private final MitarbeiterRepository mitarbeiterRepository;
    private final ArbeitszeitmodellRepository arbeitszeitmodellRepository;
    private final ZeitkontoRepository zeitkontoRepository;
    private final UrlaubskontoRepository urlaubskontoRepository;
    private final AktuelleSitzung aktuelleSitzung;

    public MitarbeiterAuskunftService(MitarbeiterRepository mitarbeiterRepository,
            ArbeitszeitmodellRepository arbeitszeitmodellRepository, ZeitkontoRepository zeitkontoRepository,
            UrlaubskontoRepository urlaubskontoRepository, AktuelleSitzung aktuelleSitzung) {
        this.mitarbeiterRepository = mitarbeiterRepository;
        this.arbeitszeitmodellRepository = arbeitszeitmodellRepository;
        this.zeitkontoRepository = zeitkontoRepository;
        this.urlaubskontoRepository = urlaubskontoRepository;
        this.aktuelleSitzung = aktuelleSitzung;
    }

    /**
     * Die angemeldete Person aus dem Spring-Security-Kontext. Die Rolle kommt aus der Sitzung,
     * nicht aus der Datenbank: Eine geänderte Rolle gilt erst ab der nächsten Anmeldung (US-M1-05).
     */
    @Override
    public MitarbeiterInfo angemeldetePerson() {
        if (!aktuelleSitzung.istAngemeldet()) {
            throw new NichtAngemeldetException(NICHT_ANGEMELDET);
        }
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(aktuelleSitzung.mitarbeiterId());
        if (gefunden.isEmpty()) {
            throw new NichtAngemeldetException(NICHT_ANGEMELDET);
        }
        return infoErstellen(gefunden.get(), aktuelleSitzung.rolle());
    }

    /** Sucht einen Mitarbeiter, auch einen deaktivierten. */
    @Override
    public Optional<MitarbeiterInfo> finde(long mitarbeiterId) {
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(mitarbeiterId);
        if (gefunden.isEmpty()) {
            return Optional.empty();
        }
        Mitarbeiter mitarbeiter = gefunden.get();
        return Optional.of(infoErstellen(mitarbeiter, mitarbeiter.getBenutzer().getRolle()));
    }

    /** Nur aktive Mitarbeiter (für Auswahllisten), sortiert nach Personalnummer. */
    @Override
    public List<MitarbeiterInfo> aktive() {
        return infosErstellen(mitarbeiterRepository.findByAktivTrueOrderByPersonalnummerAsc());
    }

    /** Alle Mitarbeiter, auch ehemalige, sortiert nach Personalnummer. */
    @Override
    public List<MitarbeiterInfo> alle() {
        return infosErstellen(mitarbeiterRepository.findAllByOrderByPersonalnummerAsc());
    }

    /** Urlaubsanspruch und Übertrag für ein Jahr; leer, wenn es dafür keine Werte gibt. */
    @Override
    public Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr) {
        // Die Spalte jahr ist SMALLINT: Größere Zahlen können gar nicht gespeichert sein.
        if (jahr < Short.MIN_VALUE || jahr > Short.MAX_VALUE) {
            return Optional.empty();
        }
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(mitarbeiterId);
        if (gefunden.isEmpty()) {
            return Optional.empty();
        }
        Optional<Urlaubskonto> konto = urlaubskontoRepository.findByMitarbeiterAndJahr(gefunden.get(), (short) jahr);
        if (konto.isEmpty()) {
            return Optional.empty();
        }
        Urlaubskonto urlaubskonto = konto.get();
        return Optional.of(new UrlaubsDaten(urlaubskonto.getJahr(), urlaubskonto.getAnspruchTage(),
                urlaubskonto.getUebertragTage()));
    }

    /** Interner Stundensatz (FA-M1-K4); leer, wenn keiner eingetragen ist. Vertraulich, Aufrufer prüft. */
    @Override
    public Optional<BigDecimal> stundensatz(long mitarbeiterId) {
        Optional<Mitarbeiter> gefunden = mitarbeiterRepository.findById(mitarbeiterId);
        if (gefunden.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(gefunden.get().getStundensatz());
    }

    private List<MitarbeiterInfo> infosErstellen(List<Mitarbeiter> mitarbeiterListe) {
        List<MitarbeiterInfo> infos = new ArrayList<>();
        for (Mitarbeiter mitarbeiter : mitarbeiterListe) {
            infos.add(infoErstellen(mitarbeiter, mitarbeiter.getBenutzer().getRolle()));
        }
        return infos;
    }

    /**
     * Baut die lesende Sicht aus Mitarbeiter, Arbeitszeitmodell und Zeitkonto zusammen.
     * Fehlt ein Arbeitszeitmodell oder Zeitkonto (sollte nach UC-M1-04 nicht vorkommen),
     * bleiben die Werte leer bzw. 0.
     */
    private MitarbeiterInfo infoErstellen(Mitarbeiter mitarbeiter, Rolle rolle) {
        Long vorgesetzterId = null;
        if (mitarbeiter.getVorgesetzter() != null) {
            vorgesetzterId = mitarbeiter.getVorgesetzter().getMitarbeiterId();
        }

        BigDecimal wochenstunden = null;
        Set<DayOfWeek> arbeitstage = new HashSet<>();
        Optional<Arbeitszeitmodell> modell = arbeitszeitmodellRepository.findByMitarbeiter(mitarbeiter);
        if (modell.isPresent()) {
            wochenstunden = modell.get().getWochenstunden();
            arbeitstage = modell.get().getArbeitstage();
        }

        LocalDate zeitkontoStartdatum = null;
        int startsaldoMinuten = 0;
        Optional<Zeitkonto> zeitkonto = zeitkontoRepository.findByMitarbeiter(mitarbeiter);
        if (zeitkonto.isPresent()) {
            zeitkontoStartdatum = zeitkonto.get().getStartdatum();
            startsaldoMinuten = zeitkonto.get().getAnfangssaldoMin();
        }

        return new MitarbeiterInfo(mitarbeiter.getMitarbeiterId(), mitarbeiter.getPersonalnummer(),
                mitarbeiter.getVorname(), mitarbeiter.getNachname(), rolle, mitarbeiter.isAktiv(),
                vorgesetzterId, mitarbeiter.getGeburtsdatum(), wochenstunden, Set.copyOf(arbeitstage),
                zeitkontoStartdatum, startsaldoMinuten);
    }
}
