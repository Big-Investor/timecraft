package de.timecraft.zeiterfassung.model;

import java.time.Instant;

import de.timecraft.geruest.common.FachlicheException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** @author Noah Cipa */
@Entity
@Table(name = "pause")
public class Pause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pause_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zeiteintrag_id", nullable = false)
    private Zeiteintrag zeiteintrag;

    @Column(nullable = false)
    private Instant beginn;

    /** null: Die Pause laeuft noch. */
    private Instant ende;

    protected Pause() {
        // Nur fuer JPA
    }

    public Pause(Zeiteintrag zeiteintrag, Instant beginn) {
        this.zeiteintrag = zeiteintrag;
        this.beginn = beginn;
    }

    public Long getId() {
        return id;
    }

    public long getZeiteintragId() {
        return zeiteintrag.getId();
    }

    public Instant getBeginn() {
        return beginn;
    }

    public Instant getEnde() {
        return ende;
    }

    public void pauseBeenden(Instant ende) {
        if (this.ende != null) {
            throw new FachlicheException("Die Pause ist schon beendet.");
        }
        if (ende.isBefore(beginn)) {
            throw new FachlicheException("Das Ende der Pause darf nicht vor ihrem Beginn liegen.");
        }
        this.ende = ende;
    }
}
