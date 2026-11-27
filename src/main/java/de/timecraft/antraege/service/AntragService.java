package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Antragstyp;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Antraege erstellen, ansehen, bearbeiten und zurueckziehen (FA-M5-01 bis FA-M5-11, FA-M5-19).
 * Die Person kommt als Parameter von der View; Rechte und Regeln prueft dieser Service.
 * Antraege anderer Module (Materialanpassung aus M4) zeigt und bearbeitet M5 nicht.
 */
@Service
public class AntragService {

    private final AntragRepository repository;
    private final AntragRegelnService regeln;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final BuchungsAuskunft buchungsAuskunft;
    private final AbwesenheitsUebernahme abwesenheitsUebernahme;
    private final ProjektAuskunft projektAuskunft;
    private final Clock clock;

    public AntragService(AntragRepository repository, AntragRegelnService regeln,
            MitarbeiterAuskunft mitarbeiterAuskunft, BuchungsAuskunft buchungsAuskunft,
            AbwesenheitsUebernahme abwesenheitsUebernahme, ProjektAuskunft projektAuskunft, Clock clock) {
        this.repository = repository;
        this.regeln = regeln;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.buchungsAuskunft = buchungsAuskunft;
        this.abwesenheitsUebernahme = abwesenheitsUebernahme;
        this.projektAuskunft = projektAuskunft;
        this.clock = clock;
    }

    // ---------- Erstellen (UC-M5-02) ----------

    @Transactional
    public Abwesenheitsantrag abwesenheitBeantragen(long mitarbeiterId, Rolle rolle, AbwesenheitsDaten daten) {
        AntragRechte.pruefeAntragsteller(rolle);
        Abwesenheitsantrag antrag = new Abwesenheitsantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(clock.instant());
        uebernehmen(antrag, daten);
        regeln.pruefe(antrag);
        return repository.save(antrag);
    }

    /** Die alten Werte liest das System aus M2 (FA-M5-03). */
    @Transactional
    public Zeitkorrekturantrag zeitkorrekturBeantragen(long mitarbeiterId, Rolle rolle, ZeitkorrekturDaten daten) {
        AntragRechte.pruefeAntragsteller(rolle);
        BuchungInfo buchung = regeln.korrigierbareBuchung(mitarbeiterId, daten.zeiteintragId());
        Zeitkorrekturantrag antrag = new Zeitkorrekturantrag();
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(clock.instant());
        antrag.setZeiteintragId(buchung.zeiteintragId());
        antrag.setAlterBeginn(buchung.beginn());
        antrag.setAltesEnde(buchung.ende());
        antrag.setAlterStandort(buchung.standort());
        uebernehmen(antrag, daten);
        regeln.pruefe(antrag);
        return repository.save(antrag);
    }

    // ---------- Bearbeiten und Zurueckziehen (UC-M5-05, UC-M5-06) ----------

    @Transactional
    public Abwesenheitsantrag abwesenheitBearbeiten(long mitarbeiterId, Rolle rolle, long antragId,
            AbwesenheitsDaten daten) {
        AntragRechte.pruefeAntragsteller(rolle);
        if (!(eigenerOffenerAntrag(mitarbeiterId, antragId) instanceof Abwesenheitsantrag antrag)) {
            throw new FachlicheException("Dieser Antrag ist keine Abwesenheit.");
        }
        uebernehmen(antrag, daten);
        regeln.pruefe(antrag);
        return antrag;
    }

    @Transactional
    public Zeitkorrekturantrag zeitkorrekturBearbeiten(long mitarbeiterId, Rolle rolle, long antragId,
            ZeitkorrekturDaten daten) {
        AntragRechte.pruefeAntragsteller(rolle);
        if (!(eigenerOffenerAntrag(mitarbeiterId, antragId) instanceof Zeitkorrekturantrag antrag)) {
            throw new FachlicheException("Dieser Antrag ist keine Zeitkorrektur.");
        }
        if (!Objects.equals(daten.zeiteintragId(), antrag.getZeiteintragId())) {
            throw new FachlicheException("Die Buchung eines Antrags kann nicht gewechselt werden. "
                    + "Bitte stellen Sie dafür einen neuen Antrag.");
        }
        uebernehmen(antrag, daten);
        regeln.pruefe(antrag);
        return antrag;
    }

