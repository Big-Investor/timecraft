# CLAUDE.md – TimeCraft Modul M5 Anträge und Korrekturen

TimeCraft ist eine digitale Zeiterfassung mit Projektübersicht für Unternehmen und Abteilungen (Softwareprojekt Wirtschaftsinformatik, Hochschule Bielefeld). Sieben Personen entwickeln je ein Modul. **Du arbeitest für Alper Sayar an genau einem Modul.** Programm bleibt lokal, nur erfundene Testdaten.

## 0. Kurzauftrag
Einheitlicher Prozess für Anträge: erstellen, prüfen, genehmigen, ablehnen, bearbeiten, zurückziehen, Änderung/Storno genehmigter Anträge. Antragstypen: Zeitkorrektur, Urlaub, Krankheit, sonstige Abwesenheit, Änderung/Storno. Der Typ bestimmt Felder und Fachregeln. Bei Genehmigung werden die Fachdaten **atomar** in M2 aktualisiert (über `AbwesenheitsUebernahme`/`BuchungsAenderung`).
Du besitzt außerdem die Basisklasse `Antrag` (Tabelle `antrag`), von der M4 für den Materialanpassungsantrag erbt. Material-Anträge entscheidet M4 selbst, nicht Du.

## 1. Dein Auftrag und Deine Grenzen (hart)
- Du entwickelst **ausschließlich Modul M5 Anträge und Korrekturen** im Paket `de.timecraft.antraege` (Unterpakete `view`, `service`, `repository`, `model`, `api`) plus Tests. Verantwortlich: Alper Sayar. Branch: `modul/m5-antraege`.
- **Fasse nichts anderes an:** nicht die Pakete anderer Module (auch nicht „nur kurz fixen“), nicht das Gerüst (`geruest.*`), nicht `pom.xml` (keine neuen Abhängigkeiten ohne Rückfrage), nicht den Schnittstellenvertrag (siehe Abschnitt 8). Auch wenn ein anderes Modul noch fehlt: Du schreibst es nicht nach, Du nimmst einen Fake.
- Auf andere Module greifst Du **nur über deren `api`** zu. Nie auf deren `service`, `repository`, `model` oder Tabellen (einzige Ausnahme: Basisklasse `Antrag` aus M5 für M4). Verweise auf fremde Daten nur als ID (`mitarbeiterId`, `projektId`), keine `@ManyToOne`-Beziehung auf fremde Entities.
- Fachlogik und Rechteprüfung liegen **nur in der `service`-Schicht**. Die `view` ruft ausschließlich den eigenen Service auf, nie ein Repository, und enthält keine Fachregeln. Ausgeblendete Buttons sind keine Sicherheit.
- Bleib beim Umfang: erst alle **Muss**-Anforderungen. **Kann**-Anforderungen sind laut Teamentscheidung (04.11.2026) **zurückgestellt**: nicht bauen, keine Oberfläche und keine Fachlogik dafür, nur nach ausdrücklicher Freigabe durch die Person vor dem Bildschirm. Die **Schnittstellen dafür** bereitest Du vor (Abschnitt 11). Bau nichts, was nicht verlangt ist (kein GPS, keine KI, keine mobile App, keine Lohn-/Rechnungs-/Zahlungsfunktionen, keine Schichtmodelle).
- **Rückfragen:** Bei Unklarheit, Lücken oder Widersprüchen im Dokument frag die Person vor dem Bildschirm (kurz, mit Deinem Vorschlag, per AskUserQuestion). Rate nicht. Alles aus „Offene Punkte“ ist ohne Antwort **nicht** selbst zu entscheiden; Standardannahmen in diesem Dokument gelten nur bis zur Antwort. Teamweite Fragen schreibst Du in den Status als „Teamfrage“ (die Person bringt sie in die Mittwochsrunde).

## 2. Technik und Code-Regeln (einfach und schlank)
- Java 21, Maven, Spring Boot mit Vaadin Flow (Oberfläche läuft im Browser, Logik auf dem Server), Spring Data JPA, JUnit 5; Docker Compose für Anwendung + Datenbank. **Versionen und Konfiguration stehen in der `pom.xml` und im Gerüst – dort nachsehen, nicht raten.** **Datenbank: PostgreSQL** (Teamentscheidung 04.11.2026, läuft im Docker Compose des Gerüsts). Trotzdem Standard-JPA/JPQL ohne PostgreSQL-spezifische Funktionen oder native Queries, außer es geht nicht anders (dann im Status begründen). Wie Datenbank-Tests laufen (Testcontainers, H2 im PostgreSQL-Modus oder laufender Container), legt das Gerüst fest: dort nachsehen, nicht selbst einführen; fehlt es, Teamfrage.
- Das Gerüst (`de.timecraft.geruest`: `config`, `layout`, `common`) gehört dem Team. Lies es, bevor Du anfängst (Navigation/Menüeintrag, Clock/Zeitzone, Fehlerklassen, wie das Schema entsteht). Fehlt etwas, das Du brauchst, oder ist es kaputt: stoppen und nachfragen.
- Schlicht bauen: kleine Klassen, kurze Methoden, Konstruktor-Injektion, wenig Annotationen, keine Vererbungshierarchien oder Frameworks ohne Not, kein Lombok (außer das Gerüst nutzt es), keine Reflection, keine Generik-Akrobatik. Lieber zehn Zeilen mehr, die jede Person im Team sofort versteht.
- Namen: deutsche Fachbegriffe wie im Dokument (Zeiteintrag, Projekt, Buchung …), ASCII-Umlaute (`ae`, `oe`, `ue`, `ss`), Bezeichner und Kommentare deutsch, Oberfläche und Fehlermeldungen **ausschließlich deutsch** und für Menschen verständlich (nie technische Fehlermeldungen oder Stacktraces in der Oberfläche).
- Tabellen und Spalten müssen **1:1 zum Relationenmodell** passen (Namen, Typen, Längen, Pflichtangaben). Änderungen am Datenmodell: nicht selbst, Teamfrage.
- Zeit: immer die `Clock`/Zeitzone **Europe/Berlin** aus dem Gerüst injizieren; niemals `Instant.now()`/`LocalDateTime.now()` direkt im Code (Tests müssen die Uhr festlegen können). Geld mit `BigDecimal`. Dauern in Minuten (`int`).
- Ungültige Eingaben → verständliche Meldung am Feld, **nichts wird gespeichert**, nie ein Absturz (QA-A-02). Mehrere zusammengehörige Schreibvorgänge in **einer** `@Transactional`-Methode.
- Kein `System.out`, kein auskommentierter Code, keine unbenutzten Klassen, keine TODOs ohne Eintrag im Status.
- Oberfläche: nutzbar in Chrome, Firefox, Edge; Hauptfunktionen mit wenigen Klicks; Listen laden mit dem Demo-Datensatz (etwa 50 Mitarbeiter, 20 Projekte) in unter 2 Sekunden. Es gibt nur lokalen Betrieb mit erfundenen Testdaten.

## 2a. Design: Mockups sind die Vorlage
Im Projektordner `Mockups/` liegen die Designs als **.png-Dateien** (je Seite oder Dialog eine Datei, benannt nach der View, z. B. `StempelView.png`). Es gibt dazu einen Prototyp, der so aussieht wie das fertige Programm aussehen soll. **Die fertige Oberfläche muss optisch den Mockups entsprechen.**
- **Vor jeder View** das passende Mockup ansehen (PNG mit dem Read-Werkzeug öffnen) und danach bauen: Aufbau, Reihenfolge der Elemente, Spalten, Buttons, Beschriftungen (wörtlich), Farben, Abstände und Schrift übernehmen. Auch Dialoge und Reiter, die Dein Modul betreffen.
- Eigene Views nur für Dein Modul bauen. Gemeinsame Gestaltung (Kopfzeile, Menü, Theme) kommt aus dem Gerüst (`src/main/frontend/themes/timecraft/` und `geruest.layout`): nicht neu erfinden, nicht verändern. Passt das Gerüst nicht zum Mockup, Teamfrage in den Status eintragen.
- Standard-Vaadin-Komponenten und das gemeinsame Theme nutzen, keine Zusatzbibliothek, kein eigenes CSS pro Modul, außer das Mockup verlangt es zwingend (dann in den Status schreiben).
- Fehlt ein Mockup, ist die Zuordnung unklar oder widerspricht es einer Anforderung: **nachfragen**, nicht erfinden. Wo das Mockup nichts festlegt (Leerzustand, Fehlermeldung), die einfache Vaadin-Standardlösung nehmen und unter „Entscheidungen“ im Status vermerken.
- Nach jeder fertigen View: Anwendung starten, die Seite im Browser öffnen und mit dem Mockup vergleichen (Screenshot, falls ein Browser-Werkzeug verfügbar ist). Abweichungen beheben oder im Status begründen. Desktop-Breite ab ca. 1280 px genügt.

## 3. Arbeitsweise: jede Sitzung ist frisch (ein Arbeitspaket pro Sitzung)
Du hast **kein Gedächtnis** zwischen Sitzungen. Alles Wichtige steht in dieser Datei, im Code, in den Tests, in git und in der Statusdatei `docs/status/M5.md`.

**Startsatz der Person:** „Arbeite das nächste offene Arbeitspaket ab.“ Dann:
1. Diese Datei und `docs/status/M5.md` lesen (fehlt sie: aus der Vorlage unten anlegen). `git status` und `git log -5` ansehen.
2. `mvn test` ausführen: Die **Ausgangslage muss grün** sein. Ist sie rot: erst melden, nicht überspielen.
3. Nur das **nächste offene** Arbeitspaket (AP) aus Abschnitt 10 nehmen. In 3–5 Zeilen sagen, was Du tun wirst (Dateien, Tests), dann sofort loslegen. Kein weiteres AP anfangen, auch wenn Zeit bleibt.
4. **Tests zuerst bzw. gleichzeitig:** Für jede Fachregel ein Test (Normalfall, Grenzfall, Fehlerfall, Rolle). Dann der einfachste Code, der ihn grün macht.
5. Fertig ist ein AP erst, wenn: (a) `mvn test` für das **gesamte** Projekt grün ist (alle Tests, nicht nur Deine), (b) der Review (Abschnitt 4) ohne offene Mängel durch ist, (c) die Statusdatei aktualisiert ist, (d) ein Commit auf `modul/m5-antraege` liegt (`M5: <AP-Name>`, kleine Commits, kein Push auf `main`, kein Force-Push).
6. Zum Schluss melden: erledigtes AP, Teststand, Review-Ergebnis, offene Fragen, nächstes AP. Dann die Sitzung beenden (die Person startet für das nächste AP eine **neue** Sitzung bzw. `/clear`).

**Statusdatei-Vorlage `docs/status/M5.md`:**
```
# Status M5 Anträge und Korrekturen
## Stand
- Erledigte AP: (Liste mit Datum/Commit)
- Nächstes AP: ...
- Teststand: mvn test grün/rot, Anzahl Tests
## Entscheidungen (mit Quelle: wer hat entschieden)
## Offene Fragen / Teamfragen
## Hinweise aus Reviews (nicht blockierend)
## Bekannte Abweichungen vom Dokument
```

**Ausnahmen, bei denen Du anhältst und fragst:** Test lässt sich nicht grün bekommen ohne fremdes Modul/Gerüst zu ändern; Anforderung widerspricht sich; Vertrag reicht nicht aus; neue Abhängigkeit nötig; Umfang eines AP sprengt eine Sitzung (dann AP in der Statusdatei teilen und nur den ersten Teil machen).

## 4. Review nach jedem Arbeitspaket (verpflichtend)
Ohne bestandenen Review ist ein AP nicht fertig. Der Review läuft in einem **unabhängigen Kontext**, damit nicht Du Deine eigene Arbeit bewertest: Starte dafür mit dem Agent-Tool einen Subagenten (Typ `general-purpose`, ohne Deinen Verlauf) mit folgendem Auftrag, sobald alle Tests grün sind:

> Du bist Reviewer für TimeCraft Modul M5 (Anträge und Korrekturen). Du prüfst nur und änderst nichts. Lies `CLAUDE.md` im Repo-Root und `docs/status/M5.md`, dann `git diff` des aktuellen Arbeitspakets (uncommitted Änderungen bzw. letzter Commit) und die geänderten Dateien. Führe `mvn test` aus. Prüfe:
> 1. **Anforderungen:** Ist jede Muss-Anforderung des Arbeitspakets umgesetzt und durch einen Test belegt (nenne die FA-/QA-IDs)? Wurde etwas gebaut, das nicht verlangt ist?
> 2. **Schichten:** Keine Fachlogik oder Repository-Zugriffe in `view`; Rechte und Validierung nur im `service`; Rechte werden auch bei direktem Service-Aufruf geprüft (QA-A-01, QA-A-05).
> 3. **Grenzen:** Der Diff berührt nur Pakete dieses Moduls, dessen Tests, `docs/status/` und die im Vertrag vorgesehenen api-Vorlagen. Kein Zugriff auf fremde `service`/`repository`/`model`; fremde Daten nur per ID; Vertragstexte unverändert; keine neue Abhängigkeit in der pom.xml ohne Freigabe.
> 4. **Daten:** Entities passen 1:1 zum Relationenmodell (Tabellen, Spalten, Typen, Längen); keine PostgreSQL-spezifischen Funktionen ohne Not; Transaktionen dort, wo mehrere Schreibvorgänge zusammengehören.
> 5. **Fehlerverhalten:** Ungültige Eingaben ergeben verständliche deutsche Meldungen und es wird nichts gespeichert; keine technischen Fehlertexte in der Oberfläche.
> 6. **Tests:** Sie laufen grün, prüfen echtes Verhalten (nicht nur Mocks), decken Grenzfälle, Fehlerfälle und alle Rollen ab; keine `@Disabled`-Tests, keine zeitabhängigen Tests ohne feste Uhr.
> 7. **Einfachheit:** Schlichter, verständlicher Code ohne unnötige Abstraktion; keine toten Teile, keine Duplikate, klare Namen.
> 8. **Design:** Für jede neue oder geänderte View das passende Mockup in `Mockups/` (PNG) öffnen und Aufbau, Beschriftungen, Spalten und Buttons vergleichen; Abweichungen benennen. Keine Zusatzbibliothek, kein modulspezifisches CSS ohne Begründung im Status.
> Antworte als Liste mit Schwere (**Blocker** = falsch/unsicher/verletzt Grenze, **Mangel** = Anforderung/Test fehlt oder unnötig kompliziert, **Hinweis**), je mit Datei:Zeile und kurzer Begründung. Wenn nichts zu beanstanden ist, antworte „OK“.

