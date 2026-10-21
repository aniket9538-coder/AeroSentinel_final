package com.aerosentinel.alert;

import java.util.Set;

/**
 * Authoritative Canonical Lifecycle State Machine for Alerts (Feature 7 / Phase 8).
 *
 * Operational lifecycle:
 *   OPEN -> ACKNOWLEDGED -> ASSIGNED -> IN_INSPECTION -> ACTION_TAKEN -> RESOLVED
 *
 * Terminal / alternative states:
 *   OPEN / ACKNOWLEDGED / ASSIGNED -> DISMISSED (with mandatory reason)
 */
public enum AlertStatus {
    OPEN,
    ACKNOWLEDGED,
    ASSIGNED,
    IN_INSPECTION,
    ACTION_TAKEN,
    RESOLVED,
    CLOSED,
    DISMISSED;

    private static final Set<String> VALID_NAMES = Set.of(
            "OPEN", "ACKNOWLEDGED", "ASSIGNED", "IN_INSPECTION", "ACTION_TAKEN", "RESOLVED", "CLOSED", "DISMISSED"
    );

    public static boolean isValid(String status) {
        if (status == null) return false;
        return VALID_NAMES.contains(status.trim().toUpperCase());
    }

    public static void validateTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            throw new IllegalArgumentException("Current status and target status must not be null");
        }

        String cur = currentStatus.trim().toUpperCase();
        String tgt = targetStatus.trim().toUpperCase();

        if (cur.equals(tgt)) {
            return; // Idempotent
        }

        // Terminal states cannot transition to anything
        if ("RESOLVED".equals(cur) || "CLOSED".equals(cur)) {
            throw new IllegalStateException("Cannot transition from terminal state " + cur + " to " + tgt);
        }
        if ("DISMISSED".equals(cur)) {
            throw new IllegalStateException("Cannot transition from terminal state DISMISSED to " + tgt);
        }

        switch (cur) {
            case "OPEN":
                if (!"ACKNOWLEDGED".equals(tgt) && !"DISMISSED".equals(tgt)) {
                    throw new IllegalStateException("Invalid transition from OPEN to " + tgt +
                            ". Valid next states are: ACKNOWLEDGED, DISMISSED");
                }
                break;

            case "ACKNOWLEDGED":
                if (!"ASSIGNED".equals(tgt) && !"DISMISSED".equals(tgt)) {
                    throw new IllegalStateException("Invalid transition from ACKNOWLEDGED to " + tgt +
                            ". Valid next states are: ASSIGNED, DISMISSED");
                }
                break;

            case "ASSIGNED":
                if (!"IN_INSPECTION".equals(tgt) && !"ASSIGNED".equals(tgt) && !"DISMISSED".equals(tgt)) {
                    throw new IllegalStateException("Invalid transition from ASSIGNED to " + tgt +
                            ". Valid next states are: IN_INSPECTION, DISMISSED");
                }
                break;

            case "IN_INSPECTION":
                if (!"ACTION_TAKEN".equals(tgt)) {
                    throw new IllegalStateException("Invalid transition from IN_INSPECTION to " + tgt +
                            ". Valid next state is: ACTION_TAKEN");
                }
                break;

            case "ACTION_TAKEN":
                if (!"RESOLVED".equals(tgt) && !"ACTION_TAKEN".equals(tgt)) {
                    throw new IllegalStateException("Invalid transition from ACTION_TAKEN to " + tgt +
                            ". Valid next state is: RESOLVED");
                }
                break;

            default:
                throw new IllegalStateException("Unknown alert lifecycle state: " + cur);
        }
    }

    public boolean canTransitionTo(AlertStatus next) {
        try {
            validateTransition(this.name(), next.name());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}