package com.aerosentinel.federated;

import com.aerosentinel.federated.dto.EvaluationComparisonDto;
import com.aerosentinel.federated.dto.NodeUpdateDto;
import com.aerosentinel.federated.dto.UpdateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FederatedTrainingServiceTest {

    @Mock
    private FederatedRoundRepository roundRepository;

    @Mock
    private FederatedGlobalModelRepository globalModelRepository;

    @Mock
    private FederatedRoundService roundService;

    private ObjectMapper objectMapper;
    private FederatedTrainingService trainingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        trainingService = new FederatedTrainingService(
                roundRepository,
                globalModelRepository,
                roundService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Local Training: Trains Pune node with 1200 samples and extracts 37 parameters")
    void testTrainLocalNode_Pune() {
        NodeUpdateDto update = trainingService.trainLocalNode("ROUND-001", "global-v1", "PUNE");

        assertThat(update).isNotNull();
        assertThat(update.cityName()).isEqualTo("PUNE");
        assertThat(update.baseModelVersion()).isEqualTo("global-v1");
        assertThat(update.sampleCount()).isEqualTo(1200);
        assertThat(update.weights()).hasSize(37); // 36 features + intercept
        for (Double w : update.weights()) {
            assertThat(w).isNotNull();
            assertThat(Double.isFinite(w)).isTrue();
        }

        assertThat(update.metrics()).isNotNull();
        assertThat(update.metrics().mae()).isGreaterThan(0.0).isLessThan(20.0);
        assertThat(update.metrics().rmse()).isGreaterThan(0.0).isLessThan(30.0);
        assertThat(update.metrics().rocAuc()).isBetween(0.5, 1.0);
        assertThat(update.metrics().brierScore()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Local Training: Mumbai and Delhi produce distinct valid local models")
    void testTrainLocalNode_MumbaiAndDelhi() {
        NodeUpdateDto mumbai = trainingService.trainLocalNode("ROUND-001", "global-v1", "MUMBAI");
        NodeUpdateDto delhi = trainingService.trainLocalNode("ROUND-001", "global-v1", "DELHI");

        assertThat(mumbai.sampleCount()).isEqualTo(800);
        assertThat(mumbai.weights()).hasSize(37);

        assertThat(delhi.sampleCount()).isEqualTo(1000);
        assertThat(delhi.weights()).hasSize(37);

        // Due to different municipal profiles, weights should not be identical
        assertThat(mumbai.weights()).isNotEqualTo(delhi.weights());
    }

    @Test
    @DisplayName("Ridge Regression Solver: Produces non-null finite parameter weights")
    void testFitRidgeRegression_Solvability() {
        double[][] X = new double[100][36];
        double[] y = new double[100];
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 36; j++) {
                X[i][j] = (i + j) * 0.1;
            }
            y[i] = 10.0 + i * 0.5;
        }

        double[] weights = FederatedTrainingService.fitRidgeRegression(X, y, 1.0);

        assertThat(weights).hasSize(37);
        for (double w : weights) {
            assertThat(Double.isFinite(w)).isTrue();
        }
    }

    @Test
    @DisplayName("Held-Out Evaluation: Compares global model against Pune local baseline and naive mean")
    void testEvaluateGlobalModel_Comparison() {
        FederatedGlobalModel model = new FederatedGlobalModel(
                "global-v1", null, null, true, 3000, "[\"PUNE\",\"MUMBAI\",\"DELHI\"]",
                "[]", 3.5, 4.8, 0.88, 0.12, "storage/models/global/global-v1.json"
        );
        when(globalModelRepository.findByVersion("global-v1")).thenReturn(Optional.of(model));

        EvaluationComparisonDto comparison = trainingService.evaluateGlobalModel("global-v1");

        assertThat(comparison).isNotNull();
        assertThat(comparison.modelVersion()).isEqualTo("global-v1");
        assertThat(comparison.testSampleCount()).isEqualTo(500);
        assertThat(comparison.parameterCount()).isEqualTo(37);
        assertThat(comparison.globalMetrics()).isNotNull();
        assertThat(comparison.localBaselineMetrics()).isNotNull();
        assertThat(comparison.naiveBaselineMetrics()).isNotNull();
        assertThat(comparison.geographicValidityNotice()).contains("Pune-domain-limited");
    }

    @Test
    @DisplayName("Simulate Round Training: Trains all participating nodes and returns update responses")
    void testSimulateRoundTraining_Success() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[\"PUNE\",\"MUMBAI\",\"DELHI\"]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));

        when(roundService.submitNodeUpdate(eq("ROUND-001"), any(NodeUpdateDto.class)))
                .thenAnswer(inv -> {
                    NodeUpdateDto dto = inv.getArgument(1);
                    return new UpdateResponse(UUID.randomUUID(), "ROUND-001", dto.cityName(), "VALIDATED", dto.sampleCount(), Instant.now());
                });

        List<UpdateResponse> updates = trainingService.simulateRoundTraining("ROUND-001");

        assertThat(updates).hasSize(3);
        assertThat(updates).extracting(UpdateResponse::cityName).containsExactlyInAnyOrder("PUNE", "MUMBAI", "DELHI");
    }
}