    /** Rueckzug aendert keine Fachdaten; der Antrag bleibt mit Status ZURUECKGEZOGEN erhalten (US-M5-07). */
    @Transactional
    public void zurueckziehen(long mitarbeiterId, Rolle rolle, long antragId) {
        AntragRechte.pruefeAntragsteller(rolle);
        eigenerOffenerAntrag(mitarbeiterId, antragId).setStatus(Antragsstatus.ZURUECKGEZOGEN);
    }

    // ---------- Ansehen (UC-M5-01, UC-M5-07, UC-M5-11) ----------

    /** Eigene Antraege mit Status, Entscheidung und Hinweis, neueste zuerst (FA-M5-08, FA-M5-18). */
    @Transactional(readOnly = true)
    public List<AntragAnsicht> eigeneAntraege(long mitarbeiterId, Rolle rolle) {
        AntragRechte.pruefeAntragsteller(rolle);
        return repository.findByAntragstellerIdOrderByEingereichtAmDesc(mitarbeiterId).stream()
                .filter(antrag -> Antragstyp.von(antrag) != null)
                .map(antrag -> ansicht(antrag, false))
                .toList();
    }

    /** Antraege aller Personen, deren Vorgesetzte die Projektleitung ist, gefiltert (FA-M5-10, US-M5-05). */
    @Transactional(readOnly = true)
    public List<AntragAnsicht> zustaendigeAntraege(long projektleitungId, Rolle rolle, AntragFilter filter) {
        AntragRechte.pruefeProjektleitung(rolle);
        List<Long> team = teamVon(projektleitungId).stream().map(MitarbeiterInfo::mitarbeiterId).toList();
        if (team.isEmpty()) {
            return List.of();
        }
        return repository.findByAntragstellerIdInOrderByEingereichtAmDesc(team).stream()
                .filter(antrag -> Antragstyp.von(antrag) != null && passt(antrag, filter))
                .map(antrag -> ansicht(antrag, true))
                .toList();
    }

    /** Ein Antrag mit allen Pruefdaten; nur fuer den Eigentuemer oder die zustaendige Projektleitung. */
    @Transactional(readOnly = true)
    public AntragAnsicht antrag(long mitarbeiterId, Rolle rolle, long antragId) {
        AntragRechte.pruefeAntragsteller(rolle);
        Antrag antrag = repository.findById(antragId)
                .filter(gefunden -> Antragstyp.von(gefunden) != null)
                .orElseThrow(() -> new NichtGefundenException("Der Antrag wurde nicht gefunden."));
        if (antrag.getAntragstellerId() == mitarbeiterId) {
            return ansicht(antrag, false);
        }
        if (rolle == Rolle.PROJEKTLEITUNG && regeln.istZustaendig(mitarbeiterId, antrag.getAntragstellerId())) {
            return ansicht(antrag, true);
        }
        throw new ZugriffVerweigertException("Sie haben keinen Zugriff auf diesen Antrag.");
    }

    // ---------- Auswahllisten fuer das Formular ----------

    /** Personen, fuer deren Antraege die Projektleitung zustaendig ist (Filter "Mitarbeiter"). */
    public List<MitarbeiterInfo> zugeordneteMitarbeiter(long projektleitungId, Rolle rolle) {
        AntragRechte.pruefeProjektleitung(rolle);
        return teamVon(projektleitungId);
    }

    /** Eigene abgeschlossene Buchungen, die den Tag beruehren (Auswahl fuer die Zeitkorrektur). */
    public List<BuchungInfo> buchungenAmTag(long mitarbeiterId, Rolle rolle, LocalDate tag) {
        AntragRechte.pruefeAntragsteller(rolle);
        if (tag == null) {
            return List.of();
        }
        return buchungsAuskunft.buchungen(mitarbeiterId, tag.atStartOfDay(clock.getZone()).toInstant(),
                tag.plusDays(1).atStartOfDay(clock.getZone()).toInstant()).stream()
                .filter(buchung -> buchung.mitarbeiterId() == mitarbeiterId && !buchung.laeuft())
                .sorted(Comparator.comparing(BuchungInfo::beginn))
                .toList();
    }

