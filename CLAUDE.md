# CLAUDE.md – TimeCraft Modul M3 Projekte und Team-Übersicht

TimeCraft ist eine digitale Zeiterfassung mit Projektübersicht für Unternehmen und Abteilungen (Softwareprojekt Wirtschaftsinformatik, Hochschule Bielefeld). Sieben Personen entwickeln je ein Modul. **Du arbeitest für Tim Stukenbröker an genau einem Modul.** Programm bleibt lokal, nur erfundene Testdaten.

## 0. Kurzauftrag
Projekte anlegen, bearbeiten, archivieren; Projektliste mit Filtern; Detailfenster mit Reitern „Details“ und „Team“; Mitarbeiter einem Projekt zuordnen/entfernen; Team-Übersicht („wer arbeitet wo und woran“). Jedes Projekt hat eine verantwortliche Projektleitung. Du lieferst über `ProjektAuskunft` Projekte und Zuordnungen an M2, M4, M5, M6, M7.
Datensparsamkeit ist Kern: Zugeordnete Mitarbeiter sehen im Reiter „Team“ **nur die Namen** ihres Teams. Nur die verantwortliche Projektleitung sieht zusätzlich Status (eingestempelt/nicht), Standort und Beginn der laufenden Buchung auf dieses Projekt, aber keine Arbeitszeiten, Salden oder Buchungen auf andere Projekte. Admin, nicht zugeordnete Personen und Projektleitungen anderer Projekte sehen den Reiter gar nicht.

## 1. Dein Auftrag und Deine Grenzen (hart)
- Du entwickelst **ausschließlich Modul M3 Projekte und Team-Übersicht** im Paket `de.timecraft.projekte` (Unterpakete `view`, `service`, `repository`, `model`, `api`) plus Tests. Verantwortlich: Tim Stukenbröker. Branch: `modul/m3-projekte`.
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
Du hast **kein Gedächtnis** zwischen Sitzungen. Alles Wichtige steht in dieser Datei, im Code, in den Tests, in git und in der Statusdatei `docs/status/M3.md`.

**Startsatz der Person:** „Arbeite das nächste offene Arbeitspaket ab.“ Dann:
1. Diese Datei und `docs/status/M3.md` lesen (fehlt sie: aus der Vorlage unten anlegen). `git status` und `git log -5` ansehen.
2. `mvn test` ausführen: Die **Ausgangslage muss grün** sein. Ist sie rot: erst melden, nicht überspielen.
3. Nur das **nächste offene** Arbeitspaket (AP) aus Abschnitt 10 nehmen. In 3–5 Zeilen sagen, was Du tun wirst (Dateien, Tests), dann sofort loslegen. Kein weiteres AP anfangen, auch wenn Zeit bleibt.
4. **Tests zuerst bzw. gleichzeitig:** Für jede Fachregel ein Test (Normalfall, Grenzfall, Fehlerfall, Rolle). Dann der einfachste Code, der ihn grün macht.
5. Fertig ist ein AP erst, wenn: (a) `mvn test` für das **gesamte** Projekt grün ist (alle Tests, nicht nur Deine), (b) der Review (Abschnitt 4) ohne offene Mängel durch ist, (c) die Statusdatei aktualisiert ist, (d) ein Commit auf `modul/m3-projekte` liegt (`M3: <AP-Name>`, kleine Commits, kein Push auf `main`, kein Force-Push).
6. Zum Schluss melden: erledigtes AP, Teststand, Review-Ergebnis, offene Fragen, nächstes AP. Dann die Sitzung beenden (die Person startet für das nächste AP eine **neue** Sitzung bzw. `/clear`).

