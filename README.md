# TimeCraft

Digitale Zeiterfassung mit Projektübersicht (Softwareprojekt Wirtschaftsinformatik, Hochschule Bielefeld). Läuft nur lokal und nur mit erfundenen Testdaten.

Dieses Repository enthält das **Gerüst**: Anmeldung (Benutzerverwaltung aus M1), Layout mit Menü, Startseite, Datenbank und ein Beispielmodul als Vorlage. Die sieben Module (Benutzer, Zeiterfassung, Projekte, Material, Anträge, Kalender, Auswertung) bauen darauf auf.

## Voraussetzungen

- Java 21
- Maven 3.9 oder neuer
- Docker (nur für die Datenbank im Betrieb, `mvn test` braucht es nicht)

## Starten

```
docker compose up -d db
mvn spring-boot:run
```

Danach ist die Anwendung unter http://localhost:8080 erreichbar. Der erste Aufruf im Browser braucht einige Sekunden, weil Vaadin im Entwicklungsmodus die Oberfläche aufbaut.

Die Tabellen legt Hibernate beim Start selbst an (`ddl-auto=update`), es gibt kein Flyway.

**Ist ein Port belegt?** Die Standardports sind 5432 (Datenbank) und 8080 (Anwendung). Mit Umgebungsvariablen weichst Du aus, ohne etwas im Projekt zu ändern:

```
DB_PORT=5433 docker compose up -d db
DB_PORT=5433 SERVER_PORT=8081 mvn spring-boot:run
```

In PowerShell: `$env:DB_PORT=5433; $env:SERVER_PORT=8081; mvn spring-boot:run`.

**Datenbank beenden oder zurücksetzen:** `docker compose down` beendet sie, die Daten bleiben erhalten. `docker compose down -v` löscht zusätzlich alle Daten.

## Testbenutzer

Die Anmeldung kommt aus M1 (Benutzerverwaltung, Tabelle `benutzer`). Beim ersten Start mit leerer Datenbank legt M1 Demo-Daten an (sieben erfundene Personen der Tischlerei Hartmann), darunter diese drei:

| Benutzername     | Rolle          | Mitarbeiter-ID |
|------------------|----------------|----------------|
| `mitarbeiter`    | Mitarbeiter    | 1 (Jonas Becker)   |
| `projektleitung` | Projektleitung | 2 (Lena Schröder)  |
| `admin`          | Admin          | 3 (Paul Hartmann)  |

Das Passwort ist für alle Demo-Benutzer `timecraft1` (Entwicklungswert `timecraft.demo.passwort` in `application.properties`; Passwörter brauchen mindestens 8 Zeichen und eine Ziffer). Die Mitarbeiter-IDs 1 bis 3 gelten nur für eine neue, leere Datenbank. Die Sitzung endet nach 30 Minuten ohne Aktivität.

## E-Mail-Versand (Einmalpasswörter, M1)

Beim Anlegen eines Mitarbeiters und bei „Einmalpasswort senden“ verschickt TimeCraft eine E-Mail über SMTP. Dafür gibt es ein GMX-Postfach nur für das Projekt (Adresse und App-Passwort im Team erfragen). Die Zugangsdaten stehen **nie im Repository**. Jede Person legt dafür außerhalb des Projektordners die Datei `~/.timecraft/mail.properties` an:

```
MAIL_HOST=mail.gmx.net
MAIL_PORT=587
MAIL_USER=<Projektadresse>@gmx.de
MAIL_PASSWORD=<App-Passwort, im Team erfragen>
MAIL_ABSENDER=<Projektadresse>@gmx.de
```

Statt der Datei gehen auch Umgebungsvariablen mit denselben Namen. Ohne Zugangsdaten schlägt der Versand fehl (verständliche Meldung, das alte Passwort bleibt gültig). Wer keine echte Mail braucht, startet mit dem Profil `fake`: Dann steht die Mail samt Einmalpasswort nur im Log.

## Tests

```
mvn test
```

läuft **ohne Docker** gegen eine H2-Datenbank im PostgreSQL-Modus (Profil `test`, Datei `src/test/resources/application-test.properties`). Jede Spring-Testklasse braucht dafür `@ActiveProfiles("test")`, sonst versucht sie, PostgreSQL zu erreichen. Weil der Betrieb PostgreSQL nutzt, gibt es in Entities und Queries nur Standard-JPA/JPQL, keine nativen Queries und nichts PostgreSQL-Spezifisches.

## Aufbau

Basispaket `de.timecraft`:

```
de.timecraft
├─ TimeCraftApplication
├─ geruest
│  ├─ config    ClockKonfiguration, SicherheitsKonfiguration
│  ├─ layout    HauptLayout, SeitenMenue, MenuEintrag, StartView
│  └─ common    Fehlerklassen, AktuelleSitzung, TimeCraftBenutzer, Meldungen
├─ benutzer    M1: api (Rolle, MitarbeiterAuskunft), model, repository, service,
│               view (u. a. AnmeldeView = Anmeldeseite, Passwort ändern/festlegen)
└─ beispiel     model, repository, service, view   (Vorlage, siehe unten)
```

Die Optik (Farben, Schrift, Abstände) liegt einmal in `src/main/frontend/themes/timecraft/styles.css` und gilt für alle Module. Die Designs liegen als Bilder in `Mockups/`.