    public List<ProjektInfo> aktiveProjekte(long mitarbeiterId, Rolle rolle) {
        AntragRechte.pruefeAntragsteller(rolle);
        return projektAuskunft.aktiveProjekteVon(mitarbeiterId);
    }

    /**
     * Urlaubstage und verbleibender Resturlaub fuer das Formular. antragId (beim Bearbeiten) und
     * bezugsantragId (bei einer Aenderung) werden beim Resturlaub nicht abgezogen. Null, solange
     * der Zeitraum unvollstaendig oder verkehrt ist.
     */
    @Transactional(readOnly = true)
    public Urlaubsvorschau urlaubsvorschau(long mitarbeiterId, Rolle rolle, Urlaubsart urlaubsart, LocalDate von,
            LocalDate bis, Long antragId, Long bezugsantragId) {
        AntragRechte.pruefeAntragsteller(rolle);
        if (von == null || bis == null || bis.isBefore(von)) {
            return null;
        }
        int tage = abwesenheitsUebernahme.urlaubstage(mitarbeiterId, von, bis);
        if (urlaubsart == Urlaubsart.UNBEZAHLTER_URLAUB) {
            return new Urlaubsvorschau(tage, null);
        }
        int jahr = von.getYear();
        int rest = regeln.resturlaub(mitarbeiterId, jahr, AntragRegelnService.ohneLeere(antragId, bezugsantragId))
                - regeln.urlaubstageImJahr(mitarbeiterId, von, bis, jahr);
        return new Urlaubsvorschau(tage, rest);
    }

    // ---------- intern ----------

    /** Liest und sperrt einen eigenen offenen Antrag (US-M5-07: nur eigene offene sind aenderbar). */
    private Antrag eigenerOffenerAntrag(long mitarbeiterId, long antragId) {
        Antrag antrag = repository.findeZumAendern(antragId)
                .filter(gefunden -> Antragstyp.von(gefunden) != null)
                .orElseThrow(() -> new NichtGefundenException("Der Antrag wurde nicht gefunden."));
        if (antrag.getAntragstellerId() != mitarbeiterId) {
            throw new ZugriffVerweigertException("Sie können nur eigene Anträge bearbeiten oder zurückziehen.");
        }
        if (antrag.getStatus() != Antragsstatus.OFFEN) {
            throw new FachlicheException("Nur offene Anträge können bearbeitet oder zurückgezogen werden.");
        }
        return antrag;
    }

    private static void uebernehmen(Abwesenheitsantrag antrag, AbwesenheitsDaten daten) {
        antrag.setArt(daten.art());
        antrag.setUrlaubsart(daten.urlaubsart());
        antrag.setVon(daten.von());
        antrag.setBis(daten.bis());
        antrag.setBegruendung(bereinigt(daten.begruendung()));
    }

    private static void uebernehmen(Zeitkorrekturantrag antrag, ZeitkorrekturDaten daten) {
        antrag.setNeuerBeginn(daten.neuerBeginn());
        antrag.setNeuesEnde(daten.neuesEnde());
        antrag.setNeuerStandort(daten.neuerStandort());
        antrag.setNeuesProjektId(daten.neuesProjektId());
        antrag.setBegruendung(bereinigt(daten.begruendung()));
    }

    private static String bereinigt(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private List<MitarbeiterInfo> teamVon(long projektleitungId) {
        return mitarbeiterAuskunft.alle().stream()
                .filter(person -> Objects.equals(person.vorgesetzterId(), projektleitungId))
                .sorted(Comparator.comparing(MitarbeiterInfo::nachname).thenComparing(MitarbeiterInfo::vorname))
                .toList();
    }

    /** Der Zeitraum-Filter trifft, wenn sich der Antragszeitraum mit [von, bis] ueberschneidet (Alper Sayar, 27.11.2026). */
    private boolean passt(Antrag antrag, AntragFilter filter) {
        if ((filter.typ() != null && filter.typ() != Antragstyp.von(antrag))
                || (filter.status() != null && filter.status() != antrag.getStatus())
                || (filter.mitarbeiterId() != null && filter.mitarbeiterId() != antrag.getAntragstellerId())) {
            return false;
        }
        if (filter.von() == null && filter.bis() == null) {
            return true;
        }
        LocalDate[] zeitraum = antragszeitraum(antrag);
        return zeitraum != null
                && (filter.von() == null || !zeitraum[1].isBefore(filter.von()))
                && (filter.bis() == null || !zeitraum[0].isAfter(filter.bis()));
    }

    /** Abwesenheit: von bis bis; Zeitkorrektur: Tag der Buchung; Storno: Zeitraum der stornierten Abwesenheit. */
    private LocalDate[] antragszeitraum(Antrag antrag) {
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            return new LocalDate[] {abwesenheit.getVon(), abwesenheit.getBis()};
        }
        if (antrag instanceof Zeitkorrekturantrag korrektur) {
            LocalDate tag = LocalDate.ofInstant(korrektur.getAlterBeginn(), clock.getZone());
            return new LocalDate[] {tag, tag};
        }
        if (antrag.getBezugsantragId() != null) {
            return repository.findById(antrag.getBezugsantragId()).map(this::antragszeitraum).orElse(null);
        }
        return null;
    }

