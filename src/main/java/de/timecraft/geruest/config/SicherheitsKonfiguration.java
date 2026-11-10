package de.timecraft.geruest.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import de.timecraft.geruest.layout.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Anmeldung mit Vaadin und Spring Security. Ohne Anmeldung ist keine Seite erreichbar.
 * Views brauchen {@code @PermitAll} oder {@code @RolesAllowed}, sonst sind sie gesperrt.
 * Die Quelle der Benutzer ist ein {@code UserDetailsService}-Bean und kann ausgetauscht werden.
 */
@Configuration
@EnableWebSecurity
public class SicherheitsKonfiguration {

    @Bean
    public SecurityFilterChain sicherheitsKette(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), konfiguration -> konfiguration.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwortVerschluesselung() {
        return new BCryptPasswordEncoder();
    }
}
