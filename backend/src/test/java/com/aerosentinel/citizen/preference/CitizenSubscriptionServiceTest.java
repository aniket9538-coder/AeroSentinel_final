package com.aerosentinel.citizen.preference;

import com.aerosentinel.air.AirObservation;
import com.aerosentinel.air.AirObservationRepository;
import com.aerosentinel.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitizenSubscriptionServiceTest {

    @Mock
    private CitizenAlertSubscriptionRepository subscriptionRepository;

    @Mock
    private CitizenInAppNotificationRepository notificationRepository;

    @Mock
    private CitizenSavedLocationRepository locationRepository;

    @Mock
    private AirObservationRepository airObservationRepository;

    private CitizenSubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new CitizenSubscriptionService(
                subscriptionRepository,
                notificationRepository,
                locationRepository,
                airObservationRepository
        );
    }

    @Test
    @DisplayName("createSubscription accepts valid pollutant threshold and creates subscription")
    void createSubscription_success() {
        when(subscriptionRepository.save(any(CitizenAlertSubscription.class))).thenAnswer(inv -> {
            CitizenAlertSubscription sub = inv.getArgument(0);
            sub.setId(UUID.randomUUID());
            return sub;
        });

        SubscriptionDto.CreateRequest request = new SubscriptionDto.CreateRequest(
                null,
                "Pune",
                "8860885357fffff",
                "PM25",
                60.0,
                "IN_APP"
        );

        SubscriptionDto.Response response = subscriptionService.createSubscription("user-1", request);

        assertThat(response).isNotNull();
        assertThat(response.pollutant()).isEqualTo("PM25");
        assertThat(response.thresholdValue()).isEqualTo(60.0);
        assertThat(response.deliveryChannel()).isEqualTo("IN_APP");
        assertThat(response.channelStatusNote()).contains("in-app delivery supported");
    }

    @Test
    @DisplayName("createSubscription rejects unsupported pollutants or non-positive thresholds")
    void createSubscription_validationFailure() {
        SubscriptionDto.CreateRequest invalidPollutant = new SubscriptionDto.CreateRequest(
                null, "Pune", null, "CO2_INVALID", 60.0, "IN_APP"
        );

        assertThatThrownBy(() -> subscriptionService.createSubscription("user-1", invalidPollutant))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported pollutant");

        SubscriptionDto.CreateRequest zeroThreshold = new SubscriptionDto.CreateRequest(
                null, "Pune", null, "PM25", 0.0, "IN_APP"
        );

        assertThatThrownBy(() -> subscriptionService.createSubscription("user-1", zeroThreshold))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be greater than zero");
    }

    @Test
    @DisplayName("evaluateSubscriptions creates notification with ground-truth value when threshold is met")
    void evaluateSubscriptions_triggersNotification() {
        CitizenAlertSubscription sub = new CitizenAlertSubscription(
                "user-1", null, "Pune", "8860885357fffff", "PM25", 50.0, "IN_APP"
        );
        sub.setId(UUID.randomUUID());

        when(subscriptionRepository.findByEnabledTrue()).thenReturn(List.of(sub));

        AirObservation observation = new AirObservation();
        observation.setPm25(75.5);
        observation.setSource("CPCB");
        observation.setObservedAt(Instant.now());
        when(airObservationRepository.findFirstByOrderByObservedAtDesc()).thenReturn(Optional.of(observation));

        SubscriptionDto.EvaluationResult result = subscriptionService.evaluateSubscriptions(null);

        assertThat(result.subscriptionsEvaluated()).isEqualTo(1);
        assertThat(result.notificationsTriggered()).isEqualTo(1);
        verify(notificationRepository).save(any(CitizenInAppNotification.class));
        verify(subscriptionRepository).save(sub);
        assertThat(sub.getLastTriggeredAt()).isNotNull();
    }

    @Test
    @DisplayName("evaluateSubscriptions enforces 1-hour cooldown deduplication to prevent alert spam")
    void evaluateSubscriptions_enforcesCooldown() {
        CitizenAlertSubscription sub = new CitizenAlertSubscription(
                "user-1", null, "Pune", "8860885357fffff", "PM25", 50.0, "IN_APP"
        );
        sub.setId(UUID.randomUUID());
        sub.setLastTriggeredAt(Instant.now().minus(20, ChronoUnit.MINUTES)); // Triggered 20 mins ago

        when(subscriptionRepository.findByEnabledTrue()).thenReturn(List.of(sub));

        SubscriptionDto.EvaluationResult result = subscriptionService.evaluateSubscriptions(null);

        assertThat(result.subscriptionsEvaluated()).isEqualTo(1);
        assertThat(result.notificationsTriggered()).isEqualTo(0); // Deduplicated by cooldown
        verify(notificationRepository, never()).save(any(CitizenInAppNotification.class));
    }
}
