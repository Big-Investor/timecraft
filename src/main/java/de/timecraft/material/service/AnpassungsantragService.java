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
import java.util.List;
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
    private final Clock clock;

    public AnpassungsantragService(AnpassungsantragRepository antragRepository,
            MaterialpositionRepository positionRepository, MaterialprotokollRepository protokollRepository,
            ProjektZugriff projektZugriff, Clock clock) {
        this.antragRepository = antragRepository;
        this.positionRepository = positionRepository;
        this.protokollRepository = protokollRepository;
        this.projektZugriff = projektZugriff;
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
                Antragsstatus.OFFEN).stream().map(AntragZurPruefung::aus).toList();
    }

    /** Eigene Antraege, neueste zuerst. */
    @Transactional(readOnly = true)
    public List<MeinMehrbedarf> meineAntraege(long mitarbeiterId, Rolle rolle) {
        Rechte.pruefeAngemeldet(rolle);
        return antragRepository.findByAntragstellerIdOrderByEingereichtAmDescIdDesc(mitarbeiterId).stream()
                .map(MeinMehrbedarf::aus).toList();
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