Blocker und Mängel behebst Du, danach `mvn test` und **erneut ein frischer Reviewer**, bis „OK“ kommt (nur Hinweise sind erlaubt, sie kommen in den Status). Das Ergebnis steht in der Abschlussmeldung. Zusätzlich gilt: Vor dem Zusammenführen in `main` prüft eine **zweite Person den Pull Request** (QA-A-05); Du hilfst dabei nicht durch „Durchwinken“.

## 5. Definition of Done für das Modul
- Alle Muss-Anforderungen aus Anhang A sind umgesetzt und bedienbar, durch die Anmeldung abgesichert (Rechte im Service).
- **Alle Tests im Projekt sind grün** (`mvn test`), Fachlogik ist ohne Oberfläche durch JUnit geprüft (QA-A-06); Negativtests je Rolle vorhanden.
- Alle Views Deines Moduls sehen aus wie die Mockups in `Mockups/` (Abschnitt 2a), Abweichungen sind im Status begründet.
- Review ohne offene Blocker/Mängel, Statusdatei aktuell, Pull Request mit Durchsicht durch eine zweite Person.
- Modul läuft einzeln (mit Fakes, Profil `fake`) und nach dem Merge gegen die echten Module. Die Oberfläche wurde einmal von Hand durchgeklickt (Anwendung starten und die Hauptabläufe prüfen; ist kein Browser-Werkzeug verfügbar, bitte die Person um Sichtprüfung und halte das im Status fest).

## 6. Rollen und Rechte in diesem Modul
Rollen: MITARBEITER, PROJEKTLEITUNG (ist zugleich Mitarbeiter), ADMIN. Das System (z. B. automatische Funktionen) ist ein interner Akteur.

| Rolle | Darf in M5 |
|---|---|
| Mitarbeiter | Anträge erstellen, eigene ansehen, offene bearbeiten und zurückziehen, Entscheidung und Hinweis ansehen |
| Projektleitung | zusätzlich zuständige offene Anträge aller Typen ansehen, filtern, prüfen, genehmigen oder ablehnen; (ist selbst auch Mitarbeiter) |
| Admin | keine eigenen Use Cases in M5 |

## 7. Pakete, Klassen und Datenmodell
Basispaket `de.timecraft`. Klassennamen sind ein Vorschlag aus dem Paketdiagramm, Abweichungen nur in Absprache.

- `view`: AntragListeView, AntragFormular, AntragPruefungView
- `service`: AntragService, EntscheidungService, AntragRegelnService
- `repository`: AntragRepository
- `model`: Antrag (Basisklasse, siehe Vertrag), Antragstyp, Antragsstatus (Vertrag), Zeitkorrekturantrag, Abwesenheitsantrag, Stornoantrag, Entscheidung
- `api`: keine (außer der Basisklasse `Antrag`, die M4 erbt)

**Tabellen (Relationenmodell, Dein Modul):**
- `antrag`: antrag_id PK, antragsteller_id FK→mitarbeiter, entscheider_id FK→mitarbeiter (null bis entschieden), bezugsantrag_id FK→antrag (für Änderung/Storno), typ VARCHAR(20) (Diskriminator), status VARCHAR(15), eingereicht_am TIMESTAMP, begruendung VARCHAR(255), entschieden_am TIMESTAMP, entscheidungshinweis VARCHAR(255)
- `abwesenheitsantrag`: antrag_id PK/FK, art VARCHAR(10) (URLAUB/KRANKHEIT/SONSTIGE), urlaubsart VARCHAR(20), von DATE, bis DATE
- `zeitkorrekturantrag`: antrag_id PK/FK, zeiteintrag_id FK (nur ID), neues_projekt_id FK→projekt (null erlaubt, nur ID), alter_beginn, altes_ende, alter_standort VARCHAR(12), neuer_beginn, neues_ende, neuer_standort VARCHAR(12)
- `stornoantrag`: antrag_id PK/FK→antrag, **sonst keine Spalten** (**Entscheidung 04.11.2026: eigene Untertabelle**; das Team zieht das Relationenmodell nach). Bezug auf den zu stornierenden Antrag in `antrag.bezugsantrag_id`, Grund in `antrag.begruendung` (Schnittstellenvertrag unverändert).
- `materialanpassungsantrag` gehört M4.
- Fremde Spalten (zeiteintrag_id, projekt_id, mitarbeiter) als einfache IDs mappen, **nie** `@ManyToOne` auf Entities anderer Module.

### Fachregeln
- **Erstellen:** Typ wählen → typabhängige Felder. Zeitkorrektur: betroffene Buchung, ursprüngliche (vom System aus M2 gelesene) und gewünschte Werte. Urlaub: Urlaubsart, Start, Ende, optional Begründung; Urlaubstage via `AbwesenheitsUebernahme.urlaubstage`. Krankheit/sonstige: Abwesenheitsart + Zeitraum. Gespeichert werden Antrag-ID, Typ, Antragsteller, Fachbezug, Werte, Zeitpunkt, Status.
- **Prüfung (`AntragRegelnService`):** Pflichtfelder, Zeiträume (Ende nicht vor Beginn), Überschneidungen (mit bestehenden Abwesenheiten über `ueberschneidetAbwesenheit` und mit eigenen offenen/genehmigten Anträgen), typabhängige Regeln; Zeitkorrektur nur für eigene, abgeschlossene Buchung; Zielprojekt aktiv und dem Antragsteller zugeordnet (`ProjektAuskunft`). Fehler → verständliche Meldung, nichts (auch nichts teilweise) gespeichert.
- **Status:** OFFEN → GENEHMIGT | ABGELEHNT | ZURUECKGEZOGEN. Eigene offene Anträge können bearbeitet oder zurückgezogen werden. Doppelte Entscheidungen und widersprüchliche Anträge werden verhindert (Status im Service innerhalb der Transaktion prüfen; Zeile pessimistisch sperren).
- **Genehmigen:** Zuständigkeit, Status und Fachregeln erneut prüfen; Antrag **und** Fachdaten in M2 in einer Transaktion ändern (`@Transactional`; bei Fehler vollständiger Rollback, Antrag bleibt OFFEN). Fachdaten ändern sich nur nach Genehmigung. **Ablehnen:** Fachdaten unverändert, optionaler Entscheidungshinweis sichtbar für den Antragsteller.
- **Änderung/Storno genehmigter Anträge:** über einen verknüpften Antrag (`bezugsantrag_id`), der selbst wieder genehmigt werden muss.
- **Nachvollziehbarkeit:** Status, Entscheider, Zeitstempel, ursprüngliche Werte bleiben erhalten (nie überschreiben, nie löschen).
- **Rechte:** serverseitig im Service nach Eigentümer, Rolle und Zuständigkeit (Antragsteller sieht nur eigene; Entscheider nur zuständige).
- **Zuständigkeit:** Standard dieser Datei: der Vorgesetzte des Antragstellers (`MitarbeiterInfo.vorgesetzterId`, FA-M1-19: „genehmigt Urlaubs- und Zeitkorrekturanträge“). Siehe offene Punkte.

