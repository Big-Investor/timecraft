package de.timecraft.material.service;

import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.material.model.Materialanpassungsantrag;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.model.Protokollaktion;
import de.timecraft.material.repository.AnpassungsantragRepository;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.material.repository.MaterialprotokollRepository;
import de.timecraft.projekte.api.ProjektInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mehrbedarf (Material-Anpassungsantrag) einreichen, zurueckziehen und entscheiden (FA-M4-04 bis FA-M4-06).
 * Stellt die verantwortliche Projektleitung selbst den Antrag, ist er sofort genehmigt: So hat jede
 * Mengenaenderung einen Antrag mit Begruendung.
 */
@Service
public class AnpassungsantragService {

    /** Spalte begruendung hat 255 Zeichen; davon braucht der laengste Grund "Kundenänderung: " 16. */
    static final int MAX_FREITEXT = 239;
    /** Spalte entscheidungshinweis. */
    static final int MAX_KOMMENTAR = 255;

    private final AnpassungsantragRepository antragRepository;
    private final MaterialpositionRepository positionRepository;
    private final MaterialprotokollRepository protokollRepository;
    private final ProjektZugriff projektZugriff;
    private final Personennamen personennamen;
    private final Clock clock;

    public AnpassungsantragService(AnpassungsantragRepository antragRepository,
            MaterialpositionRepository positionRepository, MaterialprotokollRepository protokollRepository,
            ProjektZugriff projektZugriff, Personennamen personennamen, Clock clock) {
        this.antragRepository = antragRepository;
        this.positionRepository = positionRepository;
        this.protokollRepository = protokollRepository;
        this.projektZugriff = projektZugriff;
        this.personennamen = personennamen;
        this.clock = clock;
    }

    /** Geplante Materialien des Projekts ohne Preise, als Auswahl fuer den Mehrbedarf. */
    @Transactional(readOnly = true)
    public List<PlanungOhnePreis> geplanteMaterialien(long mitarbeiterId, Rolle rolle, long projektId) {
        projektZugriff.pruefeTeilnahme(mitarbeiterId, rolle, projektId);
        return positionRepository.findByProjektIdOrderByIdAsc(projektId).stream()
                .map(p -> new PlanungOhnePreis(p.getId(), p.getMaterial().getBezeichnung(),
                        p.getMaterial().getEinheit(), p.getSollMenge()))
                .toList();
    }

    @Transactional
    public Materialanpassungsantrag einreichen(long mitarbeiterId, Rolle rolle, long positionId,
            BigDecimal zusatzmenge, Mehrbedarfsgrund grund, String text) {
        Rechte.pruefeAngemeldet(rolle);
        Materialposition position = position(positionId);
        ProjektInfo projekt = projektZugriff.pruefeTeilnahme(mitarbeiterId, rolle, position.getProjektId());
        ProjektZugriff.pruefeAktiv(projekt);
        BigDecimal zusatz = Mengen.pruefe(zusatzmenge, "die zusätzliche Menge");
        String begruendung = begruendung(grund, text);
        Instant jetzt = clock.instant();
        Materialanpassungsantrag antrag = new Materialanpassungsantrag(position, zusatz);
        antrag.setAntragstellerId(mitarbeiterId);
        antrag.setEingereichtAm(jetzt);
        antrag.setBegruendung(begruendung);
        if (ProjektZugriff.istVerantwortlich(mitarbeiterId, rolle, projekt)) {
            genehmigen(antrag, mitarbeiterId, jetzt);
        }
        return antragRepository.save(antrag);
    }

    /** Eigenen offenen Antrag zurueckziehen. */
    @Transactional
    public void zurueckziehen(long mitarbeiterId, Rolle rolle, long antragId) {
        Rechte.pruefeAngemeldet(rolle);
        Materialanpassungsantrag antrag = gesperrt(antragId);
        if (antrag.getAntragstellerId() != mitarbeiterId) {
            throw new ZugriffVerweigertException("Sie können nur Ihre eigenen Anträge zurückziehen.");
        }
        pruefeOffen(antrag);
        antrag.setStatus(Antragsstatus.ZURUECKGEZOGEN);
    }