**Statusdatei-Vorlage `docs/status/M3.md`:**
```
# Status M3 Projekte und Team-Übersicht
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

> Du bist Reviewer für TimeCraft Modul M3 (Projekte und Team-Übersicht). Du prüfst nur und änderst nichts. Lies `CLAUDE.md` im Repo-Root und `docs/status/M3.md`, dann `git diff` des aktuellen Arbeitspakets (uncommitted Änderungen bzw. letzter Commit) und die geänderten Dateien. Führe `mvn test` aus. Prüfe:
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

| Rolle | Darf in M3 |
|---|---|
| Mitarbeiter | Zugeordnete Projekte ansehen; im Reiter „Team“ die Namen des Teams |
| Projektleitung | alle aktiven Projekte ansehen; für eigene (verantwortete) Projekte: Beschreibung ändern, Mitarbeiter zuordnen/entfernen, Team mit Status/Standort/Beginn sehen und filtern |
| Admin | Projekte anlegen, ändern (inkl. Projektleitung), archivieren/reaktivieren, alle Projekte ansehen; kein Reiter „Team“ |

## 7. Pakete, Klassen und Datenmodell
Basispaket `de.timecraft`. Klassennamen sind ein Vorschlag aus dem Paketdiagramm, Abweichungen nur in Absprache.

- `view`: ProjektListeView, ProjektDetailView, TeamTab, ProjektFormular, ZuordnungsDialog
- `service`: ProjektService, TeamService, ProjektBerechtigung
- `repository`: ProjektRepository (inkl. Zuordnungen)
- `model`: Projekt, Projektzuordnung, ProjektStatus (AKTIV, ARCHIVIERT), ProjektFilter, TeamFilter, TeamEintrag
- `api`: ProjektAuskunft, ProjektInfo (Vertrag, Du bist Besitzer)

**Tabellen (Relationenmodell, Dein Modul):**
- `projekt`: projekt_id PK (vom System vergeben), projektleitung_id FK→mitarbeiter (nur als ID, kein @ManyToOne in M1-Entity), name VARCHAR(100) (nicht eindeutig), beschreibung TEXT, startdatum DATE, zieldatum DATE, status VARCHAR(10), angebotspreis NUMERIC(10,2) (gehört fachlich M4: M4 setzt ihn über `AngebotspreisPflege`; M3 hat dafür keine Oberfläche)
- `projektzuordnung`: mitarbeiter_id PK/FK, projekt_id PK/FK, zugeordnet_am TIMESTAMP

### Fachregeln
- **Anlegen (Admin):** Name, Startdatum, Zieldatum, verantwortliche Projektleitung sind Pflicht; Zieldatum nicht vor Startdatum; Projektleitung wird aus den aktiven Mitarbeitern mit Rolle PROJEKTLEITUNG gewählt. Neues Projekt ist AKTIV, Projekt-ID wird vom System vergeben. Fehler → verständliche Meldung, nichts gespeichert.
- **Ändern:** Admin: Name, Beschreibung, Projektleitung, Start, Ziel. Verantwortliche Projektleitung: nur die Beschreibung ihres aktiven Projekts.
- **Archivieren/Reaktivieren (Admin):** Archivierte Projekte erscheinen nicht mehr in `aktiveProjekteVon` (Einstempeln gesperrt), Buchungen bleiben. **Ein Projekt wird nie gelöscht.**
- **Projektliste:** Spalten Projekt-ID, Name, Start, Ziel, Projektleitung, Status. Admin sieht alle; Projektleitung alle aktiven; Mitarbeiter nur zugeordnete. Filter (kombinierbar, wirken gemeinsam, innerhalb der erlaubten Projekte): Mitarbeiter, Status, Zeitraum (für Start- und Zieldatum je von/bis), Name (enthält, Groß/Klein egal); Zurücksetzen; Hinweis ohne Treffer. Archiviert in Grau.
- **Detailfenster:** Klick auf Projekt; Reiter Details und Team sowie ein Verweis-Reiter „Materialplanung“ (Inhalt gehört M4, siehe unten).
- **Zuordnen/Entfernen:** nur verantwortliche Projektleitung, nur bei aktivem eigenem Projekt; Auswahl aus Mitarbeitern, die noch nicht zugeordnet sind; ein Mitarbeiter in mehreren Projekten möglich; niemand ordnet sich selbst zu (Service verweigert direkten Aufruf); Entfernen lässt bereits gebuchte Zeiten bestehen. Hinweis, wenn alle schon zugeordnet sind.
- **Team-Übersicht:** Daten „eingestempelt?“ kommen aus `BuchungsAuskunft.laufendeBuchungenAufProjekt`. Stand beim Aufruf + „Aktualisieren“. Filter der Projektleitung: Status, Standort (Standort gilt nur für eingestempelte). Leeres Team: „Dem Projekt sind noch keine Mitarbeiter zugeordnet“. Der Service verweigert den Zugriff auch bei direktem Aufruf (z. B. per Link).
- **Angebotspreis:** Du bietest nur `AngebotspreisPflege.setzeAngebotspreis` (schreibt die Spalte, keine Rechteprüfung, keine Oberfläche); Rechte und Pflege liegen bei M4. `ProjektInfo.angebotspreis` liefert den Wert lesend.
- **Reiter „Materialplanung“ (M4):** Den Inhalt baut M4 als eigene Seite mit Route `projekt/{projektId}/material`. Dein `ProjektDetailView` zeigt dort nur einen Reiter/Link „Materialplanung“, der per Router auf diese Route verweist (keine Compile-Abhängigkeit auf M4; Routen-Text als Konstante). Der Reiter erscheint für Projektleitung und Admin, nicht für Mitarbeiter.
- **api:** `aktiveProjekteVon`, `verantworteteProjekte`, `alle`, `zugeordneteMitarbeiter`, `istZugeordnet`, `finde`. Bereits gebuchte Zeiten bleiben nach Entfernen einer Zuordnung verfügbar.
- Einziger erlaubter Zyklus: M2 liest Deine api, Du liest `BuchungsAuskunft` von M2 (nur über api).

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

### Das stellst Du bereit (Du bist Besitzer von M3) – exakt so implementieren/anlegen

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

**`src/main/java/de/timecraft/projekte/api/AngebotspreisPflege.java`**
```java
// VORLAGE aus dem Schnittstellenvertrag der CLAUDE.md – Text nicht verändern, Besitzer ist Modul M3.
package de.timecraft.projekte.api;

