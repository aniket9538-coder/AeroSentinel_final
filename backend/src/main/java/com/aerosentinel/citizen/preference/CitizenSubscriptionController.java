package com.aerosentinel.citizen.preference;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/citizen")
public class CitizenSubscriptionController {

    private final CitizenSubscriptionService subscriptionService;

    public CitizenSubscriptionController(CitizenSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<SubscriptionDto.Response>> getSubscriptions(
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        return ResponseEntity.ok(subscriptionService.getUserSubscriptions(userId));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionDto.Response> createSubscription(
            @Valid @RequestBody SubscriptionDto.CreateRequest request,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        SubscriptionDto.Response response = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable UUID id,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        subscriptionService.deleteSubscription(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/subscriptions/evaluate")
    public ResponseEntity<SubscriptionDto.EvaluationResult> evaluateSubscriptions(
            @RequestParam(required = false) String cityName
    ) {
        return ResponseEntity.ok(subscriptionService.evaluateSubscriptions(cityName));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<SubscriptionDto.NotificationResponse>> getNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        return ResponseEntity.ok(subscriptionService.getUserNotifications(userId, unreadOnly));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(
            @PathVariable UUID id,
            Principal principal,
            @RequestHeader(name = "X-Citizen-Id", required = false) String citizenIdHeader
    ) {
        String userId = resolveUserId(principal, citizenIdHeader);
        subscriptionService.markNotificationRead(userId, id);
        return ResponseEntity.ok().build();
    }

    private String resolveUserId(Principal principal, String citizenIdHeader) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName().trim();
        }
        if (citizenIdHeader != null && !citizenIdHeader.trim().isBlank()) {
            return "guest-" + citizenIdHeader.trim();
        }
        return "anonymous-citizen";
    }
}