    /** Offene Antraege eines Projekts fuer die verantwortliche Projektleitung (Admin darf lesen). */
    @Transactional(readOnly = true)
    public List<AntragZurPruefung> offeneAntraege(long mitarbeiterId, Rolle rolle, long projektId) {
        projektZugriff.pruefeKostensicht(mitarbeiterId, rolle, projektId);
        return antragRepository.findByMaterialpositionProjektIdAndStatusOrderByEingereichtAmAsc(projektId,
                Antragsstatus.OFFEN).stream()
                .map(a -> AntragZurPruefung.aus(a, personennamen.name(a.getAntragstellerId()))).toList();
    }

    /** Eigene Antraege, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<MeinMehrbedarf> meineAntraege(long mitarbeiterId, Rolle rolle) {
        Rechte.pruefeAngemeldet(rolle);
        return antragRepository.findByAntragstellerIdOrderByEingereichtAmDescIdDesc(mitarbeiterId).stream()
                .map(a -> MeinMehrbedarf.aus(a, projektZugriff.projektname(a.getMaterialposition().getProjektId()),
                        personennamen.name(a.getEntscheiderId())))
                .toList();
    }

    /** Aktive Projekte zur Auswahl beim Mehrbedarf, ohne Preise. */
    @Transactional(readOnly = true)
    public List<Projektauswahl> projekteFuerMehrbedarf(long mitarbeiterId, Rolle rolle) {
        return projektZugriff.projekteFuerMehrbedarf(mitarbeiterId, rolle).stream()
                .map(p -> new Projektauswahl(p.projektId(), p.name())).toList();
    }

    /** Projekthistorie Material (FA-M4-06): eingeplante Positionen und genehmigte Mehrbedarfe, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<HistorienEintrag> projekthistorie(long mitarbeiterId, Rolle rolle, long projektId) {
        projektZugriff.pruefeKostensicht(mitarbeiterId, rolle, projektId);
        List<HistorienEintrag> eintraege = new ArrayList<>(eingeplant(projektId));
        for (Materialanpassungsantrag antrag : antragRepository
                .findByMaterialpositionProjektIdAndStatusOrderByEingereichtAmAsc(projektId, Antragsstatus.GENEHMIGT)) {
            Materialposition position = antrag.getMaterialposition();
            eintraege.add(new HistorienEintrag(LocalDateTime.ofInstant(antrag.getEntschiedenAm(), clock.getZone()),
                    HistorienEintrag.Art.GENEHMIGT, 1, position.getMaterial().getBezeichnung(),
                    position.getMaterial().getEinheit(), antrag.getZusatzmenge(), antrag.getBegruendung(),
                    personennamen.name(antrag.getEntscheiderId())));
        }
        eintraege.sort(Comparator.comparing(HistorienEintrag::zeitpunkt).reversed());
        return eintraege;
    }

    /**
     * Eingeplante Positionen wie im Mockup zusammengefasst: je Tag und Person ein Eintrag. Der erste Eintrag
     * des Projekts heisst ANGELEGT ("Materialplanung angelegt (5 Positionen)"), spaetere EINGEPLANT.
     */
    private List<HistorienEintrag> eingeplant(long projektId) {
        Map<String, List<Materialprotokoll>> gruppen = new LinkedHashMap<>();
        for (Materialprotokoll protokoll : protokollRepository.findByMaterialpositionProjektIdOrderByZeitpunktAscIdAsc(projektId)) {
            if (protokoll.getAktion() == Protokollaktion.GEBUCHT) {
                gruppen.computeIfAbsent(protokoll.getZeitpunkt().toLocalDate() + "/" + protokoll.getGeaendertVonId(),
                        schluessel -> new ArrayList<>()).add(protokoll);
            }
        }
        List<HistorienEintrag> eintraege = new ArrayList<>();
        for (List<Materialprotokoll> gruppe : gruppen.values()) {
            Materialprotokoll erster = gruppe.get(0);
            Materialposition position = erster.getMaterialposition();
            HistorienEintrag.Art art = eintraege.isEmpty() ? HistorienEintrag.Art.ANGELEGT : HistorienEintrag.Art.EINGEPLANT;
            eintraege.add(new HistorienEintrag(erster.getZeitpunkt(), art, gruppe.size(),
                    position.getMaterial().getBezeichnung(), position.getMaterial().getEinheit(), erster.getNeuerWert(),
                    null, personennamen.name(erster.getGeaendertVonId())));
        }
        return eintraege;
    }

