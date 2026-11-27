package de.timecraft.antraege.service;

import de.timecraft.antraege.model.Abwesenheitsantrag;
import de.timecraft.antraege.model.Antrag;
import de.timecraft.antraege.model.Antragsstatus;
import de.timecraft.antraege.model.Stornoantrag;
import de.timecraft.antraege.model.Urlaubsart;
import de.timecraft.antraege.model.Zeitkorrekturantrag;
import de.timecraft.antraege.repository.AntragRepository;
import de.timecraft.benutzer.api.MitarbeiterAuskunft;
import de.timecraft.benutzer.api.MitarbeiterInfo;
import de.timecraft.benutzer.api.UrlaubsDaten;
import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.geruest.common.NichtGefundenException;
import de.timecraft.geruest.common.ZugriffVerweigertException;
import de.timecraft.projekte.api.ProjektAuskunft;
import de.timecraft.projekte.api.ProjektInfo;
import de.timecraft.zeiterfassung.api.AbwesenheitsArt;
import de.timecraft.zeiterfassung.api.AbwesenheitsUebernahme;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAuskunft;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Fachregeln fuer Antraege (FA-M5-07): Pflichtfelder, Zeitraeume, Ueberschneidungen und
 * typabhaengige Regeln. Wird beim Erstellen, Bearbeiten und vor der Genehmigung aufgerufen.
 * Jeder Verstoss ergibt eine Fachausnahme mit deutscher Meldung; gespeichert wird hier nichts.
 * Rollenrechte prueft der aufrufende Service.
 */
@Service
public class AntragRegelnService {

    public static final int MAX_TEXTLAENGE = 255;

    private static final List<Antragsstatus> OFFEN_ODER_GENEHMIGT =
            List.of(Antragsstatus.OFFEN, Antragsstatus.GENEHMIGT);

    private final AntragRepository repository;
    private final MitarbeiterAuskunft mitarbeiterAuskunft;
    private final BuchungsAuskunft buchungsAuskunft;
    private final AbwesenheitsUebernahme abwesenheitsUebernahme;
    private final ProjektAuskunft projektAuskunft;
    private final Clock clock;

    public AntragRegelnService(AntragRepository repository, MitarbeiterAuskunft mitarbeiterAuskunft,
            BuchungsAuskunft buchungsAuskunft, AbwesenheitsUebernahme abwesenheitsUebernahme,
            ProjektAuskunft projektAuskunft, Clock clock) {
        this.repository = repository;
        this.mitarbeiterAuskunft = mitarbeiterAuskunft;
        this.buchungsAuskunft = buchungsAuskunft;
        this.abwesenheitsUebernahme = abwesenheitsUebernahme;
        this.projektAuskunft = projektAuskunft;
        this.clock = clock;
    }

    /** Prueft einen Antrag vollstaendig; wirft beim ersten Verstoss eine Fachausnahme. */
    public void pruefe(Antrag antrag) {
        pruefeAntragsteller(antrag.getAntragstellerId());
        pruefeTextlaenge(antrag.getBegruendung(), "Die Begründung");
        if (antrag instanceof Abwesenheitsantrag abwesenheit) {
            pruefeAbwesenheit(abwesenheit);
        } else if (antrag instanceof Zeitkorrekturantrag zeitkorrektur) {
            pruefeZeitkorrektur(zeitkorrektur);
        } else if (antrag instanceof Stornoantrag storno) {
            pruefeStorno(storno);
        } else {
            throw new FachlicheException("Dieser Antragstyp wird hier nicht bearbeitet.");
        }
    }

    /** Zustaendig ist der Vorgesetzte des Antragstellers (Teamentscheidung 04.11.2026). */
    public boolean istZustaendig(long entscheiderId, long antragstellerId) {
        return mitarbeiterAuskunft.finde(antragstellerId)
                .map(MitarbeiterInfo::vorgesetzterId)
                .filter(vorgesetzter -> vorgesetzter == entscheiderId)
                .isPresent();
    }

