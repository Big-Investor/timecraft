package de.timecraft.projekte.service;

import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.Rolle;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.projekte.model.Projekt;
import de.timecraft.projekte.model.ProjektDaten;
import de.timecraft.projekte.model.ProjektFilter;
import de.timecraft.projekte.model.ProjektStatus;
import de.timecraft.projekte.model.Projektzuordnung;
import de.timecraft.projekte.repository.ProjektRepository;
import de.timecraft.projekte.repository.ProjektzuordnungRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Fachlogik für Projekte: anlegen, ändern, archivieren, reaktivieren und die Projektliste.
 * <p>
 * Hier liegen alle Regeln und Rechteprüfungen. Die Oberfläche ruft nur diesen Service auf.
 * Die angemeldete Person kommt als Parameter (Mitarbeiter-ID und Rolle), damit der Service auch
 * ohne Browser getestet werden kann. Eine Rolle null bedeutet: Es ist niemand angemeldet.
 * <p>
 * Ein Projekt wird nie gelöscht: Es gibt dafür absichtlich keine Methode. Abgeschlossene
 * Projekte werden archiviert, ihre Buchungen bleiben erhalten.
 */
@Service
public class ProjektService {

    /** So lang darf ein Projektname höchstens sein (Spalte name ist VARCHAR(100)). */
    public static final int MAX_NAME_LAENGE = 100;

    private final ProjektRepository repository;
    private final ProjektzuordnungRepository zuordnungRepository;
    private final ProjektBerechtigung berechtigung;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;

    /**
     * "@Lazy" heißt: Spring besorgt sich die MitarbeiterAuskunft (gehört zu Modul M1) erst, wenn
     * sie zum ersten Mal gebraucht wird. Solange M1 in diesem Branch noch fehlt, kann die
     * Anwendung so trotzdem starten.
     */
    public ProjektService(ProjektRepository repository, ProjektzuordnungRepository zuordnungRepository,
            ProjektBerechtigung berechtigung, @Lazy MitarbeiterAuskunft mitarbeiterAuskunft) {
        this.repository = repository;
        this.zuordnungRepository = zuordnungRepository;
        this.berechtigung = berechtigung;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
    }

    /**
     * Legt ein neues Projekt an. Nur der Admin darf das. Ist etwas ungültig, gibt es eine
     * verständliche Meldung und es wird nichts gespeichert.
     * Das neue Projekt ist AKTIV, die Projekt-ID vergibt die Datenbank.
     * <p>
     * Die Mitarbeiter-ID des Admins wird hier nicht gebraucht. Sie steht trotzdem in der
     * Parameterliste, damit alle Methoden des Service dieselbe Form haben (wie im Gerüst vorgegeben).
     */
    @Transactional
    public Projekt anlegen(long mitarbeiterId, Rolle rolle, ProjektDaten daten) {
        berechtigung.pruefeAdmin(rolle);
        pruefeDaten(daten);
        pruefeProjektleitung(daten.projektleitungId());

        Projekt projekt = new Projekt(daten.projektleitungId(), daten.name().strip(),
                bereinigeBeschreibung(daten.beschreibung()), daten.startdatum(), daten.zieldatum());
        return repository.save(projekt);
    }

    /**
     * Ändert Name, Beschreibung, Start, Ziel und Projektleitung eines Projekts. Nur der Admin darf das,
     * auch bei einem archivierten Projekt. Projekt-ID, Status und Angebotspreis bleiben unverändert.
     * <p>
     * Erst werden alle Eingaben geprüft, dann geändert. Bei einem Fehler bleibt das Projekt also
     * ganz wie es war.
     */
    @Transactional
    public Projekt aendern(long mitarbeiterId, Rolle rolle, long projektId, ProjektDaten daten) {
        berechtigung.pruefeAdmin(rolle);
        Projekt projekt = ladeProjekt(projektId);
        pruefeDaten(daten);
        // Die Projektleitung wird nur geprüft, wenn der Admin sie wechselt. Sonst würde eine
        // inzwischen deaktivierte Projektleitung jede andere Änderung (z. B. am Namen) blockieren.
        if (daten.projektleitungId() != projekt.getProjektleitungId()) {
            pruefeProjektleitung(daten.projektleitungId());
        }

        projekt.setName(daten.name().strip());
        projekt.setBeschreibung(bereinigeBeschreibung(daten.beschreibung()));
        projekt.setStartdatum(daten.startdatum());
        projekt.setZieldatum(daten.zieldatum());
        projekt.setProjektleitungId(daten.projektleitungId());
        return repository.save(projekt);
    }

    /**
     * Ändert nur die Beschreibung. Das darf die verantwortliche Projektleitung des Projekts,
     * und nur solange das Projekt aktiv ist. Alle anderen Felder kann sie nicht ändern.
     * Der Admin ändert die Beschreibung über {@link #aendern}.
     */
    @Transactional
    public Projekt beschreibungAendern(long mitarbeiterId, Rolle rolle, long projektId, String beschreibung) {
        berechtigung.pruefeAngemeldet(rolle);
        Projekt projekt = ladeProjekt(projektId);
        berechtigung.pruefeVerantwortlicheProjektleitung(mitarbeiterId, rolle, projekt);
        if (projekt.getStatus() != ProjektStatus.AKTIV) {
            throw new FachlicheException("Die Beschreibung eines archivierten Projekts kann nicht geändert werden.");
        }

        projekt.setBeschreibung(bereinigeBeschreibung(beschreibung));
        return repository.save(projekt);
    }

