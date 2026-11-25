package de.timecraft.kalender.fake;

import de.timecraft.geruest.common.FachlicheException;
import de.timecraft.zeiterfassung.api.AenderungInfo;
import de.timecraft.zeiterfassung.api.AenderungsArt;
import de.timecraft.zeiterfassung.api.BuchungInfo;
import de.timecraft.zeiterfassung.api.BuchungsAenderung;
import de.timecraft.zeiterfassung.api.Standort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fake fuer die BuchungsAenderung von M2: zeichnet jeden erfolgreichen Aufruf auf. Im Modus
 * „wirft Fehler“ meldet er wie M2 eine Fachausnahme (z. B. Ueberschneidung) und speichert nichts.
 * Mit einer FakeBuchungsAuskunft (lokaler Start, Profil "fake") aendert er zusaetzlich deren Buchung,
 * schreibt die Historie und meldet Ueberschneidungen wie M2.
 *
 * @author Bennet Antonio Brinkmeier
 */
public class FakeBuchungsAenderung implements BuchungsAenderung {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATUM_ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("HH:mm");

    /** Ein gespeicherter Aufruf von aendern. */
    public record Aufruf(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) { }

    private final List<Aufruf> aufrufe = new ArrayList<>();
    private final FakeBuchungsAuskunft auskunft;
    private final Clock clock;
    private String fehlermeldung;

    /** Nur aufzeichnen (Tests). */
    public FakeBuchungsAenderung() {
        this(null, null);
    }

    /** Aufzeichnen und die Buchung in der Auskunft aendern (lokaler Start). */
    public FakeBuchungsAenderung(FakeBuchungsAuskunft auskunft, Clock clock) {
        this.auskunft = auskunft;
        this.clock = clock;
    }

    /** Ab jetzt wirft aendern eine Fachausnahme mit dieser Meldung. */
    public void wirftFehler(String meldung) {
        this.fehlermeldung = meldung;
    }

    public List<Aufruf> aufrufe() {
        return List.copyOf(aufrufe);
    }

    @Override
    public void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung) {
        if (fehlermeldung != null) {
            throw new FachlicheException(fehlermeldung);
        }
        if (auskunft != null) {
            uebernehmen(zeiteintragId, beginn, ende, standort, projektId, art, geaendertVonMitarbeiterId, begruendung);
        }
        aufrufe.add(new Aufruf(zeiteintragId, beginn, ende, standort, projektId, art, geaendertVonMitarbeiterId,
                begruendung));
    }

    /** Nachtragen (K2) ist zurueckgestellt; der Kalender ruft es nicht auf. */
    @Override
    public long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
            long geaendertVonMitarbeiterId, String begruendung) {
        throw new UnsupportedOperationException("Buchung nachtragen (K2) ist zurückgestellt.");
    }

    private void uebernehmen(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
            AenderungsArt art, long vonId, String begruendung) {
        BuchungInfo alt = auskunft.buchung(zeiteintragId)
                .orElseThrow(() -> new FachlicheException("Diese Buchung gibt es nicht."));
        pruefeUeberschneidung(alt, beginn, ende);
        int netto = Math.max(0, (int) Duration.between(beginn, ende).toMinutes() - alt.pausenMinuten());
        auskunft.ersetzen(new BuchungInfo(zeiteintragId, alt.mitarbeiterId(), projektId, standort, beginn, ende,
                alt.pausenMinuten(), netto, true));
        Instant jetzt = clock.instant();
        protokoll(zeiteintragId, jetzt, vonId, art, "Beginn", text(alt.beginn()), text(beginn), begruendung);
        protokoll(zeiteintragId, jetzt, vonId, art, "Ende", text(alt.ende()), text(ende), begruendung);
        protokoll(zeiteintragId, jetzt, vonId, art, "Standort", text(alt.standort()), text(standort), begruendung);
        protokoll(zeiteintragId, jetzt, vonId, art, "Projekt", projekt(alt.projektId()), projekt(projektId),
                begruendung);
    }

    private void pruefeUeberschneidung(BuchungInfo geaendert, Instant beginn, Instant ende) {
        for (BuchungInfo andere : auskunft.buchungen(geaendert.mitarbeiterId(), beginn, ende)) {
            if (andere.zeiteintragId() != geaendert.zeiteintragId()) {
                String bis = andere.laeuft() ? "läuft" : ZEIT.format(andere.ende().atZone(BERLIN)) + " Uhr";
                throw new FachlicheException("Die Buchung überschneidet sich mit der Buchung vom "
                        + DATUM.format(andere.beginn().atZone(BERLIN)) + ", "
                        + ZEIT.format(andere.beginn().atZone(BERLIN)) + "–" + bis + ".");
            }
        }
    }

    private void protokoll(long zeiteintragId, Instant zeitpunkt, long vonId, AenderungsArt art, String feld,
            String alterWert, String neuerWert, String begruendung) {
        if (!Objects.equals(alterWert, neuerWert)) {
            auskunft.historieHinzufuegen(zeiteintragId,
                    new AenderungInfo(zeitpunkt, vonId, art, feld, alterWert, neuerWert, begruendung));
        }
    }

    private static String text(Standort standort) {
        return switch (standort) {
            case BUERO -> "Büro";
            case HOMEOFFICE -> "Homeoffice";
            case UNTERWEGS -> "unterwegs";
        };
    }

    private static String projekt(Long projektId) {
        return projektId == null ? "Ohne Projekt" : "P-" + projektId;
    }

    private static String text(Instant zeitpunkt) {
        return DATUM_ZEIT.format(zeitpunkt.atZone(BERLIN));
    }
}
