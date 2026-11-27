package de.timecraft.zeiterfassung.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** @author Noah Cipa */
@Component
public class FeiertagVorbelegung {

    private final FeiertagService feiertagService;

    public FeiertagVorbelegung(FeiertagService feiertagService) {
        this.feiertagService = feiertagService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void beimStart() {
        feiertagService.vorbelegen();
    }
}