## 8. Schnittstellenvertrag
Die Module arbeiten **nur** über diese Schnittstellen zusammen (RB-A-03). Die Texte unten sind der **Schnittstellenvertrag (vom Team bestätigt am 04.11.2026; Änderungen nur durch den Besitzer des Interfaces nach Absprache)**. Er ist in allen sieben CLAUDE.md-Dateien wortgleich, damit jede Person ihr Modul allein bauen kann.

**Regeln:**
- Ein Interface/eine Klasse aus dem Vertrag wird **nur vom Besitzer geändert** und nur nach Absprache im Team. Brauchst Du eine Änderung oder eine neue Methode: nicht selbst ändern, sondern als „Teamfrage“ in den Status schreiben und die Person vor dem Bildschirm fragen.
- Die api-Methoden prüfen **keine Rechte**. Der aufrufende Service prüft sie (QA-A-01).
- Fehler (ungültige Eingabe, kein Zugriff, nicht angemeldet) meldet eine **Fachausnahme** aus dem Gerüst (`geruest.common`). Nachsehen, welche Klassen es dort gibt, und diese verwenden. Fehlt etwas: als Teamfrage notieren, nicht selbst im Gerüst anlegen.
- Zeitpunkte in der api sind `Instant`; Tage `LocalDate`; Monate `YearMonth`; Geld `BigDecimal`; Dauer in Minuten (`int`). Verweise auf Mitarbeiter/Projekte nur per ID.

| Schnittstelle | Besitzer | Genutzt von |
|---|---|---|
| MitarbeiterAuskunft (+MitarbeiterInfo, UrlaubsDaten, Rolle) | M1 benutzer | M2, M3, M4, M5, M6, M7 |
| BuchungsAuskunft, ZeitkontoAuskunft | M2 zeiterfassung | M3, M4, M5, M6, M7 |
| AbwesenheitsUebernahme, BuchungsAenderung | M2 zeiterfassung | M5, M6 |
| ProjektAuskunft (+ProjektInfo) | M3 projekte | M2, M4, M5, M6, M7 |
| AngebotspreisPflege (nur Angebotspreis setzen) | M3 projekte | M4 |
| MaterialAuskunft (nur Kann) | M4 material | M2 |
| Antrag (Basisklasse, Ausnahme: Klasse statt Interface) | M5 antraege | M4 |

### Das stellst Du bereit (Du bist Besitzer von M5) – exakt so implementieren/anlegen

**`src/main/java/de/timecraft/antraege/model/Antragsstatus.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M5.
package de.timecraft.antraege.model;

public enum Antragsstatus { OFFEN, GENEHMIGT, ABGELEHNT, ZURUECKGEZOGEN }
```

**`src/main/java/de/timecraft/antraege/model/Antrag.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M5.
package de.timecraft.antraege.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Basisklasse aller Anträge (Tabelle antrag). M4 erbt davon; Untertabellen per JOINED. */
@Entity
@Table(name = "antrag")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "typ", length = 20)
public abstract class Antrag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "antrag_id") private Long id;
    @Column(name = "antragsteller_id", nullable = false) private long antragstellerId;
    @Column(name = "entscheider_id") private Long entscheiderId;
    @Column(name = "bezugsantrag_id") private Long bezugsantragId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 15)
    private Antragsstatus status = Antragsstatus.OFFEN;
    @Column(name = "eingereicht_am", nullable = false) private Instant eingereichtAm;
    @Column(name = "begruendung", length = 255) private String begruendung;
    @Column(name = "entschieden_am") private Instant entschiedenAm;
    @Column(name = "entscheidungshinweis", length = 255) private String entscheidungshinweis;

    // Standard-Getter und -Setter für alle Felder, in der Reihenfolge der Felder (getId, getAntragstellerId, setAntragstellerId, ...).
    // Es gibt keinen setId.
}
```

### Das nutzt Du (gehört anderen Modulen) – nicht implementieren

Fehlt die Datei in Deinem Branch, lege sie **mit genau diesem Text** unter dem angegebenen Pfad an (inklusive Kopfzeile). Dann sind beim späteren Merge beide Fassungen identisch und es gibt keinen Konflikt. Die **Implementierung** des fremden Moduls schreibst Du nie; für Tests und lokalen Betrieb nimmst Du die Fakes (Abschnitt „Fakes“).

**`src/main/java/de/timecraft/benutzer/api/Rolle.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

public enum Rolle { MITARBEITER, PROJEKTLEITUNG, ADMIN }
```

**`src/main/java/de/timecraft/benutzer/api/MitarbeiterInfo.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Lesende Sicht auf einen Mitarbeiter (keine Entität). */
public record MitarbeiterInfo(
        long mitarbeiterId, String personalnummer, String vorname, String nachname,
        Rolle rolle, boolean aktiv, Long vorgesetzterId, LocalDate geburtsdatum,
        BigDecimal wochenstunden, Set<DayOfWeek> arbeitstage,
        LocalDate zeitkontoStartdatum, int startsaldoMinuten) {

    public String name() { return vorname + " " + nachname; }
}
```

**`src/main/java/de/timecraft/benutzer/api/UrlaubsDaten.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

public record UrlaubsDaten(int jahr, int anspruchTage, int uebertragTage) { }
```

**`src/main/java/de/timecraft/benutzer/api/MitarbeiterAuskunft.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M1.
package de.timecraft.benutzer.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface MitarbeiterAuskunft {
    /** Angemeldete Person (aus der Sitzung). Wirft eine Fachausnahme, wenn niemand angemeldet ist. */
    MitarbeiterInfo angemeldetePerson();
    /** Auch deaktivierte Mitarbeiter. */
    Optional<MitarbeiterInfo> finde(long mitarbeiterId);
    List<MitarbeiterInfo> aktive();
    /** Inklusive ehemaliger Mitarbeiter. */
    List<MitarbeiterInfo> alle();
    Optional<UrlaubsDaten> urlaubsdaten(long mitarbeiterId, int jahr);
    /** Vertraulich (nur Projektleitung/Admin dürfen es sehen) – für M4. */
    Optional<BigDecimal> stundensatz(long mitarbeiterId);
}
```

**`src/main/java/de/timecraft/zeiterfassung/api/Standort.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

public enum Standort { BUERO, HOMEOFFICE, UNTERWEGS }
```

**`src/main/java/de/timecraft/zeiterfassung/api/BuchungInfo.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

/** ende == null: Buchung läuft noch (nettoMinuten dann 0). */
public record BuchungInfo(
        long zeiteintragId, long mitarbeiterId, Long projektId, Standort standort,
        Instant beginn, Instant ende, int pausenMinuten, int nettoMinuten, boolean geaendert) {

    public boolean laeuft() { return ende == null; }
}
```

**`src/main/java/de/timecraft/zeiterfassung/api/ZeitSegment.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;
import java.time.LocalDate;

/** Anrechenbarer Zeitanteil einer abgeschlossenen Buchung an einem Kalendertag (Europe/Berlin), Pausenabzug schon durch M2. */
public record ZeitSegment(
        long zeiteintragId, long mitarbeiterId, Long projektId, Standort standort,
        LocalDate tag, Instant beginn, Instant ende, int anrechenbareMinuten) { }
```

**`src/main/java/de/timecraft/zeiterfassung/api/AenderungsArt.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

public enum AenderungsArt { AENDERUNG, KORREKTUR, NACHTRAG }
```

**`src/main/java/de/timecraft/zeiterfassung/api/AenderungInfo.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

public record AenderungInfo(
        Instant zeitpunkt, long geaendertVonMitarbeiterId, AenderungsArt art,
        String feld, String alterWert, String neuerWert, String begruendung) { }
```

**`src/main/java/de/timecraft/zeiterfassung/api/AbwesenheitsArt.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

public enum AbwesenheitsArt { URLAUB, KRANKHEIT, SONSTIGE }
```

**`src/main/java/de/timecraft/zeiterfassung/api/BuchungsAuskunft.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface BuchungsAuskunft {
    Optional<BuchungInfo> buchung(long zeiteintragId);
    Optional<BuchungInfo> laufendeBuchung(long mitarbeiterId);
    List<BuchungInfo> laufendeBuchungenAufProjekt(long projektId);
    /** Buchungen, die das Intervall [von, bis) berühren (laufende und abgeschlossene). */
    List<BuchungInfo> buchungen(long mitarbeiterId, Instant von, Instant bis);
    /** Nur abgeschlossene Buchungen, je Kalendertag (Europe/Berlin) zerlegt, Monatsgrenzen eingehalten. */
    List<ZeitSegment> segmente(long mitarbeiterId, YearMonth monat);
    List<ZeitSegment> segmenteProjekt(long projektId, YearMonth monat);
    List<AenderungInfo> historie(long zeiteintragId);
}
```

**`src/main/java/de/timecraft/zeiterfassung/api/AbwesenheitsUebernahme.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.LocalDate;
import java.util.Optional;

/** Wird von M5 innerhalb dessen Transaktion aufgerufen (Genehmigung muss atomar sein). */
public interface AbwesenheitsUebernahme {
    /** Urlaubstage im Zeitraum ohne Feiertage und arbeitsfreie Tage laut Arbeitszeitmodell. */
    int urlaubstage(long mitarbeiterId, LocalDate von, LocalDate bis);
    boolean ueberschneidetAbwesenheit(long mitarbeiterId, LocalDate von, LocalDate bis);
    /** Legt die genehmigte Abwesenheit an und gibt deren ID zurück. */
    long uebernehmen(long antragId, long mitarbeiterId, AbwesenheitsArt art, LocalDate von, LocalDate bis);
    void aufheben(long abwesenheitId);
    Optional<Long> abwesenheitZuAntrag(long antragId);
}
```

**`src/main/java/de/timecraft/zeiterfassung/api/BuchungsAenderung.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M2.
package de.timecraft.zeiterfassung.api;

import java.time.Instant;

/**
 * M2 prüft alle Integritätsregeln (Ende nach Beginn, nicht in der Zukunft, keine Überschneidung,
 * Buchung nicht laufend) und schreibt Buchung + Protokoll in EINER Transaktion.
 * Bei Fehlern: Fachausnahme mit verständlicher deutscher Meldung, nichts wird gespeichert.
 * Rechte und Projektregeln (wer darf ändern, Zielprojekt aktiv/zugeordnet) prüft der Aufrufer.
 */
public interface BuchungsAenderung {
    void aendern(long zeiteintragId, Instant beginn, Instant ende, Standort standort, Long projektId,
                 AenderungsArt art, long geaendertVonMitarbeiterId, String begruendung);

    /** Gibt die ID der neuen Buchung zurück. */
    long nachtragen(long mitarbeiterId, Instant beginn, Instant ende, Standort standort, Long projektId,
                    long geaendertVonMitarbeiterId, String begruendung);
}
```

**`src/main/java/de/timecraft/projekte/api/ProjektInfo.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjektInfo(
        long projektId, String name, LocalDate startdatum, LocalDate zieldatum,
        long projektleitungId, boolean aktiv, BigDecimal angebotspreis) { }
```

**`src/main/java/de/timecraft/projekte/api/ProjektAuskunft.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.util.List;
import java.util.Optional;

/** Keine Rechteprüfung hier: Die aufrufende Service-Schicht prüft die Rechte. */
public interface ProjektAuskunft {
    Optional<ProjektInfo> finde(long projektId);
    /** Aktive Projekte, denen der Mitarbeiter zugeordnet ist (Auswahl beim Einstempeln). */
    List<ProjektInfo> aktiveProjekteVon(long mitarbeiterId);
    /** Alle Projekte (aktiv und archiviert), die diese Projektleitung verantwortet. */
    List<ProjektInfo> verantworteteProjekte(long projektleitungId);
    List<ProjektInfo> alle();
    List<Long> zugeordneteMitarbeiter(long projektId);
    boolean istZugeordnet(long mitarbeiterId, long projektId);
}
```

## 9. Fakes: Entwickeln ohne fremde Module
- **Tests:** Fremde api-Interfaces werden in Tests durch einfache Fakes (kleine Klassen im Testordner `src/test/java/de/timecraft/antraege/fake/`) oder Mockito ersetzt. Nimm das Einfachere. Fakes bilden das **Vertragsverhalten** nach (auch Fehlerfälle) und enthalten Beispieldaten für alle drei Rollen.
- **Lokal starten:** Fakes, die für die laufende Oberfläche nötig sind, liegen in `src/main/java/de/timecraft/antraege/fake/` und sind mit `@Profile("fake")` markiert. Start: `mvn spring-boot:run -Dspring-boot.run.profiles=fake`. Die angemeldete Person des Fakes ist über eine Property wählbar (z. B. `timecraft.fake.rolle=PROJEKTLEITUNG`), damit Du alle Rollen ausprobieren kannst. Die echten Beans der anderen Module gibt es in Deinem Branch nicht; nach dem Merge ist das Profil `fake` einfach nicht aktiv.
- Fakes gehören zu Deinem Modul und werden nie für andere Module geschrieben oder von ihnen benutzt.

Für Dein Modul konkret:
- `MitarbeiterAuskunft` (Vorgesetzte), `BuchungsAuskunft` (eine abgeschlossene Buchung), `ProjektAuskunft`, `AbwesenheitsUebernahme` und `BuchungsAenderung` als aufzeichnende Fakes (merken sich die Aufrufe; ein Fake-Modus „wirft Fehler“ prüft den Rollback).

## 10. Arbeitspakete (Reihenfolge einhalten, ein AP pro Sitzung)
Jedes AP endet mit grünem `mvn test`, Review „OK“, Statusdatei und Commit (Abschnitt 3).

- **AP1 Model und Repository:** Basisklasse `Antrag` exakt wie im Vertrag, Zeitkorrekturantrag, Abwesenheitsantrag, Stornoantrag, Antragstyp; Repository; Mapping-Tests (JOINED, ein `Materialanpassungsantrag`-Stand-in im Test, um die Vererbung durch M4 zu prüfen).
- **AP2 AntragRegelnService:** Alle Prüfungen je Typ, rein testbar. Positiv- und Negativtests.
- **AP3 AntragService:** Erstellen, eigene ansehen, bearbeiten, zurückziehen, Filter für Entscheider; Rechte je Rolle.
- **AP4 EntscheidungService:** Genehmigen/Ablehnen, Atomarität mit den M2-Fakes, Doppelentscheidung, Rollback-Test (Fake wirft Fehler → Antrag bleibt OFFEN, nichts geändert).
- **AP5 Änderung/Storno:** Verknüpfte Anträge, Regeln, Tests.
- **AP6 Views:** AntragFormular (Typ wählen → passende Felder), AntragListeView (eigene Anträge mit Status/Entscheidungshinweis), AntragPruefungView (Liste, Filter, Detail, Genehmigen/Ablehnen). Entscheidung aus der Liste in zwei Klicks; Urlaubsantrag unter einer Minute.

## 11. Entscheidungen des Teams (Mittwochsrunde 04.11.2026)
Diese Punkte sind **entschieden**. Übernimm sie im ersten AP in die Statusdatei unter „Entscheidungen“ und baue danach. Weicht das Dokument ab, gilt die Entscheidung.

**Für alle Module:**
- **Schnittstellenvertrag** (Abschnitt 8) ist bestätigt, auch die neuen Teile `AngebotspreisPflege`, `Rolle` und `Standort`. Änderungen nur durch den Besitzer eines Interfaces nach Absprache, nie selbst.
- **Gerüst-Details** (Versionen, Schema-Erzeugung, Fehlerklassen, Clock/Zeitzone, Session-Timeout 30 Minuten) passen so, Tim verantwortet das Gerüst. Lies es und frage nur bei Lücken.
- **Gerüst-Annahmen** (nur wo das Gerüst nichts anderes vorgibt; Gerüst lesen geht vor): (1) Das Schema entsteht aus den Entities (Hibernate), kein Flyway/Liquibase. (2) `mvn test` läuft ohne Docker (H2 im PostgreSQL-Modus); Betrieb mit PostgreSQL aus dem Docker Compose. (3) Eine `Clock`-Bean (Europe/Berlin) wird in Services injiziert, nie `now()` direkt. (4) Fachliche Fehler laufen über wenige Ausnahmeklassen in `geruest.common` (ungültige Eingabe, kein Zugriff, nicht angemeldet, nicht gefunden); die Oberfläche zeigt deren deutschen Text. (5) Die angemeldete Person (Mitarbeiter-ID und Rolle) liefert ein kleiner Helfer im Gerüst; Views reichen sie als Parameter an den Service weiter, damit Rechte im Service ohne Webkontext testbar sind. (6) Die Platzhalter-Anmeldung ist ein fester Benutzer je Rolle, den M1 durch die echte Benutzerverwaltung ersetzt. (7) Jedes Modul trägt seine Menüpunkte und Routen selbst ein; das Layout im Gerüst sammelt sie. Fehlt eines davon im Gerüst oder ist es anders gelöst: nachfragen, nicht selbst im Gerüst nachbauen.
- **Rollen und Konten:** Jedes Benutzerkonto hat genau eine Rolle. Ein Admin-Konto ist ein getrenntes Konto und nie zugleich Projektleitung (Person mit beiden Aufgaben = zwei Konten). Vorgesetzte sind immer Projektleitungen.
- **Termine:** Das Projekt braucht in den Dateien keine Abgabetermine. Plane nicht nach Terminen und frage nicht danach.
- **Maßgebliche Quelle** ist das zusammengeführte Dokument (Anhang A bis C). Die alte Projektskizze ist veraltet (anderer Modulzuschnitt).
- **Datenbank:** PostgreSQL (siehe Abschnitt 2).
- **Kann-Anforderungen:** erstmal rauslassen, aber die Schnittstellen dafür einbauen. Konkret: keine Kann-Oberfläche und keine Kann-Fachlogik; wohl aber die im Vertrag stehenden `api`-Interfaces (auch das reine Kann-Interface `MaterialAuskunft`), nullable Kann-Spalten des Relationenmodells und die kleinen Erweiterungspunkte aus der Modulliste unten. Die Kann-Arbeitspakete in Abschnitt 10 sind zurückgestellt.

**Für Dein Modul:**
- **Zuständige Projektleitung:** der Vorgesetzte des Antragstellers. Vorgesetzte haben immer die Rolle PROJEKTLEITUNG (Admin-Konten sind getrennt und entscheiden keine Anträge, M1-Entscheidung 04.11.2026).
- **Tabellen-Layout:** Untertabellen laut Relationenmodell (JOINED), wie in M4.
- **Storno:** **eigene Untertabelle `stornoantrag`** (Typ `STORNO`) nur mit `antrag_id`, weil Bezug (`bezugsantrag_id`) und Grund (`begruendung`) schon in `antrag` stehen. Das Team zieht das Relationenmodell nach.
- **Typ-Diskriminator:** ZEITKORREKTUR, ABWESENHEIT, STORNO, MATERIALANPASSUNG; URLAUB/KRANKHEIT/SONSTIGE stehen in `abwesenheitsantrag.art`.
- **Doppelentscheidung:** pessimistisches Sperren im Service (keine Versionsspalte).
- **Antragshistorie:** Status, Zeitstempel und Originalwerte genügen, keine Verlaufstabelle.
- **Kann zurückgestellt, Schnittstellen vorbereiten:** Die Felder `begruendung` und `entscheidungshinweis` der Basisklasse bleiben vollständig befüllbar, damit Begründung und Hinweis in der Oberfläche (Kann) später nur angezeigt werden müssen. Keine zusätzlichen Kann-Oberflächen.

## 12. Offene Punkte – nicht selbst entscheiden
Frage die Person vor dem Bildschirm, **bevor** Du etwas baust, das davon abhängt. Die Antwort kommt mit Datum in die Statusdatei.

- Keine offenen Fragen aus der Mittwochsrunde. Neue Fragen in den Status eintragen.

**Termine:** stehen nicht in dieser Datei. Bei Verzug bleiben die zurückgestellten Kann-Anforderungen weg.

## Anhang A: Anforderungen Modul M5 (Originaltext aus dem Dokument)
### Funktionale Anforderungen (Muss = Pflicht, Kann = optional)
- **FA-M5-01** (Mitarbeiter; Muss): Mitarbeiter können einen Antrag erstellen und dabei den Antragstyp Zeitkorrektur, Urlaub, Krankheit, sonstige Abwesenheit oder Änderung/ Storno auswählen.
- **FA-M5-02** (System; Muss): Das System zeigt abhängig vom Antragstyp die erforderlichen Eingabefelder.
- **FA-M5-03** (Mitarbeiter, System; Muss): Zeitkorrekturen enthalten betroffene Buchung, sowie ursprüngliche und gewünschte Werte.
- **FA-M5-04** (Mitarbeiter; Muss): Urlaubsanträge enthalten Urlaubsart, Startdatum, Enddatum und optional eine Begründung.
- **FA-M5-05** (Mitarbeiter; Muss): Krankheits- und Abwesenheitsmeldungen enthalten Abweseneheitsart und Zeitraum.
- **FA-M5-06** (System; Muss): Das System speichert Antrag-ID, Typ, Antragsteller, Fachbezug, Werte, Zeitpunkt und Status.
- **FA-M5-07** (System; Muss): Das System prüft Pflichtfelder, Zeiträume, Überschneidungen und typabhängige Fachregeln.
- **FA-M5-08** (Mitarbeiter; Muss): Mitarbeiter können eigene Anträge und deren Status ansehen.
- **FA-M5-09** (Mitarbeiter; Muss): Eigene offene Anträge können bearbeitet oder zurückgezogen werden.
- **FA-M5-10** (Projektleitung; Muss): Die Projektleitung kann zuständige offene Anträge aller Typen ansehen und filtern.
- **FA-M5-11** (Projektleitung; Muss): Die Projektleitung kann Anträge mit allen entscheidungsrelevanten Daten prüfen.
- **FA-M5-12** (Projektleitung; Muss): Die Projektleitung kann einen offenen Antrag genehmigen oder ablehnen.
- **FA-M5-13** (System; Muss): Bei Genehmigung aktualisiert das System die betroffenen Zeit-, Urlaubs- oder Abwesenheitsdaten atomar.
- **FA-M5-14** (System; Muss): Bei Ablehnung bleiben Fachdaten unverändert.
- **FA-M5-15** (Mitarbeiter, Projektleitung; Muss): Genehmigte Anträge werden über einen verknüpften Änderungs- oder Stornoantrag angepasst.
- **FA-M5-16** (System; Muss): Das System verhindert doppelte Entscheidungen und widersprüchliche Anträge.
- **FA-M5-17** (System; Muss): Antragshistorie, Entscheidung, Zeitstempel, und ursprüngliche Werte bleiben nachvollziehbar.
- **FA-M5-18** (System; Muss): Mitarbeiter sehen die Entscheidung und einen optionalen Entscheidungshinweis.
- **FA-M5-19** (System; Muss): Zugriffe werden nach Eigentümer, Rolle und Zuständigkeit serverseitig geprüft.

### Qualitätsanforderungen M5
- **QA-M5-01** [Korrektheit]: Fachdaten werden nur nach erfolgreicher Genehmigung geändert. *(Prüfung: JUnit- und Integrationstest)*
- **QA-M5-02** [Datenintegrität]: Antrag und betroffene Fachdaten werden atomar gespeichert. *(Prüfung: Negativtest, Rollback)*
- **QA-M5-03** [Sicherheit]: Nur Eigentümer und zuständige Projektleitungen erhalten Zugriff. *(Prüfung: Rollentest)*
- **QA-M5-04** [Nachvollziehbarkeit]: Status, Historie, Entscheidung und Originalwerte bleiben erhalten. *(Prüfung: Datenbankprüfung)*
- **QA-M5-05** [Bedienbarkeit]: Antragstypen werden in einem gemeinsamen, verständlichen Prozess angeboten. *(Prüfung: Probelauf)*
- **QA-M5-06** [Fehlertoleranz]: Ungültige Eingaben erzeugen verständliche Hinweise ohne Teilspeicherung. *(Prüfung: Negativtest)*

### Randbedingungen M5
- **RB-M5-01** [Modulbezug]: M5 verarbeitet alle Anträge. Fachinformationen können aus Benutzer-, Zeit-, Projekt-, Arbeitszeit- und Berichtfunktionen stammen.

### Gemeinsame Qualitätsanforderungen (gelten für alle Module)
- **QA-A-01** [Sicherheit]: Ohne Anmeldung ist kein Zugriff auf Daten möglich. Die Rollenrechte (Mitarbeiter, Projektleitung, Admin) werden in der Service-Schicht geprüft, nicht nur durch ausgeblendete Schaltflächen oder Menüpunkte. *(Prüfung: Tests je Rolle)*
- **QA-A-02** [Fehlertoleranz]: Fehlerhafte Eingaben führen zu einer verständlichen Meldung in der Oberfläche, nie zu einem Absturz oder einer technischen Fehlermeldung. *(Prüfung: Negativtests, Demo)*
- **QA-A-03** [Performance]: Listen und Ansichten laden mit dem Demo-Datensatz (etwa 50 Mitarbeiter, 20 Projekte) in unter zwei Sekunden. *(Prüfung: Messung im lokalen Betrieb)*
- **QA-A-04** [Datenintegrität]: Datensätze verweisen über IDs auf Mitarbeiter und Projekte, nie über Namen, und nur auf vorhandene Benutzer und Projekte. *(Prüfung: Tests, Datenbankconstraints)*
- **QA-A-05** [Wartbarkeit]: Jedes Modul folgt dem Schichtenaufbau des Gerüsts (View, Service, Repository). Fachlogik liegt nicht in den Views. Eine zweite Person prüft jeden Pull Request. *(Prüfung: Durchsicht per Pull Request)*
- **QA-A-06** [Testbarkeit]: Die Fachregeln jedes Moduls sind ohne Oberfläche durch JUnit-Tests prüfbar. *(Prüfung: JUnit, Testbericht)*
- **QA-A-07** [Portabilität]: Die Anwendung läuft in einem aktuellen Browser (z. B. Chrome, Firefox, Edge) und lässt sich lokal nach der Startanleitung starten. *(Prüfung: Probelauf auf einem zweiten Rechner)*

### Gemeinsame Randbedingungen (ohne Terminangaben)
- **RB-A-01** [Technik]: Java-Webanwendung mit Vaadin (Flow) auf einem Spring-Boot-Server. Die Oberfläche läuft im Browser, Fachregeln und Datenzugriff laufen auf dem Server. Anmeldung und Rollen werden über Spring Security abgesichert.
- **RB-A-02** [Datenbank]: SQL-Datenbank, die das gesamte Team gemeinsam nutzt. Das konkrete Produkt (z. B. H2) legt das Team später fest; die Module nutzen daher keine produktspezifischen Funktionen. **Entschieden am 04.11.2026: PostgreSQL.**
- **RB-A-03** [Architektur]: Je Modul eine Oberfläche, eine Service- und eine Repository-Schicht. Nur die Service-Schicht prüft Regeln und Rechte. Die Module arbeiten über definierte Schnittstellen (Service-Klassen) zusammen, das Datenmodell wird im Team abgestimmt.
- **RB-A-04** [Werkzeuge]: Maven für den Build, Git und GitHub (ein Branch je Modul, Pull Request mit Durchsicht durch eine zweite Person), Trello für Aufgaben, JUnit für die Fachlogik.
- **RB-A-05** [Oberfläche]: Die Oberfläche ist ausschließlich deutschsprachig und läuft in aktuellen Versionen von Chrome, Firefox und Edge.
- **RB-A-06** [Organisation]: Eine Person verantwortet ein Modul. Das Team arbeitet in Ein-Wochen-Sprints und trifft sich mittwochs um 10 Uhr.
- **RB-A-08** [Betrieb]: Nur lokaler Betrieb mit erfundenen Testdaten, keine Veröffentlichung.
- **RB-A-09** [Abgrenzung]: Der Standort wird von Hand gewählt, es gibt keine GPS-Ortung. Nicht Teil des Projekts sind KI und Business Intelligence, eine mobile App, Lohnabrechnung, Zahlungen und Schichtmodelle.
- **RB-A-10** [Lieferumfang]: Code im GitHub-Repository mit Startanleitung, Dokumentation, Demo mit Beispieldaten und Präsentation.

