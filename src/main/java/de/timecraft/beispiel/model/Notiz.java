package de.timecraft.beispiel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Eine kurze Notiz. Die ersteller-ID ist eine einfache Zahl, keine Beziehung zu einer anderen Entity. */
@Entity
@Table(name = "notiz")
public class Notiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private LocalDateTime erstelltAm;

    @Column(nullable = false)
    private long erstellerId;

    protected Notiz() {
        // Nur fuer JPA
    }

    public Notiz(String text, LocalDateTime erstelltAm, long erstellerId) {
        this.text = text;
        this.erstelltAm = erstelltAm;
        this.erstellerId = erstellerId;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getErstelltAm() {
        return erstelltAm;
    }

    public long getErstellerId() {
        return erstellerId;
    }
}