    /** Freigabe: Soll-Menge erhoehen, Antrag GENEHMIGT, Protokoll GENEHMIGT, alles in einer Transaktion. */
    @Transactional
    public void freigeben(long mitarbeiterId, Rolle rolle, long antragId) {
        Rechte.pruefeAngemeldet(rolle);
        Materialanpassungsantrag antrag = gesperrt(antragId);
        ProjektInfo projekt = projektZugriff.pruefeVerantwortlich(mitarbeiterId, rolle,
                antrag.getMaterialposition().getProjektId());
        pruefeOffen(antrag);
        ProjektZugriff.pruefeAktiv(projekt);
        genehmigen(antrag, mitarbeiterId, clock.instant());
    }

    /** Ablehnung mit Pflichtkommentar; Mengen bleiben unveraendert. */
    @Transactional
    public void ablehnen(long mitarbeiterId, Rolle rolle, long antragId, String kommentar) {
        Rechte.pruefeAngemeldet(rolle);
        Materialanpassungsantrag antrag = gesperrt(antragId);
        projektZugriff.pruefeVerantwortlich(mitarbeiterId, rolle, antrag.getMaterialposition().getProjektId());
        pruefeOffen(antrag);
        String hinweis = pflichttext(kommentar, "Bitte begründen Sie die Ablehnung.", MAX_KOMMENTAR);
        antrag.setStatus(Antragsstatus.ABGELEHNT);
        antrag.setEntscheiderId(mitarbeiterId);
        antrag.setEntschiedenAm(clock.instant());
        antrag.setEntscheidungshinweis(hinweis);
    }

    private void genehmigen(Materialanpassungsantrag antrag, long entscheiderId, Instant jetzt) {
        Materialposition position = antrag.getMaterialposition();
        BigDecimal alt = position.getSollMenge();
        BigDecimal neu = alt.add(antrag.getZusatzmenge());
        if (neu.compareTo(Betraege.HOECHSTWERT) > 0) {
            throw new FachlicheException("Die neue Soll-Menge wäre zu groß. Bitte prüfen Sie die Menge.");
        }
        position.erhoeheSollMenge(antrag.getZusatzmenge());
        antrag.setStatus(Antragsstatus.GENEHMIGT);
        antrag.setEntscheiderId(entscheiderId);
        antrag.setEntschiedenAm(jetzt);
        protokollRepository.save(new Materialprotokoll(position, entscheiderId,
                LocalDateTime.ofInstant(jetzt, clock.getZone()), Protokollaktion.GENEHMIGT, alt, neu));
    }

    private Materialposition position(long positionId) {
        return positionRepository.findById(positionId)
                .orElseThrow(() -> new NichtGefundenException("Die Materialposition wurde nicht gefunden."));
    }

    private Materialanpassungsantrag gesperrt(long antragId) {
        return antragRepository.findeGesperrt(antragId)
                .orElseThrow(() -> new NichtGefundenException("Der Antrag wurde nicht gefunden."));
    }

    private static void pruefeOffen(Materialanpassungsantrag antrag) {
        if (antrag.getStatus() != Antragsstatus.OFFEN) {
            throw new FachlicheException("Der Antrag ist nicht mehr offen. Er wurde inzwischen entschieden oder zurückgezogen.");
        }
    }

    private static String begruendung(Mehrbedarfsgrund grund, String text) {
        String freitext = pflichttext(text, "Bitte begründen Sie den Mehrbedarf.", MAX_FREITEXT);
        return grund == null ? freitext : grund.text() + ": " + freitext;
    }

    private static String pflichttext(String text, String meldungWennLeer, int maxLaenge) {
        if (text == null || text.isBlank()) {
            throw new FachlicheException(meldungWennLeer);
        }
        String bereinigt = text.strip();
        if (bereinigt.length() > maxLaenge) {
            throw new FachlicheException("Bitte fassen Sie sich kürzer (höchstens " + maxLaenge + " Zeichen).");
        }
        return bereinigt;
    }
}