## Anhang B: Use Cases M5
### Überblick
- **UC-M5-01** Anträge ansehen – Akteur: Mitarbeiter, Projektleitung – Anforderungen: 
- **UC-M5-02** Antrag erstellen – Akteur: Mitarbeiter – Anforderungen: 
- **UC-M5-03** Antragstyp auswählen – Akteur: Mitarbeiter – Anforderungen: 
- **UC-M5-04** Antragsdaten erfassen – Akteur: Mitarbeiter – Anforderungen: 
- **UC-M5-05** Offenen Antrag bearbeiten – Akteur: Mitarbeiter – Anforderungen: 
- **UC-M5-06** Offenen Antrag zurückziehen – Akteur: Mitarbeiter – Anforderungen: 
- **UC-M5-07** Antrag prüfen – Akteur: Projektleitung – Anforderungen: 
- **UC-M5-08** Antrag genehmigen – Akteur: Projektleitung – Anforderungen: 
- **UC-M5-09** Antrag ablehnen – Akteur: Projektleitung – Anforderungen: 
- **UC-M5-10** Genehmigten Antrag ändern oder stornieren – Akteur: Mitarbeiter, Projektleitung – Anforderungen: 
- **UC-M5-11** Entscheidung anzeigen – Akteur: Mitarbeiter, Projektleitung – Anforderungen: 

### Beschriebene Use Cases (Standardablauf, Fehlerfälle: Grundlage für Tests)
#### UC-M5-02 Antrag erstellen
- *Ziel:* Einen Antrag beliebigen Typs vollständig und nachvollziehbar einreichen.
- *Akteur:* Mitarbeiter
- *Auslöser:* Mitarbeiter wählt “Antrag erstellen”.
- *Vorbedingung:* Mitarbeiter ist angemeldet.
- *Nachbedingung:* Antrag ist mit eindeutiger ID und passendem Anfangsstatus gespeichert.
**Standardablauf**
1. Antrag erstellen öffnen.
2. Antragstyp auswählen.
3. System zeigt typabhängiges Formular.
4. Antragsdaten und optionale Begründung erfassen.
5. Antrag absenden.
6. System prüft Rechte, Pflichtfelder, Zeiträume, Überschneidungen und Fachregeln.
7. System speichert den Antrag und zeigt eine Bestätigung.
**Alternativen und Fehlerfälle**
- 4a: Ungültige oder fehlende Daten -> keine Speicherung.
- 6a: Widersprüchlicher Zeitraum -> Antrag wird abgewiesen.
- Abbruch -> keine Speicherung.

#### UC-M5-06 Antrag genehmigen
- *Ziel:* Einen fachlich zulässigen offenen Antrag verbindlich genehmigen.
- *Akteur:* Zuständige Projektleitung
- *Auslöser:* Projektleitung wählt “Genehmigen” bei einem ausgewählten Antrag.
- *Vorbedingung:* Antrag ist offen und Projektleitung ist zuständig.
- *Nachbedingung:* Antrag ist genehmigt; betroffene Fachdaten sind atomar aktualisiert.
**Standardablauf**
1. Offenen Antrag auswählen.
2. System zeigt Antragstyp und entscheidungsrelevante Daten.
3. Projektleitung prüft den Antrag.
4. Projektleitung wählt “Genehmigen” und bestätigt.
5. System prüft Zuständigkeit, Status und typabhängige Fachregeln erneut.
6. System aktualisiert Antrag und betroffene Fachdaten in einer Transaktion.
7. System zeigt die Entscheidung.
**Alternativen und Fehlerfälle**
- 1a: Antrag wurde bereits entschieden -> Vorgang wird abgebrochen.
- 5a: Fachregeln verletzt: keine Änderung.
- Speicherfehler -> vollständiger Rollback.

## Anhang C: User Stories mit Akzeptanzkriterien M5 (Testgrundlage)
- **US-M5-01** Als Mitarbeiter möchtenich einen Antragstypen auswählen und passende Daten erfassen, damit ich unterschiedliche Anliegen über denselben Prozess einreichen kann. → *Akzeptanz:* Zeitkorrektur, Urlaub, Krankheit, sonstige Abwesenheiten und Änderung/ Storno sind auswählbar. Formular passt sich dem Typ an. Antrag wird mit Typ und Status gespeichert. *(Bezug: )*
- **US-M5-02** Als Mitarbeiter möchte ich Urlaub beantragen, damit meine Abwesenheit geprüft und geplant werden kann. → *Akzeptanz:* Urlaubsart und Zeitraum sind Pflichtangaben. Überschneidungen werden geprüft. Status ist sichtbar. *(Bezug: )*
- **US-M5-03** Als Mitarbeiter möchte ich eine Krankheit oder sonstige Abwesenheiten melden, damit diese korrekt dokumentiert wird. → *Akzeptanz:* Art und Zeitraum werden gespeichert. Widersprüchliche Zeiträume werden verhindert. Die Meldung erscheint in der Übersicht. *(Bezug: )*
- **US-M5-04** Als Mitarbeiter möchte ich eine Zeitbuchung korrigieren lassen, damit fehlerhafte Werte nachvollziehbar berichtigt werden. → *Akzeptanz:* Original- und Wunschwerte werden gespeichert. Mindestens ein Wert weicht ab. Original bleibt bis Genehmigung unverändert. *(Bezug: )*
- **US-M5-05** Als Projektleitung möchte ich all zuständigen offenen Anträge gemeinsam prüfen, damit ich sie einheitlich bearbeiten kann. → *Akzeptanz:* Filter nach Typ, Status, Mitarbeiter und Zietraum. Nur zuständige Anträge sichtbar. Typabhängige Prüfdaten werden angezeigt. *(Bezug: )*
- **US-M5-06** Als Projektleitung möchte ich Anträge genehmigen oder ablehnen, damit eine verbindliche Entscheidung vorliegt. → *Akzeptanz:* Nur offene Anträge sind entscheidbar. Entscheidung und Zeitpunkt werden gespeichert. Genehmigung aktualisert Fachdaten atomar. *(Bezug: )*
- **US-M5-07** Als Mitarbeiter möchte ich offene Anträge bearbeiten oder zurückziehen. → *Akzeptanz:* Nur eigene offene Anträge sind änderbar. Rückzug ändert keine Fachdaten. Historie bleibt erhalten. *(Bezug: )*
- **US-M5-08** Als Mitarbeiter möchte ich genehmigte Anträge ändern oder stornieren, damit Planänderungen nachvollziehbar bleiben. → *Akzeptanz:* Es entsteht ein verknüpfter Folgeantrag. Original bleibt historisiert. Fachdaten ändern sich erst nach der Entscheidung. *(Bezug: )*
