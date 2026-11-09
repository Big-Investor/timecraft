# TimeCraft – Gerüst (gemeinsame Basis für alle sieben Module)

Diese Datei steuert Claude Code beim Bau des **Gerüsts**. Das Gerüst ist ein lauffähiges Programm mit **Anmeldung (Platzhalter), einer Seite und der Datenbank**. Erst wenn es steht und auf `main` liegt, starten die sieben Modul-Dateien (`CLAUDE_M1…M7`). Verantwortlich: Tim. Branch: `geruest`.

## 1. Was TimeCraft ist (Kurzfassung)
Digitale Zeiterfassung mit Projektübersicht (Softwareprojekt WI, Hochschule Bielefeld, 7 Personen, je Person ein Modul). Läuft nur lokal, nur erfundene Testdaten. Rollen: **MITARBEITER**, **PROJEKTLEITUNG**, **ADMIN** (jedes Konto hat genau eine Rolle; Admin-Konten sind getrennt). Nicht gebaut: KI/BI, mobile App, Lohn, Rechnungen, Zahlungen, GPS, Schichtmodelle.
Module: M1 Benutzer (`benutzer`), M2 Zeiterfassung (`zeiterfassung`), M3 Projekte (`projekte`), M4 Material (`material`), M5 Anträge (`antraege`), M6 Kalender (`kalender`), M7 Auswertung (`auswertung`).

## 2. Ziel und Umfang
Das Gerüst liefert genau das, was alle Module gemeinsam brauchen, und **nichts Fachliches**:
1. Maven-Projekt, das mit `mvn test` grün durchläuft und mit `mvn spring-boot:run` startet.
2. **Datenbank PostgreSQL** per Docker Compose; die Anwendung verbindet sich und erzeugt das Schema aus den Entities.
3. **Platzhalter-Anmeldung**: Login-Seite, feste Testbenutzer je Rolle, Abmelden, Session-Timeout 30 Minuten.
4. **Layout** mit Kopfzeile, Seitenmenü und einer **Startseite**; Module tragen ihre Menüpunkte selbst ein.
5. **Gemeinsame Bausteine** (`geruest.common`): Fehlerklassen, Clock, Helfer für die angemeldete Person.
6. Ein **Beispielmodul** als Vorlage, wie ein Modul aufgebaut ist (View, Service mit Rechteprüfung, Repository, Entity, Tests).
7. `README.md` mit Startanleitung, `docs/status/GERUEST.md`.

**Nicht jetzt:** echte Benutzerverwaltung (M1), Passwörter in der Datenbank, Mailversand, irgendeine Fachlogik der Module, Beispieldaten der Module, Flyway/Liquibase.

## 3. Spielregeln
- **Fragen statt raten.** Unklarheit, Lücke oder Widerspruch: kurz fragen, mit Vorschlag (AskUserQuestion). Das gilt besonders für Versionen und neue Abhängigkeiten.
- **Schlicht.** Kleine Klassen, kurze Methoden, Konstruktor-Injektion, kein Lombok, keine Reflection, keine eigenen Frameworks. Lieber zehn Zeilen mehr, die alle sofort verstehen. Kein `System.out`, kein auskommentierter Code, keine unbenutzten Klassen.
- **Frische Sitzungen.** Ein Arbeitspaket (Abschnitt 8) pro Sitzung. Stand steht in `docs/status/GERUEST.md`. Zu Beginn: diese Datei und die Statusdatei lesen, dann `mvn test` (muss grün sein, sonst zuerst reparieren oder nachfragen).
- **Alle Tests grün.** `mvn test` für das ganze Projekt, ohne `@Disabled`.
- **Git:** Branch `geruest`, kleine Commits `Gerüst: <AP-Name>`, kein Push auf `main`, kein Force-Push. Zusammenführen per Pull Request mit Durchsicht durch eine zweite Person.
- **Sprache:** Bezeichner und Kommentare deutsch (ASCII-Umlaute: `ae`, `oe`, `ue`, `ss`), Oberfläche und Fehlermeldungen ausschließlich deutsch und verständlich (nie Stacktraces in der Oberfläche).