import java.math.BigDecimal;

/**
 * Schreibzugriff nur auf die Spalte projekt.angebotspreis (fachlich M4).
 * Keine Rechteprüfung hier: Der Aufrufer (M4) prüft, dass die angemeldete Person die
 * verantwortliche Projektleitung des aktiven Projekts ist.
 */
public interface AngebotspreisPflege {
    void setzeAngebotspreis(long projektId, BigDecimal angebotspreis);
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

## 9. Fakes: Entwickeln ohne fremde Module
- **Tests:** Fremde api-Interfaces werden in Tests durch einfache Fakes (kleine Klassen im Testordner `src/test/java/de/timecraft/projekte/fake/`) oder Mockito ersetzt. Nimm das Einfachere. Fakes bilden das **Vertragsverhalten** nach (auch Fehlerfälle) und enthalten Beispieldaten für alle drei Rollen.
- **Lokal starten:** Fakes, die für die laufende Oberfläche nötig sind, liegen in `src/main/java/de/timecraft/projekte/fake/` und sind mit `@Profile("fake")` markiert. Start: `mvn spring-boot:run -Dspring-boot.run.profiles=fake`. Die angemeldete Person des Fakes ist über eine Property wählbar (z. B. `timecraft.fake.rolle=PROJEKTLEITUNG`), damit Du alle Rollen ausprobieren kannst. Die echten Beans der anderen Module gibt es in Deinem Branch nicht; nach dem Merge ist das Profil `fake` einfach nicht aktiv.
- Fakes gehören zu Deinem Modul und werden nie für andere Module geschrieben oder von ihnen benutzt.

Für Dein Modul konkret:
- `MitarbeiterAuskunft` (mehrere Personen mit Rollen, angemeldete Person wählbar), `BuchungsAuskunft` (laufende Buchungen auf Projekte mit Standort/Beginn; Rest leer lassen).

## 10. Arbeitspakete (Reihenfolge einhalten, ein AP pro Sitzung)
Jedes AP endet mit grünem `mvn test`, Review „OK“, Statusdatei und Commit (Abschnitt 3).

- **AP1 Model und Repository:** Projekt, Projektzuordnung, ProjektStatus; Repository mit Filterabfrage; Tests (Zuordnung eindeutig, FK).
- **AP2 ProjektService + ProjektBerechtigung:** Anlegen/Ändern/Archivieren/Reaktivieren mit Validierung und Rechten je Rolle; Tests inkl. Negativtests (Mitarbeiter will anlegen, Projektleitung fremdes Projekt).
- **AP3 Projektliste und Filter:** Sichtbarkeit je Rolle, alle Filter einzeln und kombiniert, Zurücksetzen. Tests mit Beispieldaten je Rolle (QA-M3-01: je Rolle genau die erlaubten Projekte).
- **AP4 Zuordnung:** Zuordnen/Entfernen mit allen Regeln. Tests inkl. Selbstzuordnung, archiviertes Projekt, fremde Projektleitung.
- **AP5 TeamService:** Team-Übersicht mit Datensparsamkeit je Rolle (nur Namen vs. Status/Standort/Beginn vs. kein Zugriff), Filter. Tests je Rolle, auch direkter Service-Aufruf.
- **AP6 api-Implementierung:** ProjektAuskunft und AngebotspreisPflege exakt nach Vertrag; Tests.
- **AP7 Views:** ProjektListeView (Filterleiste, „+ Neues Projekt“ nur Admin), ProjektDetailView mit Reitern, TeamTab (+ „Mitarbeiter zuordnen“, Bestätigungsdialog zum Entfernen, „Aktualisieren“), ProjektFormular, Reiter „Materialplanung“ (nur Router-Verweis auf M4). Admin legt Projekt in höchstens 3 Schritten an; Team-Reiter mit einem Klick erreichbar.
- **AP8 Kann:** ZURÜCKGESTELLT (Teamentscheidung 04.11.2026): erst nach ausdrücklicher Freigabe, bis dahin nur die vorbereiteten Schnittstellen (Abschnitt 11). Inhalt: Freie Kapazität (K1: Zahl aktiver Zuordnungen je Mitarbeiter, sortierbar), automatische Aktualisierung des Reiters (K2).

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
- **Verantwortliche Projektleitung:** nur Rolle PROJEKTLEITUNG (FA-M3-01), nicht Admin.
- **Reiter „Materialplanung“:** Route `projekt/{projektId}/material`; M3 verweist nur per Router (Konstante), keine Abhängigkeit von M3 auf M4.
- **Angebotspreis:** wird in M4 gesetzt (über `AngebotspreisPflege`), nicht in M3.
- **Kann zurückgestellt, Schnittstellen vorbereiten:** Die Zuordnungs-Abfrage (aktive Zuordnungen je Mitarbeiter) bleibt als eigene Service-Methode erhalten, damit „freie Kapazität“ (K1) später nur eine Anzeige braucht. Keine Kapazitätsanzeige, keine Auto-Aktualisierung (K2).

## 12. Offene Punkte – nicht selbst entscheiden
Frage die Person vor dem Bildschirm, **bevor** Du etwas baust, das davon abhängt. Die Antwort kommt mit Datum in die Statusdatei.

- **Admin sieht den Reiter „Team“ nicht** – bewusst so (Datensparsamkeit), nicht „korrigieren“.

**Termine:** stehen nicht in dieser Datei. Bei Verzug bleiben die zurückgestellten Kann-Anforderungen weg.

## Anhang A: Anforderungen Modul M3 (Originaltext aus dem Dokument)
### Funktionale Anforderungen (Muss = Pflicht, Kann = optional)
- **FA-M3-01** (Admin; Muss): Der Admin kann ein Projekt mit Name, Beschreibung, Startdatum, Zieldatum und einer verantwortlichen Projektleitung anlegen. Die Projektleitung wählt er aus den Benutzern mit der Rolle Projektleitung. Das System vergibt dabei eine eindeutige Projekt-ID; der Name muss nicht eindeutig sein. Ein neues Projekt ist zunächst aktiv.
- **FA-M3-02** (Projektleitung, Admin; Muss): Der Admin kann Name, Beschreibung, Startdatum, Zieldatum und die verantwortliche Projektleitung eines Projekts ändern. Die verantwortliche Projektleitung kann im Detailfenster die Beschreibung ihres aktiven Projekts ändern, sonst nichts.
- **FA-M3-03** (Admin; Muss): Der Admin kann ein Projekt archivieren und wieder aktivieren. Auf ein archiviertes Projekt kann nicht mehr eingestempelt werden, die bisherigen Buchungen bleiben erhalten.
- **FA-M3-04** (Mitarbeiter, Projektleitung, Admin; Muss): Das System zeigt eine Projektliste mit Projekt-ID, Name, Startdatum, Zieldatum und Status. Der Admin sieht alle Projekte, die Projektleitung alle aktiven Projekte, ein Mitarbeiter nur die Projekte, denen er zugeordnet ist. Die Liste lässt sich nach Status, Zeitraum (Start- und Zieldatum) und Projektname filtern (siehe FA-M3-13).
- **FA-M3-05** (Mitarbeiter, Projektleitung, Admin; Muss): Ein Klick auf ein Projekt in der Liste öffnet ein Detailfenster mit Projekt-ID, Name, Beschreibung, Startdatum, Zieldatum, Status, verantwortlicher Projektleitung und zugeordneten Mitarbeitern. Das Fenster hat den Reiter „Details“ und den Reiter „Team“ für die zugeordneten Mitarbeiter und die verantwortliche Projektleitung des Projekts (FA-M3-08).
- **FA-M3-06** (Projektleitung; Muss): Die verantwortliche Projektleitung kann Mitarbeiter ihrem aktiven Projekt zuordnen. Die Auswahl erfolgt aus den Benutzern von M1; ein Mitarbeiter kann mehreren Projekten zugeordnet sein.
- **FA-M3-07** (Projektleitung; Muss): Die verantwortliche Projektleitung kann die Zuordnung eines Mitarbeiters zu ihrem Projekt entfernen. Bereits gebuchte Zeiten bleiben erhalten.
- **FA-M3-08** (Projektleitung; Muss): Der Reiter „Team“ im Detailfenster (Team-Übersicht) zeigt jedem dem Projekt zugeordneten Mitarbeiter die Namen aller Teammitglieder, also seines Teams, ohne Status, Standort oder Buchungszeiten. Der verantwortlichen Projektleitung zeigt er je Person zusätzlich den Status (eingestempelt oder nicht eingestempelt) und, bei eingestempelten Personen, Standort (Büro, Homeoffice, unterwegs) und Beginn der laufenden Buchung auf dieses Projekt. Er zeigt den Stand beim Aufruf und lässt sich über „Aktualisieren“ neu laden. Der Admin, nicht zugeordnete Mitarbeiter und die Projektleitung anderer Projekte sehen den Reiter nicht.
- **FA-M3-09** (Projektleitung; Muss): Die Ansicht der verantwortlichen Projektleitung im Reiter „Team“ lässt sich nach Status (eingestempelt, nicht eingestempelt) und nach Standort filtern. Der Standortfilter gilt nur für eingestempelte Personen.
- **FA-M3-10** (Mitarbeiter, Projektleitung, Admin; Muss): Alle Funktionen von M3 setzen eine Anmeldung voraus und stehen nur den Rollen offen, die in der Spalte „Rolle“ genannt sind. Die Funktionen der Projektleitung gelten nur für Projekte, deren verantwortliche Projektleitung sie ist.
- **FA-M3-11** (Admin; Muss): Das System prüft Eingaben: Projektname, Startdatum, Zieldatum und verantwortliche Projektleitung sind Pflicht, das Zieldatum liegt nicht vor dem Startdatum. Bei einem Fehler erscheint eine verständliche Meldung und nichts wird gespeichert.
- **FA-M3-12** (System; Muss): M3 stellt anderen Modulen die aktiven Projekte eines Mitarbeiters (Auswahl beim Einstempeln in M2) sowie Projekte mit ihren Zuordnungen (Monatsbericht in M7) bereit. Andere Module verweisen über die Projekt-ID auf Projekte; die Auswahl beim Einstempeln (Dropdown) zeigt jedes Projekt mit Name und Projekt-ID.
- **FA-M3-13** (Mitarbeiter, Projektleitung, Admin; Muss): Die Projektliste enthält Filter für Status (aktiv, archiviert), Zeitraum und Projektname. Den Zeitraumfilter gibt es für das Startdatum und für das Zieldatum, jeweils mit „von“ und „bis“; ein Projekt erscheint, wenn das Datum im gewählten Bereich liegt. Der Namensfilter zeigt nur Projekte, deren Name den eingegebenen Text enthält (Groß- und Kleinschreibung egal). Die Filter lassen sich beliebig kombinieren und wirken gemeinsam; sie gelten innerhalb der Projekte, die die Rolle sehen darf. Ein Zurücksetzen entfernt alle Filter. Ohne Treffer zeigt das System einen Hinweis.
- **FA-M3-K1** (Projektleitung; Kann): Freie Kapazität: Der Reiter „Team“ zeigt der verantwortlichen Projektleitung auf Wunsch zusätzlich je Mitarbeiter die Zahl seiner aktiven Projektzuordnungen und lässt sich danach sortieren. So erkennt die Projektleitung, wer Kapazität hat.
- **FA-M3-K2** (Projektleitung; Kann): Der Reiter „Team“ aktualisiert sich automatisch, ohne dass „Aktualisieren“ gewählt werden muss.

### Qualitätsanforderungen M3
- **QA-M3-01** [Funktionale Korrektheit]: Der Reiter „Team“ zeigt genau die dem Projekt zugeordneten Mitarbeiter, zugeordneten Mitarbeitern nur mit Namen, der verantwortlichen Projektleitung zusätzlich mit Status und, bei eingestempelten Personen, mit Standort. Archivierte Projekte erscheinen nicht in der Auswahl zum Einstempeln. Die Projektliste enthält je Rolle genau die erlaubten Projekte. *(Prüfung: JUnit-Tests der Service-Schicht)*
- **QA-M3-02** [Bedienbarkeit]: Der Admin legt ein Projekt ohne Anleitung in höchstens drei Schritten an, die Projektleitung ordnet Mitarbeiter in höchstens drei Schritten zu. Der Reiter „Team“ ist aus dem Detailfenster mit einem Klick erreichbar und ohne Einarbeitung verständlich. *(Prüfung: Probelauf mit einem Teammitglied außerhalb von M3)*
- **QA-M3-03** [Datensparsamkeit]: Der Reiter „Team“ zeigt zugeordneten Mitarbeitern nur die Namen ihres Teams. Die verantwortliche Projektleitung sieht zusätzlich Status, Standort und Beginn der Buchung, aber keine Arbeitszeiten, Salden oder Buchungen auf andere Projekte. Admin und nicht zugeordnete Personen erhalten keinen Zugriff. *(Prüfung: Prüfung in der Durchsicht per Pull Request)*
- **QA-M3-04** [Datenintegrität]: Ein Projekt mit Buchungen wird nie gelöscht, sondern archiviert. *(Prüfung: Tests, Datenbankconstraints)*

### Randbedingungen M3
- **RB-M3-01** [Abhängigkeiten]: M1 liefert Anmeldung, Benutzer und die Rollen Mitarbeiter, Projektleitung und Admin. M2 liefert die Buchungen mit Projekt und Standort, auf denen der Reiter „Team“ beruht. M7 nutzt die Projekte für den Monatsbericht je Projekt. M3 ist auf ein lauffähiges Gerüst angewiesen.

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

## Anhang B: Use Cases M3
### Überblick
- **UC-M3-01** Projekt anlegen – Akteur: Admin – Anforderungen: FA-M3-01, 10, 11
- **UC-M3-02** Projekt bearbeiten – Akteur: Admin, Projektleitung – Anforderungen: FA-M3-02, 10, 11
- **UC-M3-03** Projekt archivieren oder reaktivieren – Akteur: Admin – Anforderungen: FA-M3-03, 10
- **UC-M3-04** Projekte und Projektdetails ansehen – Akteur: Mitarbeiter, Projektleitung, Admin – Anforderungen: FA-M3-04, 05, 13
- **UC-M3-05** Mitarbeiter einem Projekt zuordnen – Akteur: Projektleitung – Anforderungen: FA-M3-06, 10, 12
- **UC-M3-06** Zuordnung entfernen – Akteur: Projektleitung – Anforderungen: FA-M3-07, 10
- **UC-M3-07** Team-Übersicht ansehen – Akteur: Mitarbeiter, Projektleitung – Anforderungen: FA-M3-08, 10
- **UC-M3-08** Team-Übersicht filtern – Akteur: Projektleitung – Anforderungen: FA-M3-09
- **UC-M3-K1** Freie Kapazität anzeigen (Kann) – Akteur: Projektleitung – Anforderungen: FA-M3-K1

### Beschriebene Use Cases (Standardablauf, Fehlerfälle: Grundlage für Tests)
#### UC-M3-05 Mitarbeiter einem Projekt zuordnen
- *Ziel:* Mitarbeiter können auf das Projekt einstempeln und es in ihrer Projektliste sehen.
- *Akteur:* Verantwortliche Projektleitung des Projekts
- *Auslöser:* Die Projektleitung wählt im Detailfenster ihres Projekts „Mitarbeiter zuordnen“.
- *Vorbedingung:* Das Projekt existiert und ist aktiv; die angemeldete Person ist seine verantwortliche Projektleitung.
- *Nachbedingung:* Die gewählten Mitarbeiter sind dem Projekt zugeordnet. Das Projekt erscheint in ihrer Projektliste und in der Auswahl beim Einstempeln (M2).
- *Anforderungen:* FA-M3-06, FA-M3-10, FA-M3-12
**Standardablauf**
1. Die Projektleitung öffnet das Detailfenster ihres Projekts und wählt „Mitarbeiter zuordnen“.
2. Das System zeigt die Benutzer aus M1, die dem Projekt noch nicht zugeordnet sind.
3. Die Projektleitung wählt eine oder mehrere Personen und bestätigt.
4. Das System speichert die Zuordnungen und aktualisiert die Liste der zugeordneten Mitarbeiter.
**Alternativen und Fehlerfälle**
- 2a: Alle Benutzer sind schon zugeordnet. Das System zeigt einen Hinweis, es gibt nichts auszuwählen.
- 1a: Das Projekt ist archiviert. Die Projektleitung sieht es nicht; der Admin muss es zuerst reaktivieren (UC-M3-03).
- 1b: Ein Mitarbeiter oder eine Projektleitung, die das Projekt nicht verantwortet, versucht zuzuordnen (z. B. über einen direkten Aufruf). Die Service-Schicht verweigert den Zugriff, so kann sich kein Mitarbeiter selbst einem Projekt zuordnen.
- 3a: Die Projektleitung bricht ab. Es wird nichts gespeichert.

#### UC-M3-07 Team-Übersicht ansehen
- *Ziel:* Die Projektleitung sieht, wer zum Team ihres Projekts gehört und wer davon gerade wo eingestempelt ist. Zugeordnete Mitarbeiter sehen die Namen ihres Teams.
- *Akteur:* Verantwortliche Projektleitung des Projekts (volle Ansicht), zugeordneter Mitarbeiter (nur Namen)
- *Auslöser:* Die Projektleitung oder ein zugeordneter Mitarbeiter wählt im Detailfenster des Projekts den Reiter „Team“.
- *Vorbedingung:* Die angemeldete Person ist die verantwortliche Projektleitung des Projekts oder dem Projekt als Mitarbeiter zugeordnet; das Projekt ist aktiv.
- *Nachbedingung:* Es ändert sich nichts an den Daten.
- *Anforderungen:* FA-M3-08, FA-M3-10
**Standardablauf**
1. Die Projektleitung oder ein zugeordneter Mitarbeiter öffnet das Detailfenster des Projekts (UC-M3-04) und wählt den Reiter „Team“.
2. Das System ermittelt alle dem Projekt zugeordneten Mitarbeiter und prüft je Person, ob eine offene Buchung auf dieses Projekt besteht (Daten aus M2).
3. Einem zugeordneten Mitarbeiter zeigt das System nur die Namen aller Teammitglieder. Der verantwortlichen Projektleitung zeigt es je Person Name und Status (eingestempelt oder nicht eingestempelt); bei eingestempelten Personen zusätzlich Standort (Büro, Homeoffice, unterwegs) und Beginn der Buchung. Arbeitszeiten, Salden und andere Projekte werden nicht angezeigt.
4. Die Projektleitung wählt „Aktualisieren“, das System lädt den aktuellen Stand neu.
**Alternativen und Fehlerfälle**
- 2a: Dem Projekt ist noch kein Mitarbeiter zugeordnet. Das System zeigt „Dem Projekt sind noch keine Mitarbeiter zugeordnet“.
- 1a: Der Admin, ein nicht zugeordneter Mitarbeiter oder die Projektleitung eines anderen Projekts ruft den Reiter direkt auf (z. B. über einen Link). Der Reiter wird nicht angezeigt, die Service-Schicht verweigert den Zugriff.
- Kann (FA-M3-K2): Das System aktualisiert die Anzeige automatisch, ohne dass die Projektleitung „Aktualisieren“ wählt.

## Anhang C: User Stories mit Akzeptanzkriterien M3 (Testgrundlage)
- **US-M3-01** Als Admin möchte ich ein Projekt mit Name, Beschreibung, Start- und Zieldatum und verantwortlicher Projektleitung anlegen, damit Mitarbeiter ihre Zeiten darauf buchen können. → *Akzeptanz:* Das Projekt erhält eine eindeutige Projekt-ID. Gleiche Namen sind erlaubt. Ohne Projektleitung oder mit Zieldatum vor dem Startdatum lehnt das System das Anlegen ab. *(Bezug: UC-M3-01, FA-M3-01, 10, 11)*
- **US-M3-02** Als Admin möchte ich Name, Beschreibung, Termine und Projektleitung eines Projekts ändern, damit die Projektdaten aktuell bleiben. → *Akzeptanz:* Änderungen sind sofort in Liste und Detailfenster sichtbar. Die Projekt-ID bleibt unverändert. Nur der Admin darf Name, Termine und Projektleitung ändern. *(Bezug: UC-M3-02, FA-M3-02, 10, 11)*
- **US-M3-03** Als Projektleitung möchte ich die Beschreibung meines Projekts ändern, damit das Team den aktuellen Projektinhalt kennt. → *Akzeptanz:* Die Änderung ist nur im Detailfenster eines aktiven Projekts möglich, das die Person verantwortet. Alle anderen Felder bleiben schreibgeschützt. *(Bezug: UC-M3-02, FA-M3-02, 10)*
- **US-M3-04** Als Admin möchte ich Projekte archivieren und wieder aktivieren, damit abgeschlossene Projekte nicht mehr zum Buchen angeboten werden. → *Akzeptanz:* Archivierte Projekte erscheinen nicht in der Auswahl zum Einstempeln. Zuordnungen und bisherige Buchungen bleiben erhalten. Laufende Buchungen zeigt das System vor dem Archivieren als Hinweis. *(Bezug: UC-M3-03, FA-M3-03)*
- **US-M3-05** Als Mitarbeiter möchte ich nur die Projekte sehen, denen ich zugeordnet bin, damit ich schnell das richtige Projekt finde. → *Akzeptanz:* Die Liste zeigt Projekt-ID, Name, Startdatum, Zieldatum und Status. Andere Projekte sind nicht sichtbar. *(Bezug: UC-M3-04, FA-M3-04)*
- **US-M3-06** Als Projektleitung möchte ich alle aktiven Projekte sehen, damit ich einen Überblick über die laufenden Projekte habe. → *Akzeptanz:* Archivierte Projekte sind für die Projektleitung nicht sichtbar. Aktionen und der Reiter „Team“ sind nur bei den eigenen Projekten verfügbar. *(Bezug: UC-M3-04, FA-M3-04, 10)*
- **US-M3-07** Als Admin möchte ich alle Projekte, auch archivierte, sehen, damit ich sie verwalten kann. → *Akzeptanz:* Die Liste enthält alle Projekte mit ihrem Status. Der Admin sieht den Reiter „Team“ nicht. *(Bezug: UC-M3-04, FA-M3-04, 08)*
- **US-M3-08** Als Benutzer möchte ich die Projektliste nach Status, Zeitraum (Start- und Zieldatum) und Projektname filtern, damit ich ein Projekt in einer langen Liste schnell finde. → *Akzeptanz:* Die Filter lassen sich kombinieren und zurücksetzen. Der Namensfilter ignoriert Groß- und Kleinschreibung. Ohne Treffer zeigt das System „Keine Projekte gefunden“. *(Bezug: UC-M3-04, FA-M3-04, 13)*
- **US-M3-09** Als Projektleitung möchte ich per Klick auf mein Projekt ein Detailfenster öffnen, damit ich alle Informationen und Aktionen an einem Ort habe. → *Akzeptanz:* Das Fenster hat die Reiter „Details“ und „Team“. Der Reiter „Team“ erscheint nur für zugeordnete Mitarbeiter und die verantwortliche Projektleitung. *(Bezug: UC-M3-04, FA-M3-05)*
- **US-M3-10** Als Projektleitung möchte ich Mitarbeiter meinem Projekt zuordnen, damit sie darauf Zeit buchen können. → *Akzeptanz:* Nur die verantwortliche Projektleitung kann zuordnen. Ein bereits zugeordneter Mitarbeiter wird nicht doppelt angelegt. Der Mitarbeiter sieht das Projekt danach in seiner Liste. *(Bezug: UC-M3-05, FA-M3-06, 10)*
- **US-M3-11** Als Projektleitung möchte ich Mitarbeiter aus meinem Projekt entfernen, damit nur aktuelle Teammitglieder darauf buchen. → *Akzeptanz:* Das System fragt nach einer Bestätigung. Bisherige Buchungen bleiben erhalten. Ist der Mitarbeiter gerade eingestempelt, weist das System darauf hin. *(Bezug: UC-M3-06, FA-M3-07, 10)*
- **US-M3-12** Als Projektleitung möchte ich im Reiter „Team“ sehen, wer zu meinem Projekt gehört und wer gerade wo eingestempelt ist, damit ich weiß, wer wo arbeitet. → *Akzeptanz:* Alle zugeordneten Mitarbeiter erscheinen mit Name und Status. Eingestempelte zeigen zusätzlich Standort und Beginn der Buchung. Arbeitszeiten und Salden werden nicht angezeigt. Zugeordnete Mitarbeiter sehen nur die Namen. Admin und nicht zugeordnete Personen sehen den Reiter nicht. *(Bezug: UC-M3-07, FA-M3-08, 10)*
- **US-M3-13** Als Projektleitung möchte ich die Teamliste nach Status und Standort filtern, damit ich gezielt sehe, wer im Büro, im Homeoffice oder unterwegs ist. → *Akzeptanz:* Status- und Standortfilter lassen sich kombinieren und zurücksetzen. Ohne Treffer zeigt das System „Keine Person gefunden“. *(Bezug: UC-M3-08, FA-M3-09)*
- **US-M3-14** Als Mitarbeiter möchte ich beim Einstempeln Projekte mit Name und Projekt-ID sehen, damit ich bei gleichen Namen das richtige Projekt wähle. → *Akzeptanz:* Die Auswahl in M2 zeigt je Projekt Name und Projekt-ID. Archivierte Projekte erscheinen dort nicht. *(Bezug: FA-M3-12)*
- **US-M3-15** Als Mitarbeiter möchte ich im Reiter „Team“ meines Projekts sehen, wer noch zu meinem Team gehört, damit ich weiß, mit wem ich zusammenarbeite. → *Akzeptanz:* Der Reiter zeigt nur die Namen aller zugeordneten Mitarbeiter, keinen Status, Standort oder Beginn der Buchung. Er erscheint nur bei Projekten, denen die Person zugeordnet ist. *(Bezug: UC-M3-07, FA-M3-08, 10)*
- **US-M3-K1** Als Projektleitung möchte ich im Reiter „Team“ die Zahl der aktiven Projektzuordnungen je Mitarbeiter sehen, damit ich freie Kapazität erkenne. → *Akzeptanz:* Die Liste lässt sich nach der Zahl der Zuordnungen sortieren. Was „frei“ heißt, legt das Team fest. *(Bezug: UC-M3-K1, FA-M3-K1)*
- **US-M3-K2** Als Projektleitung möchte ich, dass sich der Reiter „Team“ automatisch aktualisiert, damit ich nicht von Hand neu laden muss. → *Akzeptanz:* Die Anzeige aktualisiert sich ohne Zutun der Projektleitung. *(Bezug: UC-M3-07, FA-M3-K2)*