### Gemeinsame Bausteine

- **Fehlerklassen** (`geruest.common`): `FachlicheException` (ungültige Eingabe, verletzte Fachregel, deutsche Meldung für Menschen), `ZugriffVerweigertException` (Rolle reicht nicht), `NichtAngemeldetException`, `NichtGefundenException`. Services werfen sie, Views fangen sie ab und zeigen sie mit `Meldungen.fehler(...)`. Nie ein Stacktrace in der Oberfläche.
- **Uhr:** `Clock` wird per Konstruktor in den Service gegeben (Zeitzone Europe/Berlin). Kein `now()` ohne Clock in Fachcode; Tests setzen eine feste Uhr.
- **Angemeldete Person:** `AktuelleSitzung` liefert `mitarbeiterId()`, `benutzername()`, `rolle()` und `istAngemeldet()`. **Nur Views** benutzen sie und reichen die Werte als Parameter an den Service weiter. Der Service prüft die Rechte selbst und kennt den Webkontext nicht, damit er ohne Oberfläche testbar ist. `Rolle == null` im Service heißt „niemand angemeldet“ (`NichtAngemeldetException`).
- **Menü:** Jedes Modul stellt seine Einträge als `MenuEintrag`-Bean bereit (`bezeichnung()`, `route()`, `erlaubteRollen()`, `reihenfolge()`). Das Layout sammelt sie, sortiert sie und zeigt nur die der eigenen Rolle. Das ist nur Anzeige; die Rechte prüft immer der Service.
- **Formate:** `dd.MM.yyyy`, `HH:mm`, Komma als Dezimaltrenner. Oberfläche und Fehlermeldungen ausschließlich deutsch.

## Ein Modul anlegen (Beispiel: `beispiel`)

Das Beispielmodul `beispiel` ist die Vorlage. Es zeigt genau den Aufbau, den jedes Modul hat. Lies zuerst `NotizService`, `NotizView` und `NotizServiceTest`.

1. **Paket** `de.timecraft.<modul>` mit den Unterpaketen `model`, `repository`, `service`, `view`.
2. **Entity** (`model`): einfache Felder; Verweise auf andere Module nur als ID (Zahl), nicht als Beziehung. Schema entsteht aus der Entity.
3. **Repository** (`repository`): Spring Data, nur Standard-JPA/JPQL.
4. **Service** (`service`): Konstruktor-Injektion (Repository, `Clock`). Methoden bekommen die Person als Parameter (`long mitarbeiterId, Rolle rolle, ...`), prüfen die Rechte und werfen die Fehlerklassen.
5. **View** (`view`): holt die Person aus `AktuelleSitzung`, ruft den Service, meldet Fehler über `Meldungen`. Keine Fachlogik, kein Repository-Zugriff. Jede View braucht drei Angaben:
   - `@Route(value = "...", layout = HauptLayout.class)` für den Rahmen mit Menü und Kopfzeile,
   - `@PageTitle("...")` (sonst bleibt der Titel in der Kopfzeile leer),
   - `@PermitAll` oder `@RolesAllowed(...)` (ohne Annotation ist die View auch für Angemeldete gesperrt). Die Rollennamen sind die Namen aus `Rolle` ohne Präfix, z. B. `@RolesAllowed({"PROJEKTLEITUNG", "ADMIN"})`.
6. **Menüpunkt:** eine `@Component`-Klasse, die `MenuEintrag` umsetzt. `route()` ist derselbe Wert wie in `@Route` der View, `erlaubteRollen()` entspricht der Annotation der View. Die Gruppe im Menü ergibt sich aus `reihenfolge()`: **101 bis 199** = Zeit (100 ist „Start“), **200 bis 299** = Betrieb, **ab 300** = Verwaltung. Nimm möglichst eindeutige Zahlen; eine Zahl unter 100 landet unbemerkt in „Zeit“.
7. **Tests:** Service-Tests (Normalfall, Fehlerfall, Rolle, feste Uhr) und ein Repository-Test gegen H2 mit `@ActiveProfiles("test")`. Für Repository-Tests zusätzlich `@DataJpaTest` und `@AutoConfigureTestDatabase(replace = NONE)`.

Das Beispielmodul darf nie von einem echten Modul benutzt werden. Ob es nach dem Start der Module bestehen bleibt, entscheidet das Team (siehe `docs/status/GERUEST.md`).

## Arbeitsweise im Team

- Gerüst auf `main`; jedes Modul auf einem eigenen Branch: `modul/m1-benutzer`, `modul/m2-zeiterfassung`, `modul/m3-projekte`, `modul/m4-material`, `modul/m5-antraege`, `modul/m6-kalender`, `modul/m7-auswertung`.
- Kleine Commits mit klarer Nachricht. Kein Push auf `main`, kein Force-Push. Zusammenführen nur per Pull Request mit Durchsicht durch eine zweite Person.
- `mvn test` muss für das ganze Projekt grün sein, ohne `@Disabled`.
- Bezeichner und Kommentare deutsch mit ASCII-Umlauten (`ae`, `oe`, `ue`, `ss`), Texte in der Oberfläche deutsch mit richtigen Umlauten.
- Stand und Entscheidungen des Gerüsts: `docs/status/GERUEST.md`.