## 4. Technik
- Java 21, Maven, Spring Boot, Vaadin Flow, Spring Data JPA, Spring Security, JUnit 5, Docker Compose.
- **Versionen:** Vorschlag Spring Boot 3.x und Vaadin 24.x (jeweils aktuelle stabile Version, zueinander passend). Vor dem Festschreiben in der `pom.xml` von der Person bestätigen lassen. Danach gelten sie für alle Module.
- Weitere Abhängigkeiten nur: PostgreSQL-Treiber, Validation, Mail-Starter (nur Abhängigkeit, keine Nutzung), H2 (nur Test, PostgreSQL-Modus), Spring-Security-Test. Alles andere nur nach Rückfrage.
- **Datenbank:** PostgreSQL im Docker Compose (`docker-compose.yml` mit `db`-Dienst, Volume, Port 5432; Zugangsdaten nur als Entwicklungswerte in `application.properties`, nicht geheim). Schema: Hibernate `ddl-auto=update`, kein Flyway/Liquibase. Entities später 1:1 zum Relationenmodell.
- **Tests:** `mvn test` läuft **ohne Docker** mit H2 im PostgreSQL-Modus (eigene `application-test.properties`). Der Betrieb nutzt PostgreSQL. Deshalb in Entities und Queries nur Standard-JPA/JPQL, keine PostgreSQL-spezifischen Funktionen und keine nativen Queries.
- Zeitzone Europe/Berlin, deutsche Formate (`dd.MM.yyyy`, `HH:mm`, Komma als Dezimaltrenner). Keine `now()`-Aufrufe in Fachcode: immer eine injizierte `Clock`.

## 5. Struktur
Basispaket `de.timecraft`.
```
de.timecraft
├─ TimeCraftApplication
├─ geruest
│  ├─ config    ClockKonfiguration, SicherheitsKonfiguration
│  ├─ layout    HauptLayout, MenuEintrag (Schnittstelle), StartView, LoginView
│  └─ common    FachlicheException, ZugriffVerweigertException, NichtAngemeldetException,
│               NichtGefundenException, AktuelleSitzung, Meldungen (Hilfe für Benachrichtigungen)
├─ benutzer.api  Rolle            (Platzhalter nach Schnittstellenvertrag, M1 übernimmt)
└─ beispiel      view, service, repository, model   (Vorlage, siehe Abschnitt 7)
```
Die Pakete der Module (`benutzer`, `zeiterfassung`, `projekte`, `material`, `antraege`, `kalender`, `auswertung`) legen die Module selbst an. Das Gerüst legt nur `benutzer.api.Rolle` an, weil das Gerüst die Rolle schon braucht. Text exakt (Besitzer wird M1):
```java
package de.timecraft.benutzer.api;
public enum Rolle { MITARBEITER, PROJEKTLEITUNG, ADMIN }
```

## 6. Bausteine im Detail

### 6.1 Fehlerklassen (`geruest.common`)
- `FachlicheException` (ungültige Eingabe oder verletzte Fachregel; trägt eine deutsche Meldung für Menschen),
- `ZugriffVerweigertException` (Rolle reicht nicht),
- `NichtAngemeldetException`,
- `NichtGefundenException`.
Alle erben von `RuntimeException`. `Meldungen` zeigt eine Ausnahme als Vaadin-Benachrichtigung (Fehlertext, nie Stacktrace). Services werfen diese Klassen, Views fangen sie ab.

### 6.2 Clock
`ClockKonfiguration` stellt eine `Clock`-Bean für `Europe/Berlin` bereit. Services bekommen die Clock per Konstruktor. Tests setzen eine feste Uhr.

### 6.3 Platzhalter-Anmeldung und angemeldete Person
- `SicherheitsKonfiguration`: Spring Security mit Vaadin, eigene `LoginView` (deutsch), Abmelden, Session-Timeout **30 Minuten** (`server.servlet.session.timeout=30m`). Ohne Anmeldung ist keine Seite erreichbar.
- Platzhalter-Benutzer (nur im Speicher, BCrypt-Hash, keine Datenbank): `mitarbeiter`, `projektleitung`, `admin`. Passwort als Entwicklungswert aus `application.properties`. Jeder Benutzer hat eine feste Platzhalter-Mitarbeiter-ID (1, 2, 3) und seine Rolle.
- `AktuelleSitzung`: kleiner Helfer, der aus dem Spring-Security-Kontext **Mitarbeiter-ID, Benutzername und Rolle** der angemeldeten Person liefert (`istAngemeldet()`, `mitarbeiterId()`, `rolle()`). Wirft `NichtAngemeldetException`, wenn niemand angemeldet ist.
- **Regel für alle Module:** Views holen die Person aus `AktuelleSitzung` und reichen sie als Parameter an den Service weiter. Der Service prüft Rechte und kennt den Webkontext nicht, damit er ohne Oberfläche testbar ist.
- M1 ersetzt später die Platzhalter-Benutzer durch die echte Benutzerverwaltung. Die Sicherheitskonfiguration bleibt dafür bestehen: es wird nur die Quelle der Benutzer ausgetauscht (`UserDetailsService`) und `AktuelleSitzung` liest dann die echte Mitarbeiter-ID. Baue die Platzhalter deshalb in **einer** kleinen Klasse (`PlatzhalterBenutzer`), die sich leicht entfernen lässt.

