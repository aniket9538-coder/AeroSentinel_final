code = """package com.aerosentinel.event;

import com.aerosentinel.alert.AlertService;
import com.aerosentinel.citizen.CitizenReport;
import com.aerosentinel.citizen.CitizenReportRepository;
import com.aerosentinel.dto.alert.EvaluateAlertRequest;
import com.aerosentinel.dto.event.CreateEventRequest;
import com.aerosentinel.dto.event.EventDetailResponse;
import com.aerosentinel.dto.event.PollutionEventResponse;
import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.forecast.Forecast;
import com.aerosentinel.forecast.ForecastRepository;
import com.aerosentinel.hotspot.HotspotPrediction;
import com.aerosentinel.hotspot.HotspotPredictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PollutionEventService {

    private static final Logger log = LoggerFactory.getLogger(PollutionEventService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PollutionEventRepository eventRepository;
    private final EventEvidenceRepository evidenceRepository;
    private final HotspotPredictionRepository predictionRepository;
    private final ForecastRepository forecastRepository;
    private final CitizenReportRepository citizenReportRepository;
    private final AlertService alertService;
    private final PollutionEventMapper mapper;

    public PollutionEventService(
            PollutionEventRepository eventRepository,
            EventEvidenceRepository evidenceRepository,
            HotspotPredictionRepository predictionRepository,
            ForecastRepository forecastRepository,
            CitizenReportRepository citizenReportRepository,
            AlertService alertService,
            PollutionEventMapper mapper
    ) {
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
        this.predictionRepository = predictionRepository;
        this.forecastRepository = forecastRepository;
        this.citizenReportRepository = citizenReportRepository;
        this.alertService = alertService;
        this.mapper = mapper;
    }

    @Transactional
    public PollutionEventResponse createOrGetActiveEvent(CreateEventRequest request) {
        return createEvent(request);
    }

    @Transactional
    public PollutionEventResponse createEvent(CreateEventRequest request) {
        if (request.h3CellId() == null || request.h3CellId().isBlank()) {
            throw new ValidationException("H3 cell index is required to register a Potential Pollution Event");
        }

        String h3CellId = request.h3CellId().trim();

        Instant cutoff = Instant.now().minus(Duration.ofHours(6));
        List<PollutionEvent> existingActive = eventRepository.findActiveEventsInH3Since(h3CellId, cutoff);
        if (!existingActive.isEmpty()) {
            PollutionEvent existing = existingActive.get(0);
            log.info("Deduplication matched existing active event {} for cell {}", existing.getEventId(), h3CellId);
            return mapper.toResponse(existing);
        }

        Optional<HotspotPrediction> predOpt = predictionRepository.findTopByH3IndexOrderByPredictedAtDesc(h3CellId);
        double riskScore = predOpt.map(HotspotPrediction::getRiskScore).orElse(50.0);
        double confidence = predOpt.map(HotspotPrediction::getConfidence).orElse(0.75);
        UUID cityId = predOpt.map(HotspotPrediction::getCityId).orElse(null);

        PollutionEvent event = new PollutionEvent();
        event.setEventId(generateEventId());
        event.setCityId(cityId);
        event.setH3Index(h3CellId);
        event.setRiskScore(riskScore);
        event.setConfidence(confidence);
        event.setStartedAt(Instant.now());
        event.setStatus(PollutionEventStatus.OPEN);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        PollutionEvent saved = eventRepository.save(event);
        log.info("POTENTIAL_POLLUTION_EVENT_CREATED eventId={} h3Index={} risk={}", saved.getEventId(), h3CellId, riskScore);

        attachEvidenceRecords(saved, h3CellId, riskScore, confidence);

        try {
            EvaluateAlertRequest alertReq = new EvaluateAlertRequest(
                    saved.getEventId(),
                    saved.getH3Index(),
                    saved.getRiskScore(),
                    saved.getConfidence(),
                    saved.getCityId(),
                    null,
                    "Hotspot elevated risk: " + riskScore
            );
            alertService.evaluateAndCreateAlert(alertReq);
        } catch (Exception e) {
            log.warn("Alert evaluation failed for event {}, event remains valid: {}", saved.getEventId(), e.getMessage());
        }

        return mapper.toResponse(saved);
    }

    private void attachEvidenceRecords(PollutionEvent event, String h3CellId, double riskScore, double confidence) {
        List<EventEvidence> evidenceList = new ArrayList<>();

        EventEvidence hotspotEv = new EventEvidence();
        hotspotEv.setPollutionEvent(event);
        hotspotEv.setEventId(event.getEventId());
        hotspotEv.setEvidenceType(EvidenceType.AIR);
        hotspotEv.setSourceId("HOTSPOT_MODEL");
        hotspotEv.setValueSummary(String.format(Locale.ROOT, "Model-predicted elevated risk score %.1f with %.0f%% confidence", riskScore, confidence * 100));
        hotspotEv.setStrength(confidence);
        hotspotEv.setObservedAt(Instant.now());
        evidenceList.add(hotspotEv);

        try {
            Optional<Forecast> forecastOpt = forecastRepository.findLatestByH3Index(h3CellId);
            if (forecastOpt.isPresent()) {
                Forecast f = forecastOpt.get();
                Double pm25Val = f.getPredictedPm25();
                EventEvidence forecastEv = new EventEvidence();
                forecastEv.setPollutionEvent(event);
                forecastEv.setEventId(event.getEventId());
                forecastEv.setEvidenceType(EvidenceType.FORECAST);
                forecastEv.setSourceId("FORECAST_MODEL");
                forecastEv.setValueSummary(String.format(Locale.ROOT, "Forecasted PM2.5 trajectory indicates elevated concentration risk: %.1f µg/m³",
                        pm25Val != null ? pm25Val : 0.0));
                forecastEv.setStrength(f.getConfidence() != null ? f.getConfidence() : 0.70);
                forecastEv.setObservedAt(f.getTargetTime() != null ? f.getTargetTime() : Instant.now());
                evidenceList.add(forecastEv);
            }
        } catch (Exception ignored) {}

        try {
            List<CitizenReport> citizenReports = citizenReportRepository.findByH3Index(h3CellId);
            for (CitizenReport cr : citizenReports) {
                EventEvidence citEv = new EventEvidence();
                citEv.setPollutionEvent(event);
                citEv.setEventId(event.getEventId());
                citEv.setEvidenceType(EvidenceType.CITIZEN);
                citEv.setSourceId(cr.getReportId() != null ? cr.getReportId() : (cr.getId() != null ? cr.getId().toString() : "CITIZEN_REPORT"));
                citEv.setValueSummary("Citizen observed smoke/emissions: " + (cr.getDescription() != null ? cr.getDescription() : "Unspecified report"));
                citEv.setStrength(0.60);
                citEv.setObservedAt(cr.getCreatedAt() != null ? cr.getCreatedAt() : Instant.now());
                evidenceList.add(citEv);
            }
        } catch (Exception ignored) {}

        evidenceRepository.saveAll(evidenceList);
    }

    @Transactional(readOnly = true)
    public List<PollutionEventResponse> getEvents(UUID cityId, String status, int limit) {
        List<PollutionEvent> list;
        if (cityId != null && status != null && !status.isBlank()) {
            list = eventRepository.findByCityIdAndStatus(cityId, PollutionEventStatus.valueOf(status.toUpperCase()));
        } else if (cityId != null) {
            list = eventRepository.findByCityIdOrderByCreatedAtDesc(cityId);
        } else if (status != null && !status.isBlank()) {
            list = eventRepository.findByStatusOrderByCreatedAtDesc(PollutionEventStatus.valueOf(status.toUpperCase()));
        } else {
            list = eventRepository.findAllByOrderByCreatedAtDesc();
        }

        if (limit > 0 && list.size() > limit) {
            list = list.subList(0, limit);
        }

        return list.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PollutionEventResponse> getEventsByCity(UUID cityId, PollutionEventStatus status) {
        List<PollutionEvent> list = (status != null)
                ? eventRepository.findByCityIdAndStatus(cityId, status)
                : eventRepository.findByCityIdOrderByCreatedAtDesc(cityId);
        return list.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(String eventId) {
        PollutionEvent event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        List<EventEvidence> evidenceList = evidenceRepository.findByEventIdOrderByObservedAtAsc(eventId);

        return mapper.toDetailResponse(event, evidenceList);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetails(String eventId) {
        return getEventDetail(eventId);
    }

    @Transactional
    public PollutionEventResponse resolveEvent(String eventId, String resolutionNotes) {
        PollutionEvent event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getStatus().canTransitionTo(PollutionEventStatus.RESOLVED)) {
            throw new ValidationException("INVALID_EVENT_STATE: Event " + eventId +
                    " in status " + event.getStatus() + " cannot transition to RESOLVED");
        }

        event.setStatus(PollutionEventStatus.RESOLVED);
        event.setEndedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        PollutionEvent saved = eventRepository.save(event);
        log.info("EVENT_RESOLVED eventId={} notes={}", eventId, resolutionNotes);
        return mapper.toResponse(saved);
    }

    @Transactional
    public PollutionEventResponse dismissEvent(String eventId, String reason) {
        PollutionEvent event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getStatus().canTransitionTo(PollutionEventStatus.DISMISSED)) {
            throw new ValidationException("INVALID_EVENT_STATE: Event " + eventId +
                    " in status " + event.getStatus() + " cannot transition to DISMISSED");
        }

        event.setStatus(PollutionEventStatus.DISMISSED);
        event.setEndedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        PollutionEvent saved = eventRepository.save(event);
        log.info("EVENT_DISMISSED eventId={} reason={}", eventId, reason);
        return mapper.toResponse(saved);
    }

    private String generateEventId() {
        int code = 1000 + RANDOM.nextInt(9000);
        String candidate = "EVT-" + code;
        int attempts = 0;
        while (eventRepository.findByEventId(candidate).isPresent() && attempts < 10) {
            code = 1000 + RANDOM.nextInt(9000);
            candidate = "EVT-" + code;
            attempts++;
        }
        if (attempts >= 10) {
            candidate = "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        return candidate;
    }
}
"""

with open("src/main/java/com/aerosentinel/event/PollutionEventService.java", "w", encoding="utf-8") as f:
    f.write(code)

print("SUCCESS: PollutionEventService.java written cleanly!")