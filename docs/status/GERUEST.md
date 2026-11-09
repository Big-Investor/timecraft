# Status Gerüst
Stand: 2026-11-09, letztes AP: G3 Anmeldung, Tests: grün

## Erledigt
- **G1 Projekt und Datenbank:** Maven-Projekt (`pom.xml`), `TimeCraftApplication`, `docker-compose.yml` (PostgreSQL 16, Volume, Port 5432), `application.properties` (Entwicklung), `application-test.properties` (H2, PostgreSQL-Modus), `.gitignore`, Smoke-Test „Kontext lädt“ (läuft ohne Docker). Start gegen PostgreSQL ausprobiert.
- **G2 Common und Clock:** `geruest.common` (`FachlicheException`, `ZugriffVerweigertException`, `NichtAngemeldetException`, `NichtGefundenException`, `Meldungen`), `geruest.config.ClockKonfiguration` (Clock für Europe/Berlin), `benutzer.api.Rolle` (Text exakt nach CLAUDE.md). Tests: `MeldungenTest`, `ClockKonfigurationTest`.

- **G3 Anmeldung:** `geruest.config.SicherheitsKonfiguration` (Spring Security mit `VaadinSecurityConfigurer`, BCrypt), `geruest.config.PlatzhalterBenutzer` (`mitarbeiter`=1, `projektleitung`=2, `admin`=3, gemeinsames Entwicklungspasswort `timecraft` aus `application.properties`), `geruest.common.TimeCraftBenutzer`, `geruest.common.AktuelleSitzung`, `geruest.layout.LoginView`, Theme `timecraft` (Farben, Schrift, Anmeldeseite) in `src/main/frontend/themes/timecraft/styles.css`. Session-Timeout 30 Minuten. Tests: `AktuelleSitzungTest`, `PlatzhalterBenutzerTest`, `SicherheitsKonfigurationTest` (nicht angemeldet → Umleitung auf `/login`, Anmeldung je Rolle, falsches Passwort, Timeout). App gestartet und im Browser mit dem Mockup verglichen, Anmeldung mit `admin` ausprobiert.

## Als Nächstes
- **G4 Layout und Startseite:** `HauptLayout` (Kopfzeile mit Abmelden über `AuthenticationContext.logout()`, Seitenmenü), `MenuEintrag`, `StartView` mit `@PermitAll`; Theme erweitern; Mockup vorher ansehen (z. B. `M2 · Startseite Stempeln@1x.png` für Kopfzeile und Menü); Test „Menü zeigt nur Einträge der Rolle“.

