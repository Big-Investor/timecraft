package de.timecraft;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimeCraftApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void kontextLaedtUndNutztTestdatenbank() throws Exception {
        try (var verbindung = dataSource.getConnection()) {
            assertThat(verbindung.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
        }
    }
}
