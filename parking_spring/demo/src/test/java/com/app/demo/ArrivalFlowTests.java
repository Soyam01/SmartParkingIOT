package com.app.demo;

import com.app.demo.dto.SpotUpdate;
import com.app.demo.model.Reservation;
import com.app.demo.model.SpotStatus;
import com.app.demo.repository.ReservationRepository;
import com.app.demo.repository.SpotStatusRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ArrivalFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservations;

    @Autowired
    private SpotStatusRepository spots;

    private final List<MvcResult> streams = new ArrayList<>();

    @BeforeEach
    void resetParking() {
        reservations.deleteAll();
        spots.deleteAll();
        for (int number = 1; number <= 5; number++) {
            spots.save(new SpotStatus("SPOT-" + number));
        }
    }

    @AfterEach
    void closeStreams() {
        streams.forEach(this::closeStream);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void registersNormalizedPlateAndMatchesTheReservedDestination(int number) throws Exception {
        Reservation other = seedReservation("SPOT-" + (number % 5 + 1), "XY98ZZ7654", true, null);
        String destination = "SPOT-" + number;
        register(destination, " ba-12 pa 3456 ")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Reservation booking = reservations.findAll().stream()
                .filter(reservation -> "BA12PA3456".equals(reservation.getPlateNumber()))
                .findFirst().orElseThrow();
        assertEquals(destination, booking.getSpotNumber());
        assertTrue(booking.isActive());
        assertNull(booking.getMatchedAt());

        MvcResult match = match("B A12-pa3456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNumber").value("BA12PA3456"))
                .andExpect(jsonPath("$.spotNumber").value(destination))
                .andExpect(jsonPath("$.matchedAt").isNotEmpty())
                .andReturn();
        Number returnedId = JsonPath.read(match.getResponse().getContentAsString(), "$.reservationId");
        assertEquals(booking.getId().longValue(), returnedId.longValue());

        Reservation held = reservations.findById(booking.getId()).orElseThrow();
        assertTrue(held.isActive());
        assertNotNull(held.getMatchedAt());
        assertEquals("free", spots.findById(destination).orElseThrow().getStatus());
        assertNull(reservations.findById(other.getId()).orElseThrow().getMatchedAt());
        register(destination, "OTHER123")
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(2, reservations.count());
    }

    @Test
    void duplicateMatchesKeepIdAndTimestampAndPublishOnlyOnce() throws Exception {
        Reservation booking = seedReservation("SPOT-3", " ba-12 pa 3456 ", true, null);
        MvcResult stream = subscribe();
        assertEquals(5, JsonPath.<List<?>>read(lastEvent(stream, "snapshot"), "$").size());
        assertEquals(List.of(), JsonPath.read(lastEvent(stream, "arrival-snapshot"), "$"));

        MvcResult first = match("BA12-PA3456").andExpect(status().isOk()).andReturn();
        String firstJson = first.getResponse().getContentAsString();
        Number returnedId = JsonPath.read(firstJson, "$.reservationId");
        assertEquals(booking.getId().longValue(), returnedId.longValue());
        assertEquals("BA12PA3456", JsonPath.read(firstJson, "$.plateNumber"));
        assertEquals("SPOT-3", JsonPath.read(firstJson, "$.spotNumber"));
        LocalDateTime matchedAt = LocalDateTime.parse(JsonPath.read(firstJson, "$.matchedAt"));
        assertEquals(matchedAt, reservations.findById(booking.getId()).orElseThrow().getMatchedAt());
        assertEquals(1, eventData(stream, "spot-update").size());
        assertEquals(1, eventData(stream, "arrival-match").size());
        assertEquals(JsonPath.<Object>read(firstJson, "$"),
                JsonPath.<Object>read(lastEvent(stream, "arrival-match"), "$"));
        assertEquals("reserved", JsonPath.read(lastEvent(stream, "spot-update"), "$.status"));
        assertEquals(List.of("SPOT-3"),
                JsonPath.read(lastEvent(stream, "arrival-snapshot"), "$[*].spotNumber"));

        String eventsAfterFirst = stream.getResponse().getContentAsString();
        match("b a12 pa-3456").andExpect(status().isOk()).andExpect(content().json(firstJson));
        Reservation repeated = reservations.findById(booking.getId()).orElseThrow();
        assertEquals(booking.getId(), repeated.getId());
        assertEquals(matchedAt, repeated.getMatchedAt());
        assertTrue(repeated.isActive());
        assertEquals("free", spots.findById("SPOT-3").orElseThrow().getStatus());
        assertEquals(1, reservations.count());
        assertEquals(eventsAfterFirst, stream.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"occupied", "reserved", "duplicate-plate", "invalid-bay"})
    void rejectedRegistrationsDoNotSaveOrPublish(String reason) throws Exception {
        String destination = "SPOT-3";
        switch (reason) {
            case "occupied" -> setPhysicalStatus(3, "occupied");
            case "reserved" -> seedReservation(destination, "OTHER123", true, null);
            case "duplicate-plate" -> seedReservation("SPOT-1", " b a12-pa3456 ", true, null);
            case "invalid-bay" -> destination = "SPOT-6";
            default -> throw new IllegalArgumentException(reason);
        }
        List<Reservation> before = reservations.findAll(Sort.by("id"));
        List<SpotStatus> physicalBefore = spots.findAllByOrderBySpotNumberAsc();
        MvcResult stream = subscribe();
        String eventsBefore = stream.getResponse().getContentAsString();

        register(destination, "Ba12 PA-3456")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(before, reservations.findAll(Sort.by("id")));
        assertEquals(physicalBefore, spots.findAllByOrderBySpotNumberAsc());
        assertEquals(eventsBefore, stream.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"plateNumber\":null}", "{\"plateNumber\":\"\"}",
            "{\"plateNumber\":\"   \"}", "{\"plateNumber\":\" -.! \"}", "{\"plateNumber\":\"\\u00e9---\"}"})
    void invalidPlateRequestsReturnBadRequestWithoutChanges(String payload) throws Exception {
        seedReservation("SPOT-3", "BA12PA3456", true, LocalDateTime.of(2026, 1, 1, 10, 0));
        List<Reservation> before = reservations.findAll(Sort.by("id"));
        List<SpotStatus> physicalBefore = spots.findAllByOrderBySpotNumberAsc();
        MvcResult stream = subscribe();
        String eventsBefore = stream.getResponse().getContentAsString();

        mockMvc.perform(post("/api/arrivals/match").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertEquals(before, reservations.findAll(Sort.by("id")));
        assertEquals(physicalBefore, spots.findAllByOrderBySpotNumberAsc());
        assertEquals(eventsBefore, stream.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @CsvSource({"unregistered,404", "inactive,404", "ambiguous,409", "occupied,409", "invalid-bay,409"})
    void rejectedMatchesDoNotChangeBookingsOrPublish(String reason, int responseStatus) throws Exception {
        LocalDateTime existingMatch = LocalDateTime.of(2026, 1, 1, 10, 0);
        switch (reason) {
            case "unregistered" -> seedReservation("SPOT-1", "OTHER123", true, existingMatch);
            case "inactive" -> seedReservation("SPOT-3", "BA12PA3456", false, existingMatch);
            case "ambiguous" -> {
                seedReservation("SPOT-1", "ba-12 pa3456", true, null);
                seedReservation("SPOT-3", "BA12PA3456", true, null);
            }
            case "occupied" -> {
                seedReservation("SPOT-3", "BA12PA3456", true, existingMatch);
                setPhysicalStatus(3, "occupied");
            }
            case "invalid-bay" -> seedReservation("SPOT-6", "BA12PA3456", true, null);
            default -> throw new IllegalArgumentException(reason);
        }
        List<Reservation> before = reservations.findAll(Sort.by("id"));
        List<SpotStatus> physicalBefore = spots.findAllByOrderBySpotNumberAsc();
        MvcResult stream = subscribe();
        String eventsBefore = stream.getResponse().getContentAsString();

        match(" b a12-pa3456 ").andExpect(status().is(responseStatus));

        assertEquals(before, reservations.findAll(Sort.by("id")));
        assertEquals(physicalBefore, spots.findAllByOrderBySpotNumberAsc());
        assertEquals(eventsBefore, stream.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @CsvSource({"0,{\"status\":\"free\"}", "6,{\"status\":\"occupied\"}",
            "invalid,{\"status\":\"free\"}", "3,{\"status\":\"reserved\"}", "3,{\"status\":\"\"}",
            "3,{\"status\":null}", "3,{}", "3,null"})
    void invalidSensorRequestsDoNotChangeBookingsOrSpots(String number, String payload) throws Exception {
        seedReservation("SPOT-3", "BA12PA3456", true, LocalDateTime.of(2026, 1, 1, 10, 0));
        List<Reservation> before = reservations.findAll(Sort.by("id"));
        List<SpotStatus> physicalBefore = spots.findAllByOrderBySpotNumberAsc();
        MvcResult stream = subscribe();
        String eventsBefore = stream.getResponse().getContentAsString();

        mockMvc.perform(post("/api/spot/update").param("spot", number)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());

        assertEquals(before, reservations.findAll(Sort.by("id")));
        assertEquals(physicalBefore, spots.findAllByOrderBySpotNumberAsc());
        assertEquals(eventsBefore, stream.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @CsvSource({"free,true,reserved", "occupied,false,occupied"})
    void sensorUpdatePreservesMatchTimeAndControlsReservation(String physicalStatus,
                                                             boolean remainsActive,
                                                             String effectiveStatus) throws Exception {
        Reservation booking = seedReservation("SPOT-3", "BA12PA3456", true, null);
        match("BA12PA3456").andExpect(status().isOk());
        LocalDateTime matchedAt = reservations.findById(booking.getId()).orElseThrow().getMatchedAt();
        assertNotNull(matchedAt);
        MvcResult stream = subscribe();
        assertEquals(List.of("SPOT-3"),
                JsonPath.read(lastEvent(stream, "arrival-snapshot"), "$[*].spotNumber"));

        updateSpot(3, physicalStatus).andExpect(status().isOk());

        Reservation updated = reservations.findById(booking.getId()).orElseThrow();
        assertEquals(remainsActive, updated.isActive());
        assertEquals(matchedAt, updated.getMatchedAt());
        assertEquals(physicalStatus, spots.findById("SPOT-3").orElseThrow().getStatus());
        assertEquals(1, eventData(stream, "spot-update").size());
        assertEquals(effectiveStatus, JsonPath.read(lastEvent(stream, "spot-update"), "$.status"));
        assertTrue(eventData(stream, "arrival-match").isEmpty());
        List<String> destinations = remainsActive ? List.of("SPOT-3") : List.of();
        assertEquals(destinations, JsonPath.read(lastEvent(stream, "arrival-snapshot"), "$[*].spotNumber"));
        if (!remainsActive) {
            assertEquals(2, eventData(stream, "arrival-snapshot").size());
            assertFalse(updated.isActive());
        }
        closeStream(stream);
        MvcResult reconnected = subscribe();
        assertEquals(destinations, JsonPath.read(lastEvent(reconnected, "arrival-snapshot"), "$[*].spotNumber"));
    }

    @Test
    void occupiedAndFreeSensorsKeepWaitingBookingsActiveAndBlockable() throws Exception {
        Reservation waiting = seedReservation("SPOT-3", "BA12PA3456", true, null);
        MvcResult stream = subscribe();
        for (String physicalStatus : List.of("occupied", "free")) {
            updateSpot(3, physicalStatus).andExpect(status().isOk());
            Reservation unchanged = reservations.findById(waiting.getId()).orElseThrow();
            assertTrue(unchanged.isActive());
            assertNull(unchanged.getMatchedAt());
            assertEquals(physicalStatus, spots.findById("SPOT-3").orElseThrow().getStatus());
            assertEquals(List.of(), JsonPath.read(lastEvent(stream, "arrival-snapshot"), "$"));
        }
        assertTrue(eventData(stream, "arrival-match").isEmpty());
        register("SPOT-3", "OTHER123").andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(1, reservations.count());
    }

    @Test
    void homeRendersDtosWithoutChangingPhysicalStateOrBookings() throws Exception {
        seedReservation("SPOT-2", "WAITING123", true, null);
        seedReservation("SPOT-3", "MATCHED123", true, LocalDateTime.of(2026, 1, 1, 10, 0));
        setPhysicalStatus(3, "occupied");
        spots.deleteById("SPOT-5");
        List<Reservation> before = reservations.findAll(Sort.by("id"));
        List<SpotStatus> physicalBefore = spots.findAllByOrderBySpotNumberAsc();

        for (int visit = 0; visit < 2; visit++) {
            MvcResult home = mockMvc.perform(get("/")).andExpect(status().isOk())
                    .andExpect(view().name("index")).andReturn();
            assertNotNull(home.getModelAndView());
            List<?> rendered = assertInstanceOf(List.class, home.getModelAndView().getModel().get("spots"));
            List<SpotUpdate> updates = rendered.stream()
                    .map(spot -> assertInstanceOf(SpotUpdate.class, spot)).toList();
            assertEquals(5, updates.size());
            assertEquals("reserved", updates.get(1).status());
            assertEquals("WAITING123", updates.get(1).reservedPlate());
            assertEquals("occupied", updates.get(2).status());
            assertEquals("free", updates.get(4).status());
            assertEquals(before, reservations.findAll(Sort.by("id")));
            assertEquals(physicalBefore, spots.findAllByOrderBySpotNumberAsc());
        }
    }

    @Test
    void sseSnapshotsReplayPersistedArrivalsNewestFirstAndFilterInvalidDestinations() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 10, 0);
        Reservation older = seedReservation("SPOT-1", "OLD1", true, time);
        Reservation newer = seedReservation("SPOT-5", "NEW5", true, time.plusMinutes(1));
        seedReservation("SPOT-2", "WAITING2", true, null);
        seedReservation("SPOT-3", "INACTIVE3", false, time.plusMinutes(2));
        seedReservation("SPOT-4", "OCCUPIED4", true, time.plusMinutes(3));
        seedReservation("SPOT-6", "INVALID6", true, time.plusMinutes(4));
        setPhysicalStatus(4, "occupied");

        for (int connection = 0; connection < 2; connection++) {
            MvcResult stream = subscribe();
            assertEquals(1, eventData(stream, "snapshot").size());
            assertEquals(1, eventData(stream, "arrival-snapshot").size());
            String snapshot = lastEvent(stream, "snapshot");
            assertEquals(List.of("SPOT-1", "SPOT-2", "SPOT-3", "SPOT-4", "SPOT-5"),
                    JsonPath.read(snapshot, "$[*].spotNumber"));
            assertEquals(List.of("reserved", "reserved", "free", "occupied", "reserved"),
                    JsonPath.read(snapshot, "$[*].status"));
            String arrivals = lastEvent(stream, "arrival-snapshot");
            assertEquals(List.of("SPOT-5", "SPOT-1"), JsonPath.read(arrivals, "$[*].spotNumber"));
            assertEquals(List.of("NEW5", "OLD1"), JsonPath.read(arrivals, "$[*].plateNumber"));
            List<Number> ids = JsonPath.read(arrivals, "$[*].reservationId");
            assertEquals(List.of(newer.getId(), older.getId()), ids.stream().map(Number::longValue).toList());
            assertEquals(newer.getMatchedAt(), LocalDateTime.parse(JsonPath.read(arrivals, "$[0].matchedAt")));
            assertEquals(older.getMatchedAt(), LocalDateTime.parse(JsonPath.read(arrivals, "$[1].matchedAt")));
            assertTrue(eventData(stream, "arrival-match").isEmpty());
            closeStream(stream);
        }
    }

    private Reservation seedReservation(String destination, String plate, boolean active, LocalDateTime matchedAt) {
        Reservation reservation = new Reservation();
        reservation.setSpotNumber(destination);
        reservation.setPlateNumber(plate);
        reservation.setActive(active);
        reservation.setMatchedAt(matchedAt);
        return reservations.save(reservation);
    }

    private void setPhysicalStatus(int number, String status) {
        SpotStatus spot = spots.findById("SPOT-" + number).orElseThrow();
        spot.setStatus(status);
        spots.save(spot);
    }

    private ResultActions register(String destination, String plate) throws Exception {
        return mockMvc.perform(post("/register").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("spotNumber", destination).param("plateNumber", plate).param("active", "false"));
    }

    private ResultActions match(String plate) throws Exception {
        return mockMvc.perform(post("/api/arrivals/match").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plateNumber\":\"" + plate + "\"}"));
    }

    private ResultActions updateSpot(int number, String status) throws Exception {
        return mockMvc.perform(post("/api/spot/update").param("spot", String.valueOf(number))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private MvcResult subscribe() throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/spots/events").accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
        streams.add(stream);
        assertEquals(200, stream.getResponse().getStatus());
        assertTrue(stream.getRequest().isAsyncStarted());
        assertTrue(stream.getResponse().getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        return stream;
    }

    private void closeStream(MvcResult stream) {
        if (stream.getRequest().isAsyncStarted()) {
            stream.getRequest().getAsyncContext().complete();
        }
    }

    private List<String> eventData(MvcResult stream, String eventName) throws Exception {
        List<String> events = new ArrayList<>();
        // Read only the current buffer; never async-dispatch or await the infinite SSE response.
        for (String frame : stream.getResponse().getContentAsString().split("\\r?\\n\\r?\\n")) {
            String name = null;
            List<String> data = new ArrayList<>();
            for (String line : frame.split("\\r?\\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.add(line.substring(5).stripLeading());
                }
            }
            if (eventName.equals(name)) {
                events.add(String.join("\n", data));
            }
        }
        return events;
    }

    private String lastEvent(MvcResult stream, String name) throws Exception {
        List<String> events = eventData(stream, name);
        assertFalse(events.isEmpty(), "Missing SSE event: " + name);
        return events.get(events.size() - 1);
    }
}
