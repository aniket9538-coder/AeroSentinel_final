package com.aerosentinel.federated.dto;

import java.time.Instant;

public record EvaluationComparisonDto(
        String modelVersion,
        String baseModelVersion,
        String featureSchemaVersion,
        int parameterCount,
        int testSampleCount,
        EvaluationMetricsDto globalMetrics,
        EvaluationMetricsDto localBaselineMetrics,
        EvaluationMetricsDto naiveBaselineMetrics,
        Double generalizationDeltaMae,
        Double generalizationDeltaRmse,
        String evaluationDatasetProvenance,
        String geographicValidityNotice,
        Instant evaluatedAt
) {}
