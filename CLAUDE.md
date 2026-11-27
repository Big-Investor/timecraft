# CLAUDE.md – TimeCraft Modul M1 Benutzer- und Mitarbeiterverwaltung

TimeCraft ist eine digitale Zeiterfassung mit Projektübersicht für Unternehmen und Abteilungen (Softwareprojekt Wirtschaftsinformatik, Hochschule Bielefeld). Sieben Personen entwickeln je ein Modul. **Du arbeitest für Leon-Selim Kannenberg an genau einem Modul.** Programm bleibt lokal, nur erfundene Testdaten.

## 0. Kurzauftrag
Anmeldung/Abmeldung, Benutzerkonten mit Rollen (Mitarbeiter, Projektleitung, Admin), Passwortverfahren (BCrypt, Einmalpasswort per E-Mail), Mitarbeiterstammdaten, Arbeitszeitmodell, Startsaldo, Urlaubsdaten, Vorgesetzte. M1 ist die Grundlage aller anderen Module: Du lieferst über `MitarbeiterAuskunft` Identität, Rolle und Stammdaten.
Benutzer (Zugangskonto) und Mitarbeiter (Personalstamm) sind getrennte Datenobjekte, 1:1 verknüpft. Es gibt keine Selbstregistrierung, kein SSO, keine 2-Faktor-Anmeldung, keine Anbindung an Lohn/Personalsysteme. Resturlaub und Überstundensaldo berechnet M1 nicht (nur Ausgangswerte).

## 1. Dein Auftrag und Deine Grenzen (hart)
- Du entwickelst **ausschließlich Modul M1 Benutzer- und Mitarbeiterverwaltung** im Paket `de.timecraft.benutzer` (Unterpakete `view`, `service`, `repository`, `model`, `api`) plus Tests. Verantwortlich: Leon-Selim Kannenberg. Branch: `modul/m1-benutzer`.
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
Du hast **kein Gedächtnis** zwischen Sitzungen. Alles Wichtige steht in dieser Datei, im Code, in den Tests, in git und in der Statusdatei `docs/status/M1.md`.

**Startsatz der Person:** „Arbeite das nächste offene Arbeitspaket ab.“ Dann:
1. Diese Datei und `docs/status/M1.md` lesen (fehlt sie: aus der Vorlage unten anlegen). `git status` und `git log -5` ansehen.
2. `mvn test` ausführen: Die **Ausgangslage muss grün** sein. Ist sie rot: erst melden, nicht überspielen.
3. Nur das **nächste offene** Arbeitspaket (AP) aus Abschnitt 10 nehmen. In 3–5 Zeilen sagen, was Du tun wirst (Dateien, Tests), dann sofort loslegen. Kein weiteres AP anfangen, auch wenn Zeit bleibt.
4. **Tests zuerst bzw. gleichzeitig:** Für jede Fachregel ein Test (Normalfall, Grenzfall, Fehlerfall, Rolle). Dann der einfachste Code, der ihn grün macht.
5. Fertig ist ein AP erst, wenn: (a) `mvn test` für das **gesamte** Projekt grün ist (alle Tests, nicht nur Deine), (b) der Review (Abschnitt 4) ohne offene Mängel durch ist, (c) die Statusdatei aktualisiert ist, (d) ein Commit auf `modul/m1-benutzer` liegt (`M1: <AP-Name>`, kleine Commits, kein Push auf `main`, kein Force-Push).
6. Zum Schluss melden: erledigtes AP, Teststand, Review-Ergebnis, offene Fragen, nächstes AP. Dann die Sitzung beenden (die Person startet für das nächste AP eine **neue** Sitzung bzw. `/clear`).

