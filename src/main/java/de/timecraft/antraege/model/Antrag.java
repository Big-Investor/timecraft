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

    public Long getId() { return id; }

    public long getAntragstellerId() { return antragstellerId; }
    public void setAntragstellerId(long antragstellerId) { this.antragstellerId = antragstellerId; }

    public Long getEntscheiderId() { return entscheiderId; }
    public void setEntscheiderId(Long entscheiderId) { this.entscheiderId = entscheiderId; }

    public Long getBezugsantragId() { return bezugsantragId; }
    public void setBezugsantragId(Long bezugsantragId) { this.bezugsantragId = bezugsantragId; }

    public Antragsstatus getStatus() { return status; }
    public void setStatus(Antragsstatus status) { this.status = status; }

    public Instant getEingereichtAm() { return eingereichtAm; }
    public void setEingereichtAm(Instant eingereichtAm) { this.eingereichtAm = eingereichtAm; }

    public String getBegruendung() { return begruendung; }
    public void setBegruendung(String begruendung) { this.begruendung = begruendung; }

    public Instant getEntschiedenAm() { return entschiedenAm; }
    public void setEntschiedenAm(Instant entschiedenAm) { this.entschiedenAm = entschiedenAm; }

    public String getEntscheidungshinweis() { return entscheidungshinweis; }
    public void setEntscheidungshinweis(String entscheidungshinweis) { this.entscheidungshinweis = entscheidungshinweis; }
}