## Entscheidungen
- **Versionen (von Tim bestätigt, 2026-11-09):** Spring Boot 3.5.16, Vaadin 24.10.14 (über `vaadin-bom`), Java 21. Vaadin 25 braucht Spring Boot 4 und wird deshalb nicht genommen.
- **Abhängigkeiten:** nur die in CLAUDE.md Abschnitt 4 erlaubten (Vaadin, JPA, Security, Validation, Mail-Starter, PostgreSQL-Treiber, H2 und Spring-Security-Test nur für Tests).
- **Tests:** Testprofil `test` (`src/test/resources/application-test.properties`). Spring-Boot-Tests tragen `@ActiveProfiles("test")`.
- **Ports änderbar:** Standard bleibt 5432 (Datenbank) und 8080 (Anwendung). Ist ein Port belegt, gilt `DB_PORT` (Datenbank, in `docker-compose.yml` und `application.properties`) bzw. `SERVER_PORT` (Anwendung, Spring-Boot-Standard). Beispiel: `DB_PORT=5433 docker compose up -d db`, dann `DB_PORT=5433 SERVER_PORT=8081 mvn spring-boot:run`. Anlass: Auf Tims Rechner belegen ein lokaler PostgreSQL-Dienst 5432 und ein anderes Docker-Projekt 8080.
- **Vaadin-Produktionsbuild:** nur im Profil `production` (`mvn -Pproduction package`). Für `mvn test` und `mvn spring-boot:run` (Entwicklungsmodus) nicht nötig.
- **Fehlerklassen:** Jede Klasse hat nur den Konstruktor `(String meldung)`; die Meldung ist der deutsche Text für die Person. `Meldungen.fehler(RuntimeException)` zeigt bei den vier Fehlerklassen deren Meldung, bei allen anderen Ausnahmen nur „Es ist ein unerwarteter Fehler aufgetreten. Bitte versuchen Sie es erneut.“ (Details nur im Log). `Meldungen.fehlertext(...)` ist die testbare Textwahl; `erfolg(...)` und `fehler(...)` brauchen eine Oberfläche und werden nicht einzeln getestet. Position und Dauer (oben Mitte, 5 s) sind Vaadin-Standardwahl, Mockups legen das nicht fest.
- **`TimeCraftBenutzer` (Abweichung von der Paketliste in CLAUDE.md):** Zusätzliche Klasse in `geruest.common`, erbt von Spring Security `User` und trägt Mitarbeiter-ID und Rolle. `AktuelleSitzung` liest sie aus dem Security-Kontext. **Vertrag für M1:** Der echte `UserDetailsService` muss `TimeCraftBenutzer` liefern, bei jedem Aufruf als neues Objekt (Spring Security löscht sonst das Passwort im gemeinsam genutzten Objekt, danach schlägt jede weitere Anmeldung fehl). `PlatzhalterBenutzerTest` prüft das.
- **Views brauchen eine Zugriffs-Annotation:** Ohne Anmeldung ist alles gesperrt. Jede Modul-View braucht `@PermitAll` oder `@RolesAllowed(...)`, sonst ist sie auch für angemeldete Personen unerreichbar. Das gehört in die README und ins Beispielmodul.
- **Session-Timeout:** `server.servlet.session.timeout=30m` und zusätzlich `vaadin.closeIdleSessions=true`, sonst verlängert der Vaadin-Heartbeat die Sitzung bei offener Seite endlos.
- **Anmeldeseite, Abweichungen vom Mockup:** (1) Fehlermeldung steht über den Feldern statt zwischen Passwort und Button (Vaadin-`LoginForm` legt das so fest), (2) Text nur „Benutzername oder Passwort ist falsch.“ ohne „Noch 3 Versuche bis zur Sperre“ (Sperre ist Sache von M1), (3) ohne das „!“-Symbol vor der Meldung, (4) Schrift: Source Sans 3 aus dem Mockup wird nicht mitgeliefert (keine Zusatzbibliothek), Rückfall auf Systemschrift. Der Satz „Passwort vergessen? … Einmalpasswort per E-Mail“ steht wörtlich nach Mockup, die Funktion dahinter baut M1.
- **Theme:** Alle Stile liegen in `themes/timecraft/styles.css`. Stile in `themes/timecraft/components/` greifen im Entwicklungsmodus nicht (Vaadin lädt das fertige Standard-Bundle); Vaadin-Komponenten werden deshalb per `::part(...)` im Dokument gestaltet.
- **Pakete:** Sie entstehen mit den Arbeitspaketen (Git kennt keine leeren Ordner): `geruest.common`, `geruest.config`, `benutzer.api` mit G2, `geruest.layout` mit G3; `beispiel` folgt mit G5.
- **Open-in-View aus:** `spring.jpa.open-in-view=false`. Lazy-Zugriffe in Views außerhalb einer Transaktion schlagen fehl; Module laden, was sie brauchen, im Service.
- **Vaadin-Frontend-Dateien:** `src/main/frontend/index.html` ist im Repository. Weitere von Vaadin erzeugte Dateien (`package.json`, `vite.config.ts` u. a.) entstehen beim ersten Start; ob sie ins Repository kommen (Vaadin-Standard, weniger Merge-Konflikte zwischen Modul-Branches), wird in G6 nach dem ersten vollständigen Start entschieden.
- **Git:** Repository lokal angelegt, Branch `geruest`, kein Remote. `Mockups/` und `CLAUDE.md` liegen im Repository.

## Offene Fragen
- Remote-Repository für das Team: Wo liegt es (GitHub/GitLab HSBI)? Vorschlag: Tim legt es an, `geruest` wird dorthin gepusht, `main` bleibt per Pull Request geschützt.

## Hinweise aus Reviews
- G1, Review OK (keine Blocker/Mängel). Hinweise:
  - Testprofil greift nur mit `@ActiveProfiles("test")` an jeder Testklasse; ohne die Annotation versucht ein Test PostgreSQL zu erreichen. In G5/G6 im Beispielmodul und in der README erwähnen.
  - `vaadin-spring-boot-starter` bringt Hilla und Atmosphere-Push mit; bei G3/G4 kann die Security-Konfiguration dadurch etwas Aufwand machen.
  - Das generierte Sicherheitspasswort im Log verschwindet mit G3.
  - Übernommen: fester `container_name` aus `docker-compose.yml` entfernt (Konfliktgefahr mit anderen Projekten); Smoke-Test prüft jetzt, dass H2 verwendet wird.
- G2, Review OK (keine Blocker/Mängel). Hinweise:
  - `Meldungen.istBekannt` zählt die vier Fehlerklassen einzeln auf; kommt eine fünfte hinzu, muss sie dort ergänzt werden.
- G3, Review: ein Mangel (Doku sagte „ohne !-Symbol“, CSS enthielt aber einen `::before`-Block; Block entfernt, da das Symbol im Browser nicht erschien), sonst nur Hinweise:
  - Übernommen: Anmelde-Test prüft jetzt über den Security-Kontext Mitarbeiter-ID und Rolle je Benutzer.
  - `vaadin.closeIdleSessions=true` ist nicht durch einen Test abgesichert, nur die Property `server.servlet.session.timeout` (Begründung steht unter „Entscheidungen“).
  - Kein Test, dass `/login` anonym erreichbar ist: MockMvc erreicht den Vaadin-Servlet nicht; das wurde von Hand im Browser geprüft.
  - Uhr-Symbol ist `VaadinIcon.CLOCK` (gefüllt), im Mockup eine Kontur; unwesentlich.
