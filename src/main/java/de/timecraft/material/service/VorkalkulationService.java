package de.timecraft.material.service;

import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.material.model.Material;
import de.timecraft.material.model.Materialposition;
import de.timecraft.material.model.Materialprotokoll;
import de.timecraft.material.model.Protokollaktion;
import de.timecraft.material.repository.MaterialRepository;
import de.timecraft.material.repository.MaterialpositionRepository;
import de.timecraft.material.repository.MaterialprotokollRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialplanung (Soll) und Vorkalkulation eines Projekts (FA-M4-02, FA-M4-03).
 * Jedes Material steht hoechstens einmal im Projekt; spaetere Erhoehungen laufen ueber einen Antrag.
 */
@Service
public class VorkalkulationService {

    private final MaterialRepository materialRepository;
    private final MaterialpositionRepository positionRepository;
    private final MaterialprotokollRepository protokollRepository;
    private final ProjektZugriff projektZugriff;
    private final Clock clock;

    public VorkalkulationService(MaterialRepository materialRepository, MaterialpositionRepository positionRepository,
            MaterialprotokollRepository protokollRepository, ProjektZugriff projektZugriff, Clock clock) {
        this.materialRepository = materialRepository;
        this.positionRepository = positionRepository;
        this.protokollRepository = protokollRepository;
        this.projektZugriff = projektZugriff;
        this.clock = clock;
    }

    /** Bucht ein Material mit Soll-Menge ins Projekt und protokolliert GEBUCHT (eine Transaktion). */
    @Transactional
    public Materialposition positionHinzufuegen(long mitarbeiterId, Rolle rolle, long projektId, long materialId,
            BigDecimal sollMenge) {
        ProjektZugriff.pruefeAktiv(projektZugriff.pruefeVerantwortlich(mitarbeiterId, rolle, projektId));
        BigDecimal menge = Mengen.pruefe(sollMenge, "die Soll-Menge");
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NichtGefundenException("Das Material wurde nicht gefunden."));
        if (!material.isAktiv()) {
            throw new FachlicheException("Das Material ist deaktiviert und kann nicht mehr eingeplant werden.");
        }
        if (positionRepository.existsByProjektIdAndMaterialId(projektId, materialId)) {
            throw new FachlicheException("Das Material ist in diesem Projekt bereits eingeplant. "
                    + "Mehr Menge bitte über einen Mehrbedarf beantragen.");
        }
        Materialposition position = positionRepository.save(new Materialposition(projektId, material, menge));
        protokollRepository.save(new Materialprotokoll(position, mitarbeiterId, LocalDateTime.now(clock),
                Protokollaktion.GEBUCHT, null, menge));
        return position;
    }

    /** Vorkalkulation mit Preisen: verantwortliche Projektleitung und Admin. */
    @Transactional(readOnly = true)
    public Vorkalkulation vorkalkulation(long mitarbeiterId, Rolle rolle, long projektId) {
        projektZugriff.pruefeKostensicht(mitarbeiterId, rolle, projektId);
        return Vorkalkulation.berechne(positionRepository.findByProjektIdOrderByIdAsc(projektId));
    }
}
