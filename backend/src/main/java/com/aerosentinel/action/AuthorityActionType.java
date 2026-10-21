package com.aerosentinel.action;

import java.util.Set;

/**
 * Standard Authority Action Types (Feature 7 / Phase 8 Lifecycle).
 */
public enum AuthorityActionType {
    ACKNOWLEDGEMENT,
    ASSIGNMENT,
    REASSIGNMENT,
    FIELD_INSPECTION_STARTED,
    FIELD_VERIFICATION,
    MOBILE_SENSOR_DEPLOYED,
    SITE_CHECK,
    ADVISORY_ISSUED,
    MITIGATION_ORDERED,
    RESOLUTION,
    DISMISSAL,
    OTHER;

    private static final Set<String> VALID_TYPES = Set.of(
            "ACKNOWLEDGEMENT",
            "ASSIGNMENT",
            "REASSIGNMENT",
            "FIELD_INSPECTION_STARTED",
            "FIELD_VERIFICATION",
            "MOBILE_SENSOR_DEPLOYED",
            "SITE_CHECK",
            "ADVISORY_ISSUED",
            "MITIGATION_ORDERED",
            "RESOLUTION",
            "DISMISSAL",
            "OTHER"
    );

    public static boolean isValid(String type) {
        if (type == null) return false;
        return VALID_TYPES.contains(type.trim().toUpperCase());
    }
}