    private AntragAnsicht ansicht(Antrag antrag, boolean fuerProjektleitung) {
        Antrag bezug = antrag.getBezugsantragId() == null ? null
                : repository.findById(antrag.getBezugsantragId()).orElse(null);
        BuchungInfo buchung = null;
        String bisherigesProjekt = null;
        String gewuenschtesProjekt = null;
        Integer netto = null;
        Integer urlaubstage = null;
        Integer abwesend = null;
        Integer teamgroesse = null;
        boolean sondergenehmigung = false;
        if (antrag instanceof Zeitkorrekturantrag korrektur) {
            buchung = buchungsAuskunft.buchung(korrektur.getZeiteintragId()).orElse(null);
            bisherigesProjekt = buchung == null ? null : projektname(buchung.projektId());
            gewuenschtesProjekt = projektname(korrektur.getNeuesProjektId());
            int pause = buchung == null ? 0 : buchung.pausenMinuten();
            netto = (int) Duration.between(korrektur.getNeuerBeginn(), korrektur.getNeuesEnde()).toMinutes() - pause;
        }
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            if (Antragstyp.von(abwesenheit) == Antragstyp.URLAUB) {
                urlaubstage = abwesenheitsUebernahme.urlaubstage(abwesenheit.getAntragstellerId(),
                        abwesenheit.getVon(), abwesenheit.getBis());
                sondergenehmigung = abwesenheit.getUrlaubsart() == Urlaubsart.UNBEZAHLTER_URLAUB;
            }
            if (fuerProjektleitung) {
                List<MitarbeiterInfo> kollegen = kollegenVon(abwesenheit.getAntragstellerId());
                teamgroesse = kollegen.size();
                abwesend = (int) kollegen.stream().filter(kollege -> abwesenheitsUebernahme.ueberschneidetAbwesenheit(
                        kollege.mitarbeiterId(), abwesenheit.getVon(), abwesenheit.getBis())).count();
            }
        }
        return new AntragAnsicht(antrag, Antragstyp.von(antrag), name(antrag.getAntragstellerId()),
                antrag.getEntscheiderId() == null ? null : name(antrag.getEntscheiderId()), bezug, buchung,
                bisherigesProjekt, gewuenschtesProjekt, netto, urlaubstage, abwesend, teamgroesse, sondergenehmigung);
    }

    /** Aktive Personen mit demselben Vorgesetzten, ohne die Person selbst. */
    private List<MitarbeiterInfo> kollegenVon(long mitarbeiterId) {
        Long vorgesetzter = mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::vorgesetzterId).orElse(null);
        return mitarbeiterAuskunft.aktive().stream()
                .filter(person -> person.mitarbeiterId() != mitarbeiterId)
                .filter(person -> vorgesetzter != null && vorgesetzter.equals(person.vorgesetzterId()))
                .toList();
    }

    private String name(long mitarbeiterId) {
        return mitarbeiterAuskunft.finde(mitarbeiterId).map(MitarbeiterInfo::name).orElse("Unbekannt");
    }

    private String projektname(Long projektId) {
        if (projektId == null) {
            return "ohne Projekt";
        }
        return projektAuskunft.finde(projektId).map(ProjektInfo::name).orElse("Projekt " + projektId);
    }
}
