package com.aerosentinel.evidence;

/**
 * Normalized evidence classes for Feature 5 (F5) Evidence Fusion & Triage.
 *
 * Strict Rule: A citizen report, its photograph, and the AI analysis of that same photo
 * are NOT independent sources; their hierarchical relationship is preserved to prevent false corroboration.
 */
public enum EvidenceClass {
    /**
     * Crowdsourced observation text, reported incident time, location coordinates, and photo evidence.
     * Classified as auxiliary input; never establishes a confirmed environmental violation alone.
     */
    CITIZEN_SUBMITTED("Citizen-submitted observation, text narrative, and photos"),

    /**
     * Multimodal AI vision evaluation derived from attached citizen photograph.
     * Derivative model output; strictly linked to the originating citizen submission.
     */
    CITIZEN_VISION("Model-generated vision assessment linked to citizen submission"),

    /**
     * In-situ ground monitoring station telemetry (CAAQMS, CPCB, OpenAQ).
     * Authoritative physical sensor measurement.
     */
    ENVIRONMENTAL_MEASUREMENT("Independent ground monitoring station or sensor telemetry"),

    /**
     * Meteorological context (Open-Meteo NWP: wind velocity, temperature, relative humidity, boundary layer).
     * Environmental dispersion context; not direct proof of pollution.
     */
    WEATHER_MODEL_CONTEXT("Numerical weather prediction model dispersion context"),

    /**
     * Statutory government disaster/weather bulletins (IMD, NDMA SACHET CAP RSS 2.0).
     * Authoritative governmental context; strictly distinguished from AeroSentinel triage.
     */
    OFFICIAL_WARNING("Official statutory government disaster/weather warning bulletin"),

    /**
     * Statistical ML models (F3 Platt-calibrated Random Forest, F4 short-term regressor).
     * Predictive indicators with explicit domain boundaries, baseline disclosures, and null-confidence contracts.
     */
    EXPERIMENTAL_ML_OUTPUT("Experimental predictive machine learning model output");

    private final String description;

    EvidenceClass(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