    /**
     * Archiviert ein Projekt. Nur der Admin darf das. Archivierte Projekte erscheinen nicht mehr
     * in der Auswahl zum Einstempeln (M2), Zuordnungen und bisherige Buchungen bleiben erhalten.
     * Ist das Projekt schon archiviert, gibt es eine Meldung und nichts ändert sich.
     */
    @Transactional
    public Projekt archivieren(long mitarbeiterId, Rolle rolle, long projektId) {
        berechtigung.pruefeAdmin(rolle);
        Projekt projekt = ladeProjekt(projektId);
        if (projekt.getStatus() == ProjektStatus.ARCHIVIERT) {
            throw new FachlicheException("Das Projekt ist bereits archiviert.");
        }

        projekt.setStatus(ProjektStatus.ARCHIVIERT);
        return repository.save(projekt);
    }

    /** Macht ein archiviertes Projekt wieder aktiv. Nur der Admin darf das. Ist es schon aktiv, gibt es eine Meldung. */
    @Transactional
    public Projekt reaktivieren(long mitarbeiterId, Rolle rolle, long projektId) {
        berechtigung.pruefeAdmin(rolle);
        Projekt projekt = ladeProjekt(projektId);
        if (projekt.getStatus() == ProjektStatus.AKTIV) {
            throw new FachlicheException("Das Projekt ist bereits aktiv.");
        }

        projekt.setStatus(ProjektStatus.AKTIV);
        return repository.save(projekt);
    }

    /**
     * Die Projektliste für die angemeldete Person, sortiert nach Projekt-ID. Zuerst gilt der Filter
     * (alle gesetzten Filter wirken gemeinsam), danach bleibt nur stehen, was die Rolle sehen darf:
     * <ul>
     *   <li>Admin: alle Projekte, auch archivierte.</li>
     *   <li>Projektleitung: alle aktiven Projekte, nicht nur die eigenen.</li>
     *   <li>Mitarbeiter: nur die Projekte, denen er zugeordnet ist.</li>
     * </ul>
     * Die Sichtbarkeit gilt immer zusätzlich zum Filter. Der Filter kann also nie mehr zeigen, als die
     * Rolle sehen darf: Fragt ein Mitarbeiter über den Mitarbeiterfilter nach einer anderen Person,
     * sieht er davon nur die Projekte, die ihm ohnehin zugeordnet sind (ohne Fehlermeldung).
     * Ein Filter null oder ein leerer Filter bedeutet: nichts filtern.
     */
    @Transactional(readOnly = true)
    public List<Projekt> projekteSehen(long mitarbeiterId, Rolle rolle, ProjektFilter filter) {
        berechtigung.pruefeAngemeldet(rolle);
        List<Projekt> treffer = repository.filtern(filter == null ? new ProjektFilter() : filter);

        if (rolle == Rolle.ADMIN) {
            return treffer;
        }
        if (rolle == Rolle.PROJEKTLEITUNG) {
            return treffer.stream().filter(projekt -> projekt.getStatus() == ProjektStatus.AKTIV).toList();
        }
        Set<Long> eigeneProjekte = zuordnungRepository.findByMitarbeiterId(mitarbeiterId).stream()
                .map(Projektzuordnung::getProjektId)
                .collect(Collectors.toSet());
        return treffer.stream().filter(projekt -> eigeneProjekte.contains(projekt.getProjektId())).toList();
    }

    /** Holt das Projekt aus der Datenbank. Gibt es die ID nicht, kommt eine verständliche Meldung. */
    private Projekt ladeProjekt(long projektId) {
        return repository.findById(projektId)
                .orElseThrow(() -> new NichtGefundenException("Das Projekt wurde nicht gefunden."));
    }

    /**
     * Prüft die Eingaben, die nur aus den Daten selbst erkennbar sind: Pflichtfelder,
     * Länge des Namens und die Reihenfolge der Termine.
     */
    private void pruefeDaten(ProjektDaten daten) {
        if (daten.name() == null || daten.name().isBlank()) {
            throw new FachlicheException("Bitte geben Sie einen Projektnamen ein.");
        }
        if (daten.name().strip().length() > MAX_NAME_LAENGE) {
            throw new FachlicheException("Der Projektname darf höchstens " + MAX_NAME_LAENGE + " Zeichen lang sein.");
        }
        if (daten.startdatum() == null) {
            throw new FachlicheException("Bitte geben Sie ein Startdatum ein.");
        }
        if (daten.zieldatum() == null) {
            throw new FachlicheException("Bitte geben Sie ein Zieldatum ein.");
        }
        if (daten.zieldatum().isBefore(daten.startdatum())) {
            throw new FachlicheException("Das Zieldatum darf nicht vor dem Startdatum liegen.");
        }
        if (daten.projektleitungId() == null) {
            throw new FachlicheException("Bitte wählen Sie eine verantwortliche Projektleitung aus.");
        }
    }

    /**
     * Prüft die gewählte Projektleitung bei M1: Die Person muss es geben, sie muss noch aktiv sein
     * und die Rolle PROJEKTLEITUNG haben (ein Admin kann nicht verantwortliche Projektleitung sein).
     */
    private void pruefeProjektleitung(long projektleitungId) {
        MitarbeiterInfo person = mitarbeiterAuskunft.finde(projektleitungId)
                .orElseThrow(() -> new FachlicheException("Die gewählte Projektleitung wurde nicht gefunden."));
        if (person.rolle() != Rolle.PROJEKTLEITUNG) {
            throw new FachlicheException("Die gewählte Person ist keine Projektleitung.");
        }
        if (!person.aktiv()) {
            throw new FachlicheException("Die gewählte Projektleitung ist nicht mehr aktiv.");
        }
    }

    /** Eine leere Beschreibung (nur Leerzeichen) wird nicht gespeichert, sondern bleibt leer (null). */
    private String bereinigeBeschreibung(String beschreibung) {
        if (beschreibung == null || beschreibung.isBlank()) {
            return null;
        }
        return beschreibung.strip();
    }
}