**Statusdatei-Vorlage `docs/status/M1.md`:**
```
# Status M1 Benutzer- und Mitarbeiterverwaltung
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

> Du bist Reviewer für TimeCraft Modul M1 (Benutzer- und Mitarbeiterverwaltung). Du prüfst nur und änderst nichts. Lies `CLAUDE.md` im Repo-Root und `docs/status/M1.md`, dann `git diff` des aktuellen Arbeitspakets (uncommitted Änderungen bzw. letzter Commit) und die geänderten Dateien. Führe `mvn test` aus. Prüfe:
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

| Rolle | Darf in M1 |
|---|---|
| Mitarbeiter | Eigenes Profil ansehen (Kann), eigenes Passwort ändern |
| Projektleitung | zusätzlich Mitarbeiterliste ansehen, Stammdaten und Arbeitszeitmodell bearbeiten (nicht Urlaubsdaten, Startsaldo, Vorgesetzten, Rolle, Konto) |
| Admin | Benutzer, Rollen, Konten (sperren, Passwort zurücksetzen), alle Mitarbeiterdaten |

## 7. Pakete, Klassen und Datenmodell
Basispaket `de.timecraft`. Klassennamen sind ein Vorschlag aus dem Paketdiagramm, Abweichungen nur in Absprache.

- `view`: AnmeldeView, BenutzerListeView (Kann), MitarbeiterListeView, MitarbeiterFormular, ProfilView
- `service`: BenutzerService, MitarbeiterService, PasswortService, MailService
- `repository`: BenutzerRepository, MitarbeiterRepository
- `model`: Benutzer, Mitarbeiter, Arbeitszeitmodell, Qualifikation (Azubi, Geselle, Meister); `Rolle` liegt im Vertrag in `api` (siehe unten)
- `api`: MitarbeiterAuskunft, MitarbeiterInfo, UrlaubsDaten, Rolle (Vertrag)
- Anmeldung läuft über Spring Security im Gerüst (`geruest.config.SecurityConfig`). Du lieferst die Benutzerdaten dafür (UserDetailsService). Bevor Du am Gerüst etwas änderst: nachfragen.

**Tabellen (Relationenmodell, Dein Modul):**
- `benutzer`: benutzer_id PK, benutzername VARCHAR(50) eindeutig, passwort_hash VARCHAR(60), rolle VARCHAR(15), gesperrt BOOLEAN, fehlversuche SMALLINT, einmalpasswort_hash VARCHAR(60), einmalpasswort_gueltig_bis TIMESTAMP, passwort_aenderung_noetig BOOLEAN
- `mitarbeiter`: mitarbeiter_id PK, benutzer_id FK (1:1), vorgesetzter_id FK→mitarbeiter (null erlaubt, nie selbst), personalnummer VARCHAR(10) eindeutig, vorname, nachname, email, telefon, geburtsdatum DATE, eintrittsdatum, austrittsdatum, qualifikation VARCHAR(10), aktiv BOOLEAN, stundensatz NUMERIC(8,2) (Kann)
- `arbeitszeitmodell`: arbeitszeitmodell_id PK, mitarbeiter_id FK (1:1), wochenstunden NUMERIC(4,2); `arbeitstag`: arbeitszeitmodell_id PK/FK + wochentag SMALLINT PK (1=Montag … 7=Sonntag)
- `zeitkonto`: zeitkonto_id PK, mitarbeiter_id FK (1:1), startdatum DATE, anfangssaldo_min INTEGER
- `urlaubskonto`: urlaubskonto_id PK, mitarbeiter_id FK, jahr SMALLINT, anspruch_tage SMALLINT, uebertrag_tage SMALLINT
- Hinweis: `zeitkonto` und `urlaubskonto` sind im Relationenmodell M2 zugeordnet (Farbe), die Analysesequenz UC-M1-04 lässt aber `MitarbeiterService` sie beim Anlegen erzeugen. Standard in dieser Datei: **M1 legt nur die Startwerte an und besitzt diese zwei Tabellen (Entities in `benutzer.model`)**, M2 liest sie über `MitarbeiterAuskunft`. Das ist ein offener Punkt (siehe unten).

### Fachregeln
- Anmelden: falsche Daten → neutrale Meldung, die nicht verrät, welches Feld falsch ist. Gesperrtes Konto kann sich nicht anmelden. Nach Anmeldung nur Menüpunkte der eigenen Rolle (zusätzlich im Service geprüft).
- Passwörter nur als BCrypt-Hash (`passwort_hash`, `einmalpasswort_hash`). Regeln: mindestens 8 Zeichen und mindestens eine Ziffer (`PasswortService`, rein testbar).
- Einmalpasswort: zufällig, einmal gültig, höchstens 24 Stunden (`einmalpasswort_gueltig_bis`), nach Nutzung muss ein eigenes Passwort gesetzt werden (`passwort_aenderung_noetig`); ohne neues Passwort kein Zugriff auf andere Seiten. Wird beim Anlegen und beim Zurücksetzen erzeugt und per `MailService` (Spring Boot Mail, SMTP) gesendet. **Versand schlägt fehl:** beim Zurücksetzen bleibt das alte Passwort gültig und der Admin sieht eine Fehlermeldung; beim Anlegen bleiben Mitarbeiter und Konto gespeichert, Hinweis „Mitarbeiter gespeichert, Einmalpasswort erneut senden“.
- Mitarbeiter anlegen (UC-M1-04, siehe Anhang): prüft Pflichtfelder und Plausibilität (Austrittsdatum nach Eintrittsdatum, Urlaubsanspruch mindestens 20 Tage, mindestens ein Arbeitstag, niemand ist sein eigener Vorgesetzter, Vorgesetzter hat Rolle PROJEKTLEITUNG (Teamentscheidung 04.11.2026, weicht von FA-M1-19 ab)), Personalnummer und Benutzername eindeutig (Fehler am Feld, nichts gespeichert). Dann Benutzer + Mitarbeiter + Arbeitszeitmodell + Zeitkonto-Startwert + Urlaubskonto in einer Transaktion, danach Einmalpasswort.
- Deaktivieren statt löschen (Austrittsdatum Pflicht). Mitarbeiter mit Zeiten/Anträgen werden nie gelöscht. Ein Vorgesetzter kann erst deaktiviert werden, wenn seinen Mitarbeitern ein neuer Vorgesetzter zugeordnet ist. Deaktivierte erscheinen nicht mehr in `aktive()`.
- Projektleitung darf nur Stammdaten + Arbeitszeitmodell ändern; Admin alles. Rollenänderung gilt ab der nächsten Anmeldung. Die Projektleitung ändert keine Rollen.
- Sitzung läuft nach 30 Minuten Inaktivität ab (QA-M1-02; Konfiguration im Gerüst – nachfragen, falls nötig).
- `MitarbeiterAuskunft` (Vertrag): Keine Rechteprüfung in der api (Aufrufer prüft), `angemeldetePerson()` aus dem Spring-Security-Kontext.
- Service-Schicht mindestens 70 % durch JUnit abgedeckt (QA-M1-10).

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

### Das stellst Du bereit (Du bist Besitzer von M1) – exakt so implementieren/anlegen

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

## 9. Fakes: Entwickeln ohne fremde Module
- **Tests:** Fremde api-Interfaces werden in Tests durch einfache Fakes (kleine Klassen im Testordner `src/test/java/de/timecraft/benutzer/fake/`) oder Mockito ersetzt. Nimm das Einfachere. Fakes bilden das **Vertragsverhalten** nach (auch Fehlerfälle) und enthalten Beispieldaten für alle drei Rollen.
- **Lokal starten:** Fakes, die für die laufende Oberfläche nötig sind, liegen in `src/main/java/de/timecraft/benutzer/fake/` und sind mit `@Profile("fake")` markiert. Start: `mvn spring-boot:run -Dspring-boot.run.profiles=fake`. Die angemeldete Person des Fakes ist über eine Property wählbar (z. B. `timecraft.fake.rolle=PROJEKTLEITUNG`), damit Du alle Rollen ausprobieren kannst. Die echten Beans der anderen Module gibt es in Deinem Branch nicht; nach dem Merge ist das Profil `fake` einfach nicht aktiv.
- Fakes gehören zu Deinem Modul und werden nie für andere Module geschrieben oder von ihnen benutzt.

Für Dein Modul konkret:
M1 braucht keine Fakes für andere Module. Für Mailversand: `JavaMailSender` im Test mocken (Erfolg und simulierter Fehler). Fürs lokale Starten: Mail-Versand im Profil `fake` nur ins Log schreiben (`@Profile("fake")` `MailService`-Variante oder Konfiguration), damit das Einmalpasswort sichtbar wird. **Entscheidung 04.11.2026:** Die Fake-Variante meldet zusätzlich eine simulierte Zustellung zurück („E-Mail ist angekommen“), und die Oberfläche zeigt diese Rückmeldung an (z. B. „E-Mail wurde zugestellt“). Versandfehler bleiben per Test mit gemocktem `JavaMailSender` abgedeckt.

## 10. Arbeitspakete (Reihenfolge einhalten, ein AP pro Sitzung)
Jedes AP endet mit grünem `mvn test`, Review „OK“, Statusdatei und Commit (Abschnitt 3).

- **AP1 Model und Repository:** Entities Benutzer, Mitarbeiter, Arbeitszeitmodell (+Arbeitstage), Startwert-Entities, Qualifikation; Repositories; Repository-Tests mit Constraints (Benutzername/Personalnummer eindeutig, 1:1-Beziehungen).
- **AP2 PasswortService und Anmeldung:** BCrypt, Passwortregeln, Einmalpasswort (24 h, einmalig, Erzwingen der Änderung), UserDetailsService für Spring Security, Passwort ändern mit altem Passwort (FA-M1-06), Abmelden. Tests: Regeln, Ablauf, Hash statt Klartext.
- **AP3 MitarbeiterService:** Anlegen (UC-M1-04), Bearbeiten (Rechte Projektleitung vs. Admin), Deaktivieren (Vorgesetzten-Regel), Liste, Validierungen, Rollen ändern, sperren/entsperren, Passwort zurücksetzen mit MailService (Versandfehler-Tests). Tests je Rolle (Negativtests).
- **AP4 api:** MitarbeiterAuskunft + Infos implementieren (Vertrag exakt). Tests: angemeldete Person, aktive/alle, Urlaubsdaten.
- **AP5 Views:** AnmeldeView (mit Hinweis Einmalpasswort), Passwort-ändern-Maske, MitarbeiterListeView, MitarbeiterFormular (Stammdaten, Arbeitszeit/Urlaub, Arbeitstage, Benutzerkonto, Hinweis Jugendarbeitsschutz bei unter 18, „Speichern & Einmalpasswort senden“). Validierung direkt im Formular, Pflichtfelder markiert, Meldungen deutsch. Jede Funktion in höchstens 3 Klicks aus dem Hauptmenü. Desktop und Tablet.
- **AP6 Kann:** ZURÜCKGESTELLT (Teamentscheidung 04.11.2026): erst nach ausdrücklicher Freigabe, bis dahin nur die vorbereiteten Schnittstellen (Abschnitt 11). Inhalt: Benutzerliste mit Filter (K1), Sperre nach 5 Fehlversuchen (K2), Mein Profil (K3), Stundensatz (K4), Suche/Filter der Mitarbeiterliste (K5).

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
- **Geburtsdatum:** Pflichtfeld im Mitarbeiterformular (wird für die Minderjährigenregeln gebraucht, auch wenn FA-M1-10 es nicht nennt).
- **Zeitkonto/Urlaubskonto:** M1 legt die Startwerte an und besitzt die Tabellen `zeitkonto`/`urlaubskonto` (Leon, Noah). M2 liest sie nur über `MitarbeiterAuskunft`.
- **Mindesturlaub:** pauschal mindestens 20 Tage (keine Anteilsrechnung bei weniger Arbeitstagen).
- **Mail lokal:** Im Profil `fake` wird die Mail nur ins Log geschrieben **und** eine simulierte Rückmeldung „E-Mail ist angekommen“ in der Oberfläche gezeigt.
- **Kann zurückgestellt, Schnittstellen vorbereiten:** Spalte `stundensatz` (nullable) im Entity und in der Tabelle mit anlegen, aber ohne Oberfläche und ohne Logik. Den Anmeldevorgang so bauen, dass ein Fehlversuch an genau einer Stelle gezählt werden könnte (K2): eine kleine Methode `fehlversuchRegistrieren(...)`, die heute nichts sperrt. Keine Benutzerliste (K1), kein „Mein Profil“ (K3), keine Suche/Filter (K5).
- **Vorgesetzter:** nur Benutzer mit Rolle PROJEKTLEITUNG sind als Vorgesetzte wählbar (Abweichung von FA-M1-19, UC-M1-04 und US-M1-10, die auch ADMIN nennen). Ein Admin-Konto ist ein getrenntes Konto und hat nie zugleich die Rolle Projektleitung. Jedes Konto hat genau eine Rolle (`benutzer.rolle`). Eine Person, die Admin **und** Projektleitung ist, hat daher zwei Konten; jedes Konto ist 1:1 mit einem eigenen Mitarbeiter-Datensatz verknüpft. Dafür baust Du nichts Besonderes.
- **Mail im Normalbetrieb:** kein festgelegter SMTP-Zugang außerhalb des Profils `fake`; `JavaMailSender` per Properties konfigurierbar halten, keine Zugangsdaten im Repository. So ist es entschieden, keine weitere Rückfrage.
- **Gerüst-Anmeldung:** Das Gerüst enthält eine Platzhalter-Anmeldung. M1 ersetzt sie, indem M1 die Benutzerverwaltung für Spring Security bereitstellt (Benutzer laden, Passwort prüfen, Sperre, Einmalpasswort), die Sicherheitskonfiguration des Gerüsts aber nur so weit anpasst, wie unbedingt nötig. Siehe auch „Gerüst-Annahmen“ oben. Passt das nicht zum Gerüst, Teamfrage.

## 12. Offene Punkte – nicht selbst entscheiden
Frage die Person vor dem Bildschirm, **bevor** Du etwas baust, das davon abhängt. Die Antwort kommt mit Datum in die Statusdatei.

- Keine offenen Fragen aus der Mittwochsrunde. Treten beim Lesen des Gerüsts Lücken auf (Abschnitt 11, „Gerüst-Annahmen“), als Teamfrage in den Status eintragen und mit Tim klären.

**Termine:** stehen nicht in dieser Datei. Bei Verzug bleiben die zurückgestellten Kann-Anforderungen weg.

## Anhang A: Anforderungen Modul M1 (Originaltext aus dem Dokument)
### Funktionale Anforderungen (Muss = Pflicht, Kann = optional)
- **FA-M1-01** (Mitarbeiter, Projektleitung, Admin; Muss): Das System muss eine Anmeldung mit Benutzername und Passwort ermöglichen.
- **FA-M1-02** (Mitarbeiter, Projektleitung, Admin; Muss): Das System muss angemeldeten Benutzern eine Abmeldung ermöglichen.
- **FA-M1-03** (Admin; Muss): Das System muss dem Admin ermöglichen, ein Benutzerkonto anzulegen und ihm eine Rolle zuzuweisen.
- **FA-M1-04** (Admin; Muss): Das System muss dem Admin ermöglichen, die Rolle eines Benutzers zu ändern.
- **FA-M1-05** (Admin; Muss): Das System muss dem Admin ermöglichen, ein Benutzerkonto zu sperren und zu entsperren. Gesperrte Benutzer können sich nicht anmelden.
- **FA-M1-06** (Mitarbeiter, Projektleitung, Admin; Muss): Das System muss jedem Benutzer ermöglichen, sein eigenes Passwort zu ändern (altes Passwort zur Bestätigung)
- **FA-M1-07** (Mitarbeiter, Projektleitung, Admin; Muss): Das System muss Ansichten und Funktionen abhängig von der Rolle des angemeldeten Benutzers freigeben.
- **FA-M1-08** (Admin; Muss): Das System muss dem Admin ermögliche, das Passwort eines Benutzers zurückzusetzten. Dabei wird ein Einmalpasswort an die hinterlegte E-Mail Adresse versendet.
- **FA-M1-09** (Admin; Muss): Das System muss beim Anlegen eines Benutzerkontos ein Einmalpasswort erzeugen und an die hinterlegte E-Mail-Adresse senden. Bei der ersten Anmeldung muss der Benutzer ein eigenes Passwort festlegen.
- **FA-M1-K1** (Admin; Kann): Das System kann dem Admin eine Liste aller Benutzerkonten mit Suche und Filter nach Rolle und Status anzeigen.
- **FA-M1-K2** (System; Kann): Das System kann ein Konto nach 5 aufeinanderfolgenden Fehlversuchen automatisch sperren.
- **FA-M1-10** (Admin; Muss): Das System muss das Anlegen eines Mitarbeiters mit Personalnummer, Vorname, Nachname, E-Mail, Telefon, Eintrittsdatum und Qualifikation (Azubi, Geselle, Meister) ermöglichen.
- **FA-M1-11** (Projektleitung, Admin; Muss): Das System muss dem Admin ermöglichen, alle Daten eines Mitarbeiters zu bearbeiten. Die Projektleitung darf nur die Stammdaten (FA-M1-10) und das Arbeitszeitmodell (FA-M1-16) bearbeiten.
- **FA-M1-12** (Admin; Muss): Das System muss einen Mitarbeiter beim Austritt mit Angabe des Austrittsdatums deaktivieren können, statt ihn zu löschen, damit bereits erfasste Zeiten und Anträge erhalten bleiben.
- **FA-M1-13** (Admin; Muss): Das System muss beim Anlegen eines Mitarbeiters das zugehörige Benutzerkonto verknüpfen bzw. mit anlegen (1:1).
- **FA-M1-14** (System; Muss): Das System muss anderen Modulen(Projekte, Zeiterfassung, Korrekturanträge, Urlaubsverwaltung) die aktiven Mitarbeiter samt Arbeitszeitmodell, Urlaubsdaten und Vorgesetztem bereitstellen).
- **FA-M1-15** (Projektleitung, Admin; Muss): Das System muss eine Liste aller Mitarbeiter anzeigen, aus der ein Mitarbeiter zur Bearbeitung ausgewählt werden kann.
- **FA-M1-16** (Projektleitung, Admin; Muss): Das System muss für jeden Mitarbeiter ein Arbeitszeitmodell mit Wochenarbeitszeit und Arbeitstagen speichern. Daraus berechnet die Zeiterfassung die Soll-Stunden.
- **FA-M1-17** (Admin; Muss): Das System muss für jeden Mitarbeiter einen Überstunden-Startsaldo für die Einführung von TimeCraft speichern. Der weitere Saldo wird über Das System berechnet
- **FA-M1-18** (Admin; Muss): Das System muss für jeden Mitarbeiter den jährlichen Urlaubsanspruch und den Resturlaub-Übertrag aus dem Vorjahr speichern. Der aktuelle Resturlaub wird von der Urlaubsverwaltung berechnet.
- **FA-M1-19** (Admin; Muss): Das System muss jedem Mitarbeiter einen Vorgesetzten mit der Rolle Projektleitung oder Admin zuordnen. Dieser genehmigt Urlaubs- und Zeitkorrekturanträge.
- **FA-M1-K3** (Mitarbeiter; Kann): Das System soll einem Mitarbeiter die Ansicht seiner eigenen Daten ermöglichen („Mein Profil“).
- **FA-M1-K4** (Admin; Kann): Das System kann einem Mitarbeiter einen internen Stundensatz für die Projektkalkulation zuordnen
- **FA-M1-K5** (Projektleitung, Admin; Kann): Das System kann die Mitarbeiterliste nach Name und Personalnummer durchsuchen sowie nach Status (aktiv/inaktiv) und Qualifikation filtern.

### Qualitätsanforderungen M1
- **QA-M1-01** [Sicherheit]: Passwörter und Einmalpasswörter werden ausschließlich als BCrypt-Hash gespeichert. Das Einmalpasswort ist zufällig und nur für eine Anmeldung gültig. *(Prüfung: JUnit-Test, Datenbankprüfung)*
- **QA-M1-02** [Sicherheit]: Eine Sitzung soll nach 30 Minuten Inaktivität ablaufen. *(Prüfung: Test mit verkürzter Sitzungsdauer)*
- **QA-M1-03** [Sicherheit]: Passwörter haben mindestens 8 Zeichen und mindestens eine Ziffer. *(Prüfung: JUnit-Tests der Passwortregeln)*
- **QA-M1-04** [Datenschutz]: Es werden nur Mitarbeiterdaten gespeichert, die für Zeiterfassung und Projektzuordnung nötig sind. *(Prüfung: Durchsicht des Datenmodells per Pull Request)*
- **QA-M1-05** [Zuverlässigkeit]: Benutzername und Personalnummer sind eindeutig, Duplikate werden mit einer Fehlermeldung abgelehnt. *(Prüfung: Tests, Datenbankconstraints)*
- **QA-M1-06** [Zuverlässigkeit]: Mitarbeiter mit erfassten Zeiten oder Anträgen können nur deaktiviert werden (referenzielle Integrität). Ein Vorgesetzter kann erst deaktiviert werden, wenn seinen Mitarbeitern ein neuer Vorgesetzter zugeordnet ist. *(Prüfung: Tests, Datenbankconstraints)*
- **QA-M1-07** [Bedienbarkeit]: Eingaben werden direkt im Formular validiert. Pflichtfelder sind markiert und Fehlermeldungen sind in deutsch und verständlich beschrieben. Bei der Plausibilitätsprüfungen: Das Austrittsdatum liegt nach dem Eintrittsdatum. Der Urlaubsanspruch entspricht dem mindestens gesetzlichen Mindesturlaub (20 Tage bei 5-Tage-Woche). Es muss mindestens ein Arbeitstag gewählt werden und kein Mitarbeiter ist sein eigener Vorgesetzter. *(Prüfung: Negativtests, Demo)*
- **QA-M1-08** [Bedienbarkeit]: Jede Funktion des Moduls ist vom Hauptmenü aus mit höchstens 3 Klicks erreichbar. *(Prüfung: Probelauf, Demo)*
- **QA-M1-09** [Bedienbarkeit]: Die Oberfläche ist auf Desktop und Tablet nutzbar, weil in der Werkstatt Tablets eingesetzt werden könnten. *(Prüfung: Probelauf auf Desktop und Tablet)*
- **QA-M1-10** [Testbarkeit]: Die Service-Schicht wird mit JUnit-Tests zu mindestens 70 % abgedeckt. *(Prüfung: JUnit, Testbericht)*
- **QA-M1-11** [Zuverlässigkeit]: Schlägt der Versand des Einmalpassworts fehl, bleibt das bisherige Passwort gültig und der Admin erhält eine Fehlermeldung. *(Prüfung: Test mit simuliertem Versandfehler)*
- **QA-M1-12** [Sicherheit]: Ein Einmalpasswort ist höchstens 24 Stunden gültig. *(Prüfung: JUnit-Test mit abgelaufenem Einmalpasswort)*

### Randbedingungen M1
- **RB-M1-01** [Technik]: E-Mail-Versand der Einmalpasswörter über Spring Boot Mail und einen SMTP-Server.
- **RB-M1-02** [Abgrenzung]: Keine Anbindung an Lohnbuchhaltung oder externe Personalsysteme.
- **RB-M1-03** [Abgrenzung]: Keine Anmeldung über externe Identitätsanbieter (z. B. Microsoft-Konto) und keine Zwei-Faktor-Authentifizierung.
- **RB-M1-04** [Abgrenzung]: Keine Selbstregistrierung: Konten legt ausschließlich der Admin an.
- **RB-M1-05** [Abgrenzung]: Keine Berechnung von Resturlaub und Überstundensaldo: Diese übernehmen Urlaubsverwaltung und Zeiterfassung; das Modul liefert nur die Ausgangswerte.

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

## Anhang B: Use Cases M1
### Überblick
- **UC-M1-01** Anmelden – Akteur: Mitarbeiter, Projektleitung, Admin – Anforderungen: FA-M1-01, 07, 09
- **UC-M1-02** Abmelden – Akteur: Mitarbeiter, Projektleitung, Admin – Anforderungen: FA-M1-02
- **UC-M1-03** Eigenes Passwort ändern – Akteur: Mitarbeiter, Projektleitung, Admin – Anforderungen: FA-M1-06
- **UC-M1-04** Mitarbeiter anlegen – Akteur: Admin – Anforderungen: FA-M1-10, 13, 16, 17, 18, 19 FA-M1-03, 09
- **UC-M1-05** Mitarbeiter bearbeiten – Akteur: Admin, Projektleitung – Anforderungen: FA-M1-11, 16, 17, 18, 19
- **UC-M1-06** Mitarbeiter deaktivieren – Akteur: Admin – Anforderungen: FA-M1-12
- **UC-M1-07** Eigenes Profil ansehen – Akteur: Mitarbeiter, Projektleitung, Admin – Anforderungen: FA-M1-K3
- **UC-M1-08** Rolle eines Benutzers ändern – Akteur: Admin – Anforderungen: FA-M1-04
- **UC-M1-09** Benutzerkonto sperren oder entsperren – Akteur: Admin – Anforderungen: FA-M1-05
- **UC-M1-10** Passwort zurücksetzen – Akteur: Admin – Anforderungen: FA-M1-08
- **UC-M1-11** Mitarbeiterliste ansehen – Akteur: Admin, Projektleitung – Anforderungen: FA-M1-15
- **UC-M1-K1** Benutzerliste ansehen (Kann) – Akteur: Admin – Anforderungen: FA-M1-K1
- **UC-M1-K2** Konto nach Fehlversuchen automatisch sperren (Kann) – Akteur: System – Anforderungen: FA-M1-K2
- **UC-M1-K3** Mitarbeiterliste durchsuchen und filtern (Kann) – Akteur: Admin, Projektleitung – Anforderungen: FA-M1-K5

### Beschriebene Use Cases (Standardablauf, Fehlerfälle: Grundlage für Tests)
#### UC-M1-04 Mitarbeiter anlegen
- *Ziel:* Ein neuer Mitarbeiter ist mit Benutzerkonto angelegt und steht Projekten, Zeiterfassung und Urlaubsverwaltung zur Verfügung.
- *Akteur:* Admin
- *Auslöser:* Der Admin wählt in der Mitarbeiterliste „Neuer Mitarbeiter“.
- *Vorbedingung:* Der Admin ist angemeldet.
- *Nachbedingung:* Der Mitarbeiter ist mit Stammdaten, Arbeitszeitmodell, Überstunden-Startsaldo, Urlaubsdaten und Vorgesetztem und dem Status aktiv gespeichert. Ein verknüpftes Benutzerkonto (1:1) mit Rolle existiert, und ein Einmalpasswort wurde an die hinterlegte E-Mail-Adresse gesendet.
- *Anforderungen:* FA-M1-10, 13, 16, 17, 18, 19; FA-M1-03, 09
**Standardablauf**
1. Der Admin öffnet das Formular „Neuer Mitarbeiter“.
2. Der Admin gibt die Stammdaten ein: Personalnummer, Vorname, Nachname, E-Mail, Telefon, Eintrittsdatum und Qualifikation.
3. Der Admin gibt Wochenarbeitszeit, Arbeitstage, Überstunden-Startsaldo, Urlaubsanspruch und Resturlaub-Übertrag ein und wählt einen Vorgesetzten aus den Benutzern mit der Rolle Projektleitung oder Admin.
4. Der Admin gibt einen Benutzernamen ein, wählt die Rolle und wählt „Speichern“.
5. Das System prüft die Eingaben: Pflichtfelder sind ausgefüllt, Personalnummer und Benutzername sind eindeutig, der Urlaubsanspruch beträgt mindestens 20 Tage und es ist mindestens ein Arbeitstag gewählt.
6. Das System speichert Mitarbeiter und Benutzerkonto mit dem Status aktiv.
7. Das System erzeugt ein Einmalpasswort, sendet es an die E-Mail-Adresse und zeigt den neuen Mitarbeiter in der Mitarbeiterliste.
**Alternativen und Fehlerfälle**
- 5a: Eine Pflichtangabe fehlt oder eine Plausibilitätsprüfung schlägt fehl. Das System zeigt eine Meldung am Feld, speichert nichts und lässt das Formular offen.
- 5b: Personalnummer oder Benutzername sind bereits vergeben. Das System zeigt eine Meldung am Feld und speichert nichts.
- 7a: Der Versand des Einmalpassworts schlägt fehl. Mitarbeiter und Konto bleiben gespeichert; das System zeigt eine Fehlermeldung, und der Admin sendet später über UC-M1-10 ein neues Einmalpasswort.
- 3a: Der Admin trägt zusätzlich einen internen Stundensatz ein (optional, FA-M1-K4).
- 2a–4a: Der Admin bricht ab. Das Formular schließt, nichts wird gespeichert.
- 1a: Eine Person ohne Admin-Rolle ruft das Formular direkt auf. Die Service-Schicht verweigert den Zugriff, nichts wird gespeichert.

#### UC-M1-10 Passwort zurücksetzen
- *Ziel:* Ein Benutzer, der sein Passwort vergessen hat, kann sich wieder anmelden.
- *Akteur:* Admin; Mailserver (sekundär)
- *Auslöser:* Ein Benutzer meldet dem Admin ein vergessenes Passwort; der Admin öffnet den Mitarbeiter und wählt im Bereich „Benutzerkonto“ „Einmalpasswort senden“.
- *Vorbedingung:* Der Admin ist angemeldet. Das Benutzerkonto existiert, und der verknüpfte Mitarbeiter hat eine E-Mail-Adresse.
- *Nachbedingung:* Ein Einmalpasswort ist als BCrypt-Hash mit 24 Stunden Gültigkeit gespeichert und per E-Mail versendet. Das alte Passwort ist ungültig; bei der nächsten Anmeldung muss der Benutzer ein neues Passwort festlegen.
- *Anforderungen:* FA-M1-08; QA-M1-01, 11, 12
**Standardablauf**
1. Der Admin öffnet in der Mitarbeiterliste den betroffenen Mitarbeiter.
2. Der Admin wählt im Bereich „Benutzerkonto“ „Einmalpasswort senden“ und bestätigt die Sicherheitsabfrage.
3. Das System erzeugt ein zufälliges Einmalpasswort und sendet es an die hinterlegte E-Mail-Adresse.
4. Nach erfolgreichem Versand speichert das System den Hash, setzt die Gültigkeit auf 24 Stunden, markiert eine Passwortänderung als erforderlich und zeigt eine Bestätigung.
5. Der Benutzer meldet sich mit dem Einmalpasswort an (UC-M1-01) und legt ein neues Passwort fest.
**Alternativen und Fehlerfälle**
- 2a: Der Admin bricht die Sicherheitsabfrage ab. Nichts wird geändert.
- 3a: Der Mailversand schlägt fehl. Das bisherige Passwort bleibt gültig, und der Admin erhält eine Fehlermeldung.
- 3b: Die E-Mail-Adresse ist ungültig. Das System zeigt einen Hinweis; der Admin korrigiert die Adresse über UC-M1-05.
- 5a: Das Einmalpasswort ist älter als 24 Stunden. Die Anmeldung wird abgelehnt; der Admin muss ein neues Einmalpasswort senden.
- 5b: Das neue Passwort erfüllt die Regeln nicht (mindestens 8 Zeichen, eine Ziffer). Das System zeigt eine Meldung und lässt das Formular offen.
- 1a: Eine Person ohne Admin-Rolle ruft die Funktion direkt auf. Die Service-Schicht verweigert den Zugriff, nichts wird geändert.

## Anhang C: User Stories mit Akzeptanzkriterien M1 (Testgrundlage)
- **US-M1-01** Als Mitarbeiter möchte ich mich mit Benutzername und Passwort anmelden, damit ich meine Arbeitszeiten erfassen kann. → *Akzeptanz:* Bei falschen Daten erscheint eine Fehlermeldung, ohne zu verraten, welches Feld falsch ist. Ein gesperrtes Konto kann sich nicht anmelden. Nach der Anmeldung sind nur die Menüpunkte der eigenen Rolle sichtbar. *(Bezug: UC-M1-01; FA-M1-01, 05, 07)*
- **US-M1-02** Als neuer Mitarbeiter möchte ich mit dem Einmalpasswort aus der E-Mail ein eigenes Passwort festlegen, damit nur ich mein Passwort kenne. → *Akzeptanz:* Ohne neues Passwort ist kein Zugriff auf andere Seiten möglich. Das neue Passwort hat mindestens 8 Zeichen und eine Ziffer. Ein Einmalpasswort älter als 24 Stunden wird abgelehnt. *(Bezug: UC-M1-01; FA-M1-09; QA-M1-03, 12)*
- **US-M1-03** Als Mitarbeiter möchte ich mich abmelden, damit am gemeinsam genutzten Werkstatt-Tablet niemand meine Daten sieht. → *Akzeptanz:* Nach dem Abmelden erscheint die Anmeldeseite. Der Zurück-Button des Browsers zeigt keine geschützten Seiten. *(Bezug: UC-M1-02; FA-M1-02)*
- **US-M1-04** Als Mitarbeiter möchte ich mein Passwort selbst ändern, damit ich es regelmäßig erneuern kann. → *Akzeptanz:* Das alte Passwort muss eingegeben werden. Das neue Passwort erfüllt die Passwortregeln. Eine Bestätigung zeigt die erfolgreiche Änderung. *(Bezug: UC-M1-03; FA-M1-06; QA-M1-03)*
- **US-M1-05** Als Admin möchte ich die Rolle eines Benutzers ändern, damit ein beförderter Geselle als Projektleitung Mitarbeiter betreuen kann. → *Akzeptanz:* Es sind nur die Rollen Admin, Projektleitung und Mitarbeiter wählbar. Die neuen Rechte gelten ab der nächsten Anmeldung. Eine Projektleitung kann keine Rollen ändern. *(Bezug: UC-M1-08; FA-M1-04)*
- **US-M1-06** Als Admin möchte ich ein Benutzerkonto sperren und entsperren, damit ich den Zugang bei Bedarf sofort unterbinden kann. → *Akzeptanz:* Ein gesperrtes Konto kann sich nicht anmelden. Nach dem Entsperren ist die Anmeldung wieder möglich. Alle Daten des Mitarbeiters bleiben erhalten. *(Bezug: UC-M1-09; FA-M1-05)*
- **US-M1-07** Als Admin möchte ich einem Mitarbeiter ein Einmalpasswort per E-Mail senden, damit er nach einem vergessenen Passwort schnell weiterarbeiten kann. → *Akzeptanz:* Das Einmalpasswort geht an die hinterlegte E-Mail-Adresse. Schlägt der Versand fehl, bleibt das alte Passwort gültig und der Admin sieht eine Fehlermeldung. Das Einmalpasswort ist höchstens 24 Stunden gültig. *(Bezug: UC-M1-10; FA-M1-08; QA-M1-11, 12)*
- **US-M1-08** Als Admin möchte ich einen neuen Mitarbeiter samt Benutzerkonto in einem Formular anlegen, damit er ab dem ersten Arbeitstag Zeiten erfassen kann. → *Akzeptanz:* Pflichtfelder sind markiert. Personalnummer und Benutzername sind eindeutig. Nach dem Speichern erhält der Mitarbeiter ein Einmalpasswort per E-Mail. *(Bezug: UC-M1-04; FA-M1-10, 13; FA-M1-03, 09)*
- **US-M1-09** Als Admin möchte ich Arbeitszeitmodell, Überstunden-Startsaldo und Urlaubsdaten hinterlegen, damit Zeiterfassung und Urlaubsverwaltung richtig rechnen. → *Akzeptanz:* Mindestens ein Arbeitstag ist gewählt. Der Urlaubsanspruch beträgt mindestens 20 Tage. Der Startsaldo darf negativ sein. *(Bezug: UC-M1-04, 05; FA-M1-16, 17, 18; QA-M1-07)*
- **US-M1-10** Als Admin möchte ich jedem Mitarbeiter einen Vorgesetzten zuordnen, damit Urlaubs- und Korrekturanträge bei der richtigen Projektleitung landen. → *Akzeptanz:* Wählbar sind nur Benutzer mit der Rolle Projektleitung oder Admin. Ein Mitarbeiter kann nicht sein eigener Vorgesetzter sein. *(Bezug: UC-M1-04, 05; FA-M1-19; QA-M1-07)*
- **US-M1-11** Als Projektleitung möchte ich die Daten eines Mitarbeiters bearbeiten, damit z. B. eine geänderte Wochenarbeitszeit sofort berücksichtigt wird. → *Akzeptanz:* Es gelten dieselben Prüfungen wie beim Anlegen. Urlaubsdaten, Überstunden-Startsaldo, Vorgesetzten, Rolle und Kontostatus kann die Projektleitung nicht ändern. *(Bezug: UC-M1-05; FA-M1-11, 16)*
- **US-M1-12** Als Admin möchte ich einen ausgetretenen Mitarbeiter deaktivieren statt löschen, damit seine erfassten Zeiten und Anträge erhalten bleiben. → *Akzeptanz:* Das Austrittsdatum ist Pflicht und liegt nach dem Eintrittsdatum. Ein Vorgesetzter lässt sich erst deaktivieren, wenn seine Mitarbeiter neu zugeordnet sind. Deaktivierte Mitarbeiter erscheinen in den anderen Modulen nicht mehr zur Auswahl. *(Bezug: UC-M1-06; FA-M1-12, 14; QA-M1-06)*
- **US-M1-13** Als Mitarbeiter möchte ich meine eigenen Daten ansehen, damit ich z. B. meinen Urlaubsanspruch und meine Wochenarbeitszeit prüfen kann. → *Akzeptanz:* Die Ansicht zeigt nur die eigenen Daten. Die Daten sind nur lesbar, nicht änderbar. *(Bezug: UC-M1-07; FA-M1-K3)*
- **US-M1-14** Als Projektleitung möchte ich alle Mitarbeiter in einer Liste sehen, damit ich einen Mitarbeiter zum Bearbeiten auswählen kann. → *Akzeptanz:* Die Liste zeigt Personalnummer, Name, Qualifikation und Status. Ein Klick öffnet den Mitarbeiter. Bei bis zu 50 Einträgen lädt sie in unter 2 Sekunden. *(Bezug: UC-M1-11; FA-M1-15; QA-A-03)*
- **US-M1-15** Als Admin möchte ich alle Benutzerkonten in einer Liste sehen, damit ich auf einen Blick erkenne, wer welche Rolle hat und welche Konten gesperrt sind. → *Akzeptanz:* Die Liste lässt sich nach Rolle und Status filtern. Nur der Admin sieht den Menüpunkt. *(Bezug: UC-M1-K1; FA-M1-K1)*
- **US-M1-16** Als Admin möchte ich, dass ein Konto nach 5 Fehlversuchen automatisch gesperrt wird, damit Passwörter nicht durch Ausprobieren erraten werden. → *Akzeptanz:* Nach dem 5. Fehlversuch in Folge ist das Konto gesperrt. Eine erfolgreiche Anmeldung setzt den Zähler zurück. Nur der Admin kann das Konto wieder entsperren. *(Bezug: UC-M1-K2; FA-M1-K2)*
- **US-M1-17** Als Admin möchte ich einem Mitarbeiter einen internen Stundensatz zuordnen, damit die Projektkosten kalkuliert werden können. → *Akzeptanz:* Der Stundensatz ist ein optionales Feld in Euro pro Stunde. Nur der Admin sieht und ändert ihn. *(Bezug: UC-M1-04, 05; FA-M1-K4)*
- **US-M1-18** Als Projektleitung möchte ich die Mitarbeiterliste durchsuchen und filtern, damit ich schnell den richtigen Mitarbeiter finde. → *Akzeptanz:* Suche nach Name und Personalnummer. Filter nach Status (aktiv/inaktiv) und Qualifikation. *(Bezug: UC-M1-K3; FA-M1-K5)*
