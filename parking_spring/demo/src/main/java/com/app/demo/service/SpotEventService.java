package com.app.demo.service;

import com.app.demo.dto.SpotUpdate;
import com.app.demo.model.Reservation;
import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SpotEventService {

    private final SpotStatusRepository spotStatusRepository;
    private final ReservationRepository reservationRepository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SpotEventService(SpotStatusRepository spotStatusRepository,
                            ReservationRepository reservationRepository) {
        this.spotStatusRepository = spotStatusRepository;
        this.reservationRepository = reservationRepository;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(getSnapshot()));
        } catch (IOException | RuntimeException error) {
            emitters.remove(emitter);
            emitter.completeWithError(error);
        }

        return emitter;
    }

    public void publish(String spotNumber) {
        SpotUpdate update = getEffectiveUpdate(spotNumber);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("spot-update")
                        .data(update));
            } catch (IOException | RuntimeException error) {
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }

    private List<SpotUpdate> getSnapshot() {
        List<SpotUpdate> updates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            updates.add(getEffectiveUpdate("SPOT-" + i));
        }
        return updates;
    }

    private SpotUpdate getEffectiveUpdate(String spotNumber) {
        Optional<SpotStatus> spot = spotStatusRepository.findById(spotNumber);
        Optional<Reservation> reservation =
                reservationRepository.findFirstBySpotNumberAndActiveTrue(spotNumber);

        String physicalStatus = spot.map(SpotStatus::getStatus).orElse("free");
        if ("occupied".equalsIgnoreCase(physicalStatus)) {
            return new SpotUpdate(spotNumber, "occupied", null,
                    spot.map(SpotStatus::getLastUpdated).orElse(null));
        }
        if (reservation.isPresent()) {
            return new SpotUpdate(spotNumber, "reserved", reservation.get().getPlateNumber(),
                    spot.map(SpotStatus::getLastUpdated).orElse(null));
        }
        return new SpotUpdate(spotNumber, "free", null,
                spot.map(SpotStatus::getLastUpdated).orElse(null));
    }
}
