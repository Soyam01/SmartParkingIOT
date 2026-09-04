package com.app.demo.controller;

import com.app.demo.service.SpotEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/spots")
public class SpotEventController {

    private final SpotEventService spotEventService;

    public SpotEventController(SpotEventService spotEventService) {
        this.spotEventService = spotEventService;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSpotUpdates() {
        return spotEventService.subscribe();
    }
}