### 6.4 Layout und Menü
- `HauptLayout` (Vaadin `AppLayout`): Kopfzeile mit Name TimeCraft, angemeldete Person samt Rolle, Abmelden; Seitenmenü.
- **Menü-Registrierung:** Schnittstelle `MenuEintrag` mit `bezeichnung()`, `route()`, `erlaubteRollen()`, `reihenfolge()`. Jedes Modul stellt seine Einträge als Spring-Beans bereit; das Layout sammelt alle Beans, sortiert sie und zeigt nur die der eigenen Rolle. (Das ist nur Anzeige; die Rechte prüft immer der Service.)
- `StartView`: Begrüßung, Name und Rolle. Sonst nichts Fachliches.
- Gestaltung und Beschriftungen **nach den Mockups** (Abschnitt 9).

## 7. Beispielmodul `beispiel` (Vorlage)
Ein winziges, bewusst nichtfachliches Modul zeigt allen den Aufbau. Es soll genau das zeigen und nicht mehr:
- `model`: eine Entity `Notiz` (id, text, erstelltAm, ersteller-ID als einfache Zahl, nicht als Beziehung),
- `repository`: `NotizRepository` (Spring Data),
- `service`: `NotizService` mit `anlegen(...)` und `alle(...)`; prüft die Rolle im Service (nur angemeldete Personen; Admin darf zusätzlich löschen), wirft die Fehlerklassen, benutzt die `Clock`,
- `view`: `NotizView` (Liste und ein Formular), meldet Fehler über `Meldungen`, trägt einen `MenuEintrag` ein,
- Tests: Service-Tests (Normalfall, Fehlerfall, Rolle, feste Uhr) und ein Repository-Test gegen H2.
Das Beispielmodul ist **Vorlage und Selbsttest**. Ob es nach dem Start der Module bestehen bleibt oder entfernt wird, entscheidet das Team (Teamfrage in den Status). Es darf nie von einem echten Modul benutzt werden.

## 8. Arbeitspakete (eine frische Sitzung je AP)
1. **G1 Projekt und Datenbank:** Maven-Projekt, Versionen bestätigen lassen, Paketstruktur, `docker-compose.yml` mit PostgreSQL, `application.properties` (Entwicklung) und `application-test.properties` (H2, PostgreSQL-Modus), `.gitignore`, Smoke-Test „Kontext lädt“ (läuft ohne Docker).
2. **G2 Common und Clock:** Fehlerklassen, `Meldungen`, `ClockKonfiguration`, `benutzer.api.Rolle`; Tests.
3. **G3 Anmeldung:** `SicherheitsKonfiguration`, `LoginView`, `PlatzhalterBenutzer`, `AktuelleSitzung`, Session-Timeout; Tests (nicht angemeldet → kein Zugriff, je Rolle richtige Person, Timeout-Einstellung).
4. **G4 Layout und Startseite:** `HauptLayout`, `MenuEintrag`, `StartView`, Theme nach Mockup; Test „Menü zeigt nur Einträge der Rolle“.
5. **G5 Beispielmodul:** wie Abschnitt 7, mit Tests.
6. **G6 Abschluss:** `README.md` (Voraussetzungen, `docker compose up -d db`, `mvn spring-boot:run`, Testbenutzer, `mvn test`, Aufbau eines Moduls anhand des Beispiels, Branch-Regeln), Statusdatei fertig, alles einmal von Grund auf ausprobieren (frischer Klon, Start, Anmelden mit allen drei Rollen, Abmelden).

Faustregel: Ein AP passt in eine Sitzung. Wird es zu groß, teile es und trage die Teile in den Status ein.

