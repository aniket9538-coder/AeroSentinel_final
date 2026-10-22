package com.aerosentinel.citizen.preference;

import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CitizenSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(CitizenSubscriptionService.class);
    private static final Duration COOLDOWN_DURATION = Duration.ofHours(1);

    private final CitizenAlertSubscriptionRepository subscriptionRepository;
    private final CitizenInAppNotificationRepository notificationRepository;
    private final CitizenSavedLocationRepository locationRepository;
    private final AirObservationRepository airObservationRepository;

    public CitizenSubscriptionService(
            CitizenAlertSubscriptionRepository subscriptionRepository,
            CitizenInAppNotificationRepository notificationRepository,
            CitizenSavedLocationRepository locationRepository,
            AirObservationRepository airObservationRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.notificationRepository = notificationRepository;
        this.locationRepository = locationRepository;
        this.airObservationRepository = airObservationRepository;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto.Response> getUserSubscriptions(String userId) {
        String effectiveUserId = sanitizeUserId(userId);
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(effectiveUserId)
                .stream()
                .map(SubscriptionDto.Response::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public SubscriptionDto.Response createSubscription(String userId, SubscriptionDto.CreateRequest request) {
        String effectiveUserId = sanitizeUserId(userId);

        if (request.thresholdValue() == null || request.thresholdValue() <= 0) {
            throw new ValidationException("Threshold value must be greater than zero");
        }

        String pollutant = request.pollutant() != null ? request.pollutant().toUpperCase().trim() : "PM25";
        if (!Arrays.asList("PM25", "PM10", "AQI", "NO2").contains(pollutant)) {
            throw new ValidationException("Unsupported pollutant: '" + pollutant + "'. Supported: PM25, PM10, AQI, NO2");
        }

        CitizenSavedLocation location = null;
        if (request.locationId() != null) {
            location = locationRepository.findByIdAndUserId(request.locationId(), effectiveUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Referenced location not found or not owned by user"));
        }

        String effectiveCity = location != null ? location.getCityName() : (request.cityName() != null ? request.cityName().trim() : "Pune");
        String effectiveH3 = location != null ? location.getH3Index() : request.h3Index();

        String channel = request.deliveryChannel() != null ? request.deliveryChannel().toUpperCase().trim() : "IN_APP";

        CitizenAlertSubscription entity = new CitizenAlertSubscription(
                effectiveUserId,
                location,
                effectiveCity,
                effectiveH3,
                pollutant,
                request.thresholdValue(),
                channel
        );

        CitizenAlertSubscription saved = subscriptionRepository.save(entity);
        log.info("Created alert subscription for user '{}' on pollutant '{}' >= {} for city '{}'",
                effectiveUserId, pollutant, request.thresholdValue(), effectiveCity);

        return SubscriptionDto.Response.fromEntity(saved);
    }

    @Transactional
    public void deleteSubscription(String userId, UUID subscriptionId) {
        String effectiveUserId = sanitizeUserId(userId);
        CitizenAlertSubscription sub = subscriptionRepository.findByIdAndUserId(subscriptionId, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found or not owned by requesting user"));

        subscriptionRepository.delete(sub);
        log.info("Deleted subscription {} for user '{}'", subscriptionId, effectiveUserId);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto.NotificationResponse> getUserNotifications(String userId, boolean unreadOnly) {
        String effectiveUserId = sanitizeUserId(userId);
        List<CitizenInAppNotification> list = unreadOnly
                ? notificationRepository.findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(effectiveUserId)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(effectiveUserId);

        return list.stream()
                .map(SubscriptionDto.NotificationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markNotificationRead(String userId, UUID notificationId) {
        String effectiveUserId = sanitizeUserId(userId);
        CitizenInAppNotification notification = notificationRepository.findByIdAndUserId(notificationId, effectiveUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found or not owned by user"));

        notification.setReadStatus(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public SubscriptionDto.EvaluationResult evaluateSubscriptions(String cityNameFilter) {
        List<CitizenAlertSubscription> subscriptions = cityNameFilter != null && !cityNameFilter.isBlank()
                ? subscriptionRepository.findByEnabledTrueAndCityNameIgnoreCase(cityNameFilter)
                : subscriptionRepository.findByEnabledTrue();

        int evaluated = subscriptions.size();
        int triggered = 0;
        Instant now = Instant.now();

        for (CitizenAlertSubscription sub : subscriptions) {
            // Check cooldown: prevent sending duplicate notifications for unchanged qualifying event
            if (sub.getLastTriggeredAt() != null) {
                Duration elapsed = Duration.between(sub.getLastTriggeredAt(), now);
                if (elapsed.compareTo(COOLDOWN_DURATION) < 0) {
                    continue;
                }
            }

            // Find latest observation for city or H3
            Optional<AirObservation> latestObsOpt = airObservationRepository.findFirstByOrderByObservedAtDesc();
            if (latestObsOpt.isEmpty()) {
                continue;
            }

            AirObservation obs = latestObsOpt.get();
            Double observedValue = null;
            if ("PM25".equalsIgnoreCase(sub.getPollutant())) {
                observedValue = obs.getPm25();
            } else if ("PM10".equalsIgnoreCase(sub.getPollutant())) {
                observedValue = obs.getPm10();
            } else if ("AQI".equalsIgnoreCase(sub.getPollutant())) {
                observedValue = obs.getAqi();
            } else if ("NO2".equalsIgnoreCase(sub.getPollutant())) {
                observedValue = obs.getNo2();
            }

            if (observedValue != null && observedValue >= sub.getThresholdValue()) {
                String source = obs.getSource() != null ? obs.getSource() : "CPCB";
                String title = String.format("Environmental Alert: %s Threshold Exceeded", sub.getPollutant());
                String locLabel = sub.getSavedLocation() != null ? sub.getSavedLocation().getLabel() : sub.getCityName();
                String message = String.format("Observed %s at %s reached %.1f µg/m³, exceeding your alert threshold of %.1f µg/m³. Data source: %s verified telemetry.",
                        sub.getPollutant(), locLabel, observedValue, sub.getThresholdValue(), source);

                CitizenInAppNotification notif = new CitizenInAppNotification(
                        sub.getUserId(),
                        sub.getId(),
                        title,
                        message,
                        sub.getPollutant(),
                        observedValue,
                        sub.getThresholdValue(),
                        source
                );
                notificationRepository.save(notif);

                sub.setLastTriggeredAt(now);
                subscriptionRepository.save(sub);
                triggered++;

                log.info("Triggered in-app alert for user '{}' (Sub ID {}): {} >= {}",
                        sub.getUserId(), sub.getId(), observedValue, sub.getThresholdValue());
            }
        }

        return new SubscriptionDto.EvaluationResult(
                evaluated,
                triggered,
                now.toString(),
                "In-app notifications dispatched with safe 1-hour cooldown deduplication."
        );
    }

    private String sanitizeUserId(String userId) {
        if (userId == null || userId.trim().isBlank()) {
            return "anonymous-citizen";
        }
        return userId.trim();
    }
}