    /** Ein genehmigter Antrag ist abgeloest, wenn eine Aenderung oder ein Storno dazu genehmigt wurde. */
    public boolean istAbgeloest(Antrag antrag) {
        return antrag.getId() != null && repository.findByBezugsantragId(antrag.getId()).stream()
                .anyMatch(folge -> folge.getStatus() == Antragsstatus.GENEHMIGT);
    }

    /** Buchung fuer eine Zeitkorrektur: muss existieren, dem Antragsteller gehoeren und abgeschlossen sein. */
    public BuchungInfo korrigierbareBuchung(long antragstellerId, Long zeiteintragId) {
        if (zeiteintragId == null) {
            throw new FachlicheException("Bitte wählen Sie die betroffene Buchung aus.");
        }
        BuchungInfo buchung = buchungsAuskunft.buchung(zeiteintragId)
                .orElseThrow(() -> new NichtGefundenException("Die Buchung wurde nicht gefunden."));
        if (buchung.mitarbeiterId() != antragstellerId) {
            throw new ZugriffVerweigertException("Sie können nur eigene Buchungen korrigieren lassen.");
        }
        if (buchung.laeuft()) {
            throw new FachlicheException("Laufende Buchungen können nicht korrigiert werden.");
        }
        return buchung;
    }

    /**
     * Resturlaub eines Jahres: Anspruch und Uebertrag minus aller offenen und gueltig genehmigten
     * Urlaube, die gegen den Anspruch zaehlen. Die Antraege mit den IDs in {@code ohneAntraege}
     * (der Antrag selbst und sein Bezugsantrag) werden nicht abgezogen.
     */
    public int resturlaub(long mitarbeiterId, int jahr, List<Long> ohneAntraege) {
        UrlaubsDaten daten = mitarbeiterAuskunft.urlaubsdaten(mitarbeiterId, jahr).orElseThrow(
                () -> new FachlicheException("Für " + jahr + " ist kein Urlaubsanspruch hinterlegt."));
        int rest = daten.anspruchTage() + daten.uebertragTage();
        LocalDate jahresbeginn = LocalDate.of(jahr, 1, 1);
        LocalDate jahresende = LocalDate.of(jahr, 12, 31);
        for (Abwesenheitsantrag urlaub : repository.findeAbwesenheiten(mitarbeiterId, OFFEN_ODER_GENEHMIGT,
                jahresbeginn, jahresende)) {
            if (zaehltGegenAnspruch(urlaub) && !ohneAntraege.contains(urlaub.getId()) && !istAbgeloest(urlaub)) {
                rest -= urlaubstageImJahr(mitarbeiterId, urlaub.getVon(), urlaub.getBis(), jahr);
            }
        }
        return rest;
    }

    /** Urlaubstage des Zeitraums, die in das angegebene Jahr fallen (laut M2). */
    public int urlaubstageImJahr(long mitarbeiterId, LocalDate von, LocalDate bis, int jahr) {
        LocalDate anfang = von.getYear() < jahr ? LocalDate.of(jahr, 1, 1) : von;
        LocalDate ende = bis.getYear() > jahr ? LocalDate.of(jahr, 12, 31) : bis;
        if (ende.isBefore(anfang)) {
            return 0;
        }
        return abwesenheitsUebernahme.urlaubstage(mitarbeiterId, anfang, ende);
    }

    /** Erholungs- und Sonderurlaub zaehlen gegen den Anspruch, unbezahlter Urlaub nicht (Alper Sayar, 27.11.2026). */
    public static boolean zaehltGegenAnspruch(Abwesenheitsantrag antrag) {
        return antrag.getArt() == AbwesenheitsArt.URLAUB && antrag.getUrlaubsart() != Urlaubsart.UNBEZAHLTER_URLAUB;
    }

    /** IDs fuer {@link #resturlaub}: nur die vorhandenen (neue Antraege haben noch keine ID). */
    public static List<Long> ohneLeere(Long... antragIds) {
        return Stream.of(antragIds).filter(Objects::nonNull).toList();
    }

    public static void pruefeTextlaenge(String text, String feld) {
        if (text != null && text.length() > MAX_TEXTLAENGE) {
            throw new FachlicheException(feld + " darf höchstens " + MAX_TEXTLAENGE + " Zeichen lang sein.");
        }
    }

    // ---------- Abwesenheit ----------

    private void pruefeAbwesenheit(Abwesenheitsantrag antrag) {
        if (antrag.getArt() == null) {
            throw new FachlicheException("Bitte wählen Sie die Art der Abwesenheit.");
        }
        if (antrag.getArt() == AbwesenheitsArt.URLAUB && antrag.getUrlaubsart() == null) {
            throw new FachlicheException("Bitte wählen Sie die Urlaubsart.");
        }
        if (antrag.getArt() != AbwesenheitsArt.URLAUB && antrag.getUrlaubsart() != null) {
            throw new FachlicheException("Eine Urlaubsart gibt es nur bei Urlaub.");
        }
        if (antrag.getVon() == null || antrag.getBis() == null) {
            throw new FachlicheException("Bitte geben Sie Beginn und Ende des Zeitraums an.");
        }
        if (antrag.getBis().isBefore(antrag.getVon())) {
            throw new FachlicheException("Das Ende darf nicht vor dem Beginn liegen.");
        }
        Abwesenheitsantrag bezug = null;
        if (antrag.getBezugsantragId() != null) {
            bezug = (Abwesenheitsantrag) genehmigterBezug(antrag, Abwesenheitsantrag.class,
                    "Eine Änderung muss denselben Antragstyp wie der ursprüngliche Antrag haben.");
            if (bezug.getArt() != antrag.getArt()) {
                throw new FachlicheException(
                        "Eine Änderung muss dieselbe Art der Abwesenheit wie der ursprüngliche Antrag haben.");
            }
        }
        pruefeEigeneUeberschneidung(antrag, bezug);
        pruefeUeberschneidungInZeiterfassung(antrag, bezug);
        if (antrag.getArt() == AbwesenheitsArt.URLAUB) {
            pruefeUrlaubstage(antrag, bezug);
        }
    }

    private void pruefeEigeneUeberschneidung(Abwesenheitsantrag antrag, Abwesenheitsantrag bezug) {
        for (Abwesenheitsantrag anderer : repository.findeAbwesenheiten(antrag.getAntragstellerId(),
                OFFEN_ODER_GENEHMIGT, antrag.getVon(), antrag.getBis())) {
            if (!gleich(anderer, antrag) && !gleich(anderer, bezug) && !istAbgeloest(anderer)) {
                throw new FachlicheException("Der Zeitraum überschneidet sich mit einem anderen Antrag von Ihnen.");
            }
        }
    }

    /**
     * Fragt M2 nach bestehenden Abwesenheiten. Bei einer Aenderung wird der Zeitraum des
     * Bezugsantrags ausgespart, weil dessen Abwesenheit durch die Aenderung ersetzt wird.
     */
    private void pruefeUeberschneidungInZeiterfassung(Abwesenheitsantrag antrag, Abwesenheitsantrag bezug) {
        for (LocalDate[] teil : ohneZeitraum(antrag.getVon(), antrag.getBis(), bezug)) {
            if (abwesenheitsUebernahme.ueberschneidetAbwesenheit(antrag.getAntragstellerId(), teil[0], teil[1])) {
                throw new FachlicheException("Im Zeitraum ist bereits eine Abwesenheit eingetragen.");
            }
        }
    }

    private static List<LocalDate[]> ohneZeitraum(LocalDate von, LocalDate bis, Abwesenheitsantrag bezug) {
        List<LocalDate[]> teile = new ArrayList<>();
        if (bezug == null) {
            teile.add(new LocalDate[] {von, bis});
            return teile;
        }
        if (von.isBefore(bezug.getVon())) {
            teile.add(new LocalDate[] {von, frueheres(bis, bezug.getVon().minusDays(1))});
        }
        if (bis.isAfter(bezug.getBis())) {
            teile.add(new LocalDate[] {spaeteres(von, bezug.getBis().plusDays(1)), bis});
        }
        return teile;
    }

