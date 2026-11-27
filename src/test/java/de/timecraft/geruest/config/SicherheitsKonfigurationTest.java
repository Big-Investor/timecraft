package de.timecraft.geruest.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueft die Sicherheitskonfiguration. Die Anmeldung je Rolle und mit falschem Passwort prueft seit
 * M1 der Test {@code de.timecraft.benutzer.service.AnmeldungIntegrationTest} (echte Benutzerverwaltung).
 * Der Umleitungstest laeuft gegen einen echten Server auf einem freien Port, weil Vaadin 24.10.7
 * (Hilla) beim Aufruf einer Seite einen echten Servlet-Container braucht; MockMvc reicht dafuer nicht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SicherheitsKonfigurationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void ohneAnmeldungLeitetEineSeiteAufDieAnmeldungUm() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        HttpRequest anfrage = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/irgendeine-seite")).build();

        HttpResponse<String> antwort = client.send(anfrage, HttpResponse.BodyHandlers.ofString());

        assertThat(antwort.statusCode()).isEqualTo(302);
        assertThat(antwort.headers().firstValue("Location")).hasValueSatisfying(ziel -> assertThat(ziel).endsWith("/login"));
    }

    @Test
    void sitzungEndetNachDreissigMinuten() {
        assertThat(serverProperties.getServlet().getSession().getTimeout()).isEqualTo(Duration.ofMinutes(30));
    }
}