## 9. Mockups als Designvorlage
Die Designs liegen als **.png** im Ordner `Mockups/`; es gibt einen Prototyp mit diesem Aussehen. **Das fertige Programm muss so aussehen wie die Mockups**, und das Gerüst legt dafür die gemeinsame Optik (Theme, Kopfzeile, Menü), die alle Module erben. Die Mockups liegen in `Mockups/` (als **.png**, je Seite eine Datei, z. B. `LoginView.png`). Für das Gerüst sind nur **Anmeldeseite, Kopfzeile, Seitenmenü und Startseite** wichtig.
1. Vor G3 und G4 das passende Mockup ansehen (Bilder mit dem Read-Werkzeug öffnen). Aufbau, Beschriftungen und Farbwirkung übernehmen, Beschriftungen wörtlich.
2. Fehlt ein Mockup oder ist es unklar: nachfragen, nicht erfinden. Wo das Mockup nichts festlegt, die einfache Vaadin-Standardlösung nehmen und in der Statusdatei unter „Entscheidungen“ vermerken.
3. Gemeinsame Gestaltung (Farben, Schrift, Abstände) einmal in `src/main/frontend/themes/timecraft/` festlegen, damit alle Module dieselbe Optik erben. Kein eigenes Designsystem, keine Zusatzbibliothek.
4. Nach der Seite: App starten und mit dem Mockup vergleichen.

## 10. Review und „fertig“
Ein AP ist fertig, wenn: (a) `mvn test` grün ist, (b) die App startet und die Seite funktioniert, (c) der Review ohne offene Mängel durch ist, (d) `docs/status/GERUEST.md` aktuell ist, (e) ein Commit auf `geruest` liegt.

**Review in unabhängigem Kontext:** Starte mit dem Agent-Tool einen Subagenten (Typ `general-purpose`, ohne Deinen Verlauf):
> Du bist Reviewer für das TimeCraft-Gerüst. Du prüfst nur und änderst nichts. Lies `CLAUDE.md` und `docs/status/GERUEST.md`, dann `git diff` des aktuellen Arbeitspakets und die geänderten Dateien. Führe `mvn test` aus. Prüfe: 1. **Umfang:** nichts Fachliches, keine unnötigen Abhängigkeiten, keine Datenbankanbindung der Anmeldung. 2. **Schichten:** Rechte nur im Service, Views ohne Fachlogik und ohne Repository-Zugriff. 3. **Einfachheit:** kleine Klassen, kein toter oder auskommentierter Code, keine Duplikate, kein `System.out`. 4. **Wiederverwendbarkeit:** Ein Modul kann Menüpunkt, Route, Person und Fehlerklassen ohne Änderung am Gerüst nutzen. 5. **Tests:** laufen ohne Docker, prüfen echtes Verhalten, keine zeitabhängigen Tests ohne feste Uhr. 6. **Design:** Seite entspricht dem Mockup. Antworte mit „Blocker“ / „Mängel“ / „Hinweise“ (Datei und Zeile) oder „OK“.

Blocker und Mängel beheben, `mvn test`, dann **erneut ein frischer Reviewer**, bis „OK“ kommt. Hinweise kommen in die Statusdatei. Vor dem Zusammenführen prüft eine **zweite Person den Pull Request**.

## 11. Statusdatei `docs/status/GERUEST.md`
```
# Status Gerüst
Stand: <Datum>, letztes AP: <Name>, Tests: grün/rot
## Erledigt
## Als Nächstes
## Entscheidungen   (Versionen, Mockup-Abweichungen, Namen)
## Offene Fragen    (Teamfragen mit Vorschlag)
## Hinweise aus Reviews
```

## 12. Abschluss und Übergabe
Am Ende jeder Sitzung kurz melden: erledigtes AP, Teststand, Review-Ergebnis, offene Fragen, nächstes AP; dann die Sitzung beenden. Nach **G6** und dem Pull Request: `main` enthält das Gerüst. Dann legt jedes Teammitglied seinen Modul-Branch an (`modul/m1-benutzer`, `modul/m2-zeiterfassung`, `modul/m3-projekte`, `modul/m4-material`, `modul/m5-antraege`, `modul/m6-kalender`, `modul/m7-auswertung`), legt seine `CLAUDE_Mx…md` als `CLAUDE.md` in den Projektroot und startet. Die Modul-Dateien verlassen sich auf genau diese Bausteine (Abschnitt 6): `AktuelleSitzung`, die Fehlerklassen, die `Clock`, `MenuEintrag`, PostgreSQL und die Testdatenbank ohne Docker. Weicht das Gerüst am Ende davon ab, sagt Claude es hier in der Statusdatei, damit die Module-Dateien angepasst werden.