    private void pruefeUrlaubstage(Abwesenheitsantrag antrag, Abwesenheitsantrag bezug) {
        long mitarbeiterId = antrag.getAntragstellerId();
        if (abwesenheitsUebernahme.urlaubstage(mitarbeiterId, antrag.getVon(), antrag.getBis()) == 0) {
            throw new FachlicheException("Im gewählten Zeitraum liegt kein Arbeitstag.");
        }
        if (!zaehltGegenAnspruch(antrag)) {
            return;
        }
        List<Long> ohneAntraege = ohneLeere(antrag.getId(), bezug == null ? null : bezug.getId());
        for (int jahr = antrag.getVon().getYear(); jahr <= antrag.getBis().getYear(); jahr++) {
            int rest = resturlaub(mitarbeiterId, jahr, ohneAntraege);
            int beantragt = urlaubstageImJahr(mitarbeiterId, antrag.getVon(), antrag.getBis(), jahr);
            if (beantragt > rest) {
                throw new FachlicheException("Der Urlaub übersteigt Ihren Resturlaub für " + jahr
                        + " (noch " + Math.max(rest, 0) + " Tage).");
            }
        }
    }

    // ---------- Zeitkorrektur ----------

    private void pruefeZeitkorrektur(Zeitkorrekturantrag antrag) {
        long mitarbeiterId = antrag.getAntragstellerId();
        BuchungInfo buchung = korrigierbareBuchung(mitarbeiterId, antrag.getZeiteintragId());
        if (antrag.getNeuerBeginn() == null || antrag.getNeuesEnde() == null || antrag.getNeuerStandort() == null) {
            throw new FachlicheException("Bitte geben Sie Beginn, Ende und Standort an.");
        }
        if (!antrag.getNeuesEnde().isAfter(antrag.getNeuerBeginn())) {
            throw new FachlicheException("Das Ende muss nach dem Beginn liegen.");
        }
        if (antrag.getNeuesEnde().isAfter(clock.instant())) {
            throw new FachlicheException("Die gewünschten Zeiten dürfen nicht in der Zukunft liegen.");
        }
        if (!buchung.beginn().equals(antrag.getAlterBeginn()) || !buchung.ende().equals(antrag.getAltesEnde())
                || buchung.standort() != antrag.getAlterStandort()) {
            throw new FachlicheException(
                    "Die Buchung wurde inzwischen geändert. Bitte ziehen Sie den Antrag zurück und stellen Sie ihn neu.");
        }
        if (antrag.getNeuerBeginn().equals(buchung.beginn()) && antrag.getNeuesEnde().equals(buchung.ende())
                && antrag.getNeuerStandort() == buchung.standort()
                && Objects.equals(antrag.getNeuesProjektId(), buchung.projektId())) {
            throw new FachlicheException("Mindestens ein Wert muss sich von der Buchung unterscheiden.");
        }
        pruefeZielprojekt(mitarbeiterId, antrag.getNeuesProjektId(), buchung.projektId());
        pruefeUeberschneidungMitBuchungen(antrag);
        pruefeOffeneZeitkorrektur(antrag);
        if (antrag.getBezugsantragId() != null) {
            Zeitkorrekturantrag bezug = (Zeitkorrekturantrag) genehmigterBezug(antrag, Zeitkorrekturantrag.class,
                    "Eine Änderung muss denselben Antragstyp wie der ursprüngliche Antrag haben.");
            if (bezug.getZeiteintragId() != antrag.getZeiteintragId()) {
                throw new FachlicheException("Die Änderung muss dieselbe Buchung betreffen.");
            }
        }
    }

    private void pruefeZielprojekt(long mitarbeiterId, Long neuesProjektId, Long bisherigesProjektId) {
        if (neuesProjektId == null || neuesProjektId.equals(bisherigesProjektId)) {
            return;
        }
        boolean aktiv = projektAuskunft.finde(neuesProjektId).map(ProjektInfo::aktiv).orElse(false);
        if (!aktiv) {
            throw new FachlicheException("Das gewünschte Projekt ist nicht aktiv.");
        }
        if (!projektAuskunft.istZugeordnet(mitarbeiterId, neuesProjektId)) {
            throw new FachlicheException("Sie sind dem gewünschten Projekt nicht zugeordnet.");
        }
    }

    private void pruefeUeberschneidungMitBuchungen(Zeitkorrekturantrag antrag) {
        boolean ueberschneidung = buchungsAuskunft.buchungen(antrag.getAntragstellerId(), antrag.getNeuerBeginn(),
                antrag.getNeuesEnde()).stream()
                .filter(andere -> andere.zeiteintragId() != antrag.getZeiteintragId())
                .anyMatch(andere -> andere.beginn().isBefore(antrag.getNeuesEnde())
                        && (andere.laeuft() || andere.ende().isAfter(antrag.getNeuerBeginn())));
        if (ueberschneidung) {
            throw new FachlicheException("Die gewünschte Zeit überschneidet sich mit einer anderen Buchung.");
        }
    }

    private void pruefeOffeneZeitkorrektur(Zeitkorrekturantrag antrag) {
        boolean andererOffen = repository.findeZeitkorrekturen(antrag.getZeiteintragId(), Antragsstatus.OFFEN)
                .stream().anyMatch(anderer -> !gleich(anderer, antrag));
        if (andererOffen) {
            throw new FachlicheException("Für diese Buchung gibt es bereits einen offenen Antrag.");
        }
    }

    // ---------- Storno ----------

    private void pruefeStorno(Stornoantrag antrag) {
        if (antrag.getBezugsantragId() == null) {
            throw new FachlicheException("Bitte wählen Sie den Antrag aus, den Sie stornieren möchten.");
        }
        if (antrag.getBegruendung() == null || antrag.getBegruendung().isBlank()) {
            throw new FachlicheException("Bitte geben Sie einen Grund für die Stornierung an.");
        }
        genehmigterBezug(antrag, Abwesenheitsantrag.class, "Stornieren können Sie nur genehmigte Abwesenheiten.");
    }

    // ---------- gemeinsam ----------

    private void pruefeAntragsteller(long mitarbeiterId) {
        MitarbeiterInfo person = mitarbeiterAuskunft.finde(mitarbeiterId)
                .orElseThrow(() -> new FachlicheException("Der Antragsteller ist nicht bekannt."));
        if (person.vorgesetzterId() == null) {
            throw new FachlicheException("Ihnen ist keine zuständige Projektleitung zugeordnet. "
                    + "Bitte wenden Sie sich an die Verwaltung.");
        }
    }

    /**
     * Bezugsantrag einer Aenderung oder eines Stornos: eigener, genehmigter Antrag des erwarteten
     * Typs, noch nicht abgeloest und ohne weiteren offenen Folgeantrag.
     */
    private Antrag genehmigterBezug(Antrag folge, Class<? extends Antrag> typ, String falscherTyp) {
        Antrag bezug = repository.findById(folge.getBezugsantragId())
                .orElseThrow(() -> new NichtGefundenException("Der ursprüngliche Antrag wurde nicht gefunden."));
        if (bezug.getAntragstellerId() != folge.getAntragstellerId()) {
            throw new ZugriffVerweigertException("Sie können nur eigene Anträge ändern oder stornieren.");
        }
        if (!typ.isInstance(bezug)) {
            throw new FachlicheException(falscherTyp);
        }
        if (bezug.getStatus() != Antragsstatus.GENEHMIGT) {
            throw new FachlicheException("Ändern oder stornieren können Sie nur genehmigte Anträge.");
        }
        for (Antrag andereFolge : repository.findByBezugsantragId(bezug.getId())) {
            if (gleich(andereFolge, folge)) {
                continue;
            }
            if (andereFolge.getStatus() == Antragsstatus.GENEHMIGT) {
                throw new FachlicheException("Dieser Antrag wurde bereits geändert oder storniert.");
            }
            if (andereFolge.getStatus() == Antragsstatus.OFFEN) {
                throw new FachlicheException(
                        "Zu diesem Antrag gibt es bereits einen offenen Änderungs- oder Stornoantrag.");
            }
        }
        return bezug;
    }

    private static boolean gleich(Antrag a, Antrag b) {
        return b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    private static LocalDate frueheres(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate spaeteres(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
