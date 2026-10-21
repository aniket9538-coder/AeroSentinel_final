package com.aerosentinel.federated;

import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.federated.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FederatedRoundServiceTest {

    @Mock
    private FederatedRoundRepository roundRepository;

    @Mock
    private FederatedNodeUpdateRepository updateRepository;

    @Mock
    private FederatedGlobalModelRepository globalModelRepository;

    private ObjectMapper objectMapper;
    private FederatedRoundService roundService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        roundService = new FederatedRoundService(
                roundRepository,
                updateRepository,
                globalModelRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("Round Creation: Generates round with CREATED status and active base model")
    void testStartRound_Success() {
        when(globalModelRepository.findByIsActiveTrue()).thenReturn(Optional.of(
                new FederatedGlobalModel("global-v1", null, null, true, 0, "[]", "[]", 0.0, 0.0, 0.5, 0.25, "path")
        ));
        when(roundRepository.count()).thenReturn(0L);
        when(roundRepository.existsByRoundId("ROUND-001")).thenReturn(false);
        when(roundRepository.save(any(FederatedRound.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoundResponse res = roundService.startRound(null, 2, List.of("PUNE", "MUMBAI", "DELHI"));

        assertThat(res).isNotNull();
        assertThat(res.roundId()).isEqualTo("ROUND-001");
        assertThat(res.baseModelVersion()).isEqualTo("global-v1");
        assertThat(res.status()).isEqualTo("CREATED");
        assertThat(res.minQuorum()).isEqualTo(2);
        assertThat(res.participatingNodes()).containsExactly("PUNE", "MUMBAI", "DELHI");

        verify(roundRepository).save(any(FederatedRound.class));
    }

    @Test
    @DisplayName("Node Update Submission: Ingests valid update and advances state to UPDATES_COLLECTING")
    void testSubmitNodeUpdate_Success() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[\"PUNE\",\"MUMBAI\",\"DELHI\"]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));
        when(updateRepository.existsByRoundIdAndCityName("ROUND-001", "PUNE")).thenReturn(false);
        when(updateRepository.save(any(FederatedNodeUpdate.class))).thenAnswer(invocation -> {
            FederatedNodeUpdate u = invocation.getArgument(0);
            u.setUpdateId(UUID.randomUUID());
            return u;
        });

        NodeUpdateDto updateDto = new NodeUpdateDto(
                "PUNE",
                "PUNE",
                "pune-round-001",
                "global-v1",
                1200,
                Collections.emptyList(),
                new EvaluationMetricsDto(0.4312, 0.5123, 0.7241, 0.1822),
                "storage/models/updates/pune_round-001.json"
        );

        UpdateResponse res = roundService.submitNodeUpdate("ROUND-001", updateDto);

        assertThat(res).isNotNull();
        assertThat(res.cityName()).isEqualTo("PUNE");
        assertThat(res.status()).isEqualTo("VALIDATED");
        assertThat(res.sampleCount()).isEqualTo(1200);
        assertThat(round.getStatus()).isEqualTo("UPDATES_COLLECTING");

        verify(roundRepository).save(round);
        verify(updateRepository).save(any(FederatedNodeUpdate.class));
    }

    @Test
    @DisplayName("Stale Base Model Rejection: Throws ValidationException if baseModelVersion does not match round")
    void testSubmitNodeUpdate_RejectsStaleBaseModel() {
        FederatedRound round = new FederatedRound("ROUND-002", "global-v2", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-002")).thenReturn(Optional.of(round));

        NodeUpdateDto staleUpdate = new NodeUpdateDto(
                "DELHI",
                "DELHI",
                "delhi-r1",
                "global-v1", // Stale base model
                1000,
                Collections.emptyList(),
                new EvaluationMetricsDto(0.12, 0.20, 0.80, 0.15),
                "ref"
        );

        assertThatThrownBy(() -> roundService.submitNodeUpdate("ROUND-002", staleUpdate))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Stale base model");

        verify(updateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate Update Rejection: Second update from same city in same round throws IllegalStateException")
    void testSubmitNodeUpdate_RejectsDuplicateUpdate() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));
        when(updateRepository.existsByRoundIdAndCityName("ROUND-001", "MUMBAI")).thenReturn(true);

        NodeUpdateDto duplicateUpdate = new NodeUpdateDto(
                "MUMBAI",
                "MUMBAI",
                "mumbai-r1",
                "global-v1",
                800,
                Collections.emptyList(),
                new EvaluationMetricsDto(0.40, 0.50, 0.70, 0.19),
                "ref"
        );

        assertThatThrownBy(() -> roundService.submitNodeUpdate("ROUND-001", duplicateUpdate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate update");

        verify(updateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quorum Enforcement: Aggregation is blocked and round marked FAILED when updates < minQuorum")
    void testTriggerAggregation_QuorumEnforcement() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));

        FederatedNodeUpdate puneUpdate = new FederatedNodeUpdate(
                "ROUND-001", "PUNE", "pune-v1", "global-v1", 1200, "[]", 0.40, 0.50, 0.70, 0.18, "ref"
        );
        when(updateRepository.findByRoundId("ROUND-001")).thenReturn(List.of(puneUpdate)); // Only 1 update, minQuorum is 2

        assertThatThrownBy(() -> roundService.triggerAggregation("ROUND-001"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Insufficient updates for aggregation");

        assertThat(round.getStatus()).isEqualTo("FAILED");
        assertThat(round.getFailureReason()).contains("received 1, minimum quorum is 2");
        verify(roundRepository, atLeastOnce()).save(round);
        verify(globalModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("Aggregation Execution: 3-Node FedAvg calculates sample-weighted metrics and registers global-v2")
    void testTriggerAggregation_Success() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));

        // Pune: 1200 samples, MAE 0.4312
        FederatedNodeUpdate pune = new FederatedNodeUpdate(
                "ROUND-001", "PUNE", "pune-v1", "global-v1", 1200, "[]", 0.4312, 0.5123, 0.7238, 0.1822, "ref"
        );
        // Mumbai: 800 samples, MAE 0.4188
        FederatedNodeUpdate mumbai = new FederatedNodeUpdate(
                "ROUND-001", "MUMBAI", "mumbai-v1", "global-v1", 800, "[]", 0.4188, 0.4850, 0.7150, 0.1910, "ref"
        );
        // Delhi: 1000 samples, MAE 0.0944
        FederatedNodeUpdate delhi = new FederatedNodeUpdate(
                "ROUND-001", "DELHI", "delhi-v1", "global-v1", 1000, "[]", 0.0944, 0.2215, 0.7310, 0.1615, "ref"
        );

        when(updateRepository.findByRoundId("ROUND-001")).thenReturn(List.of(pune, mumbai, delhi));
        when(globalModelRepository.findAll()).thenReturn(Collections.emptyList());

        AggregationResponse res = roundService.triggerAggregation("ROUND-001");

        assertThat(res).isNotNull();
        assertThat(res.roundId()).isEqualTo("ROUND-001");
        assertThat(res.status()).isEqualTo("COMPLETED");
        assertThat(res.baseModelVersion()).isEqualTo("global-v1");
        assertThat(res.globalModelVersion()).isEqualTo("global-v2");
        assertThat(res.participatingNodesCount()).isEqualTo(3);
        assertThat(res.totalSamples()).isEqualTo(3000);
        assertThat(res.artifactPath()).isEqualTo("storage/models/global/global-v2.json");

        // Mathematical verification:
        // Expected MAE = (1200*0.4312 + 800*0.4188 + 1000*0.0944) / 3000 = (517.44 + 335.04 + 94.4) / 3000 = 946.88 / 3000 = 0.3156
        assertThat(res.aggregatedMetrics().mae()).isEqualTo(0.3156);

        ArgumentCaptor<FederatedGlobalModel> modelCaptor = ArgumentCaptor.forClass(FederatedGlobalModel.class);
        verify(globalModelRepository).save(modelCaptor.capture());
        FederatedGlobalModel savedModel = modelCaptor.getValue();
        assertThat(savedModel.getVersion()).isEqualTo("global-v2");
        assertThat(savedModel.isActive()).isTrue();
        assertThat(savedModel.getTotalSamples()).isEqualTo(3000);
    }

    @Test
    @DisplayName("Partial Quorum (2 of 3): Aggregates successfully with Pune + Mumbai when quorum threshold is met")
    void testTriggerAggregation_TwoOfThreeQuorum() {
        FederatedRound round = new FederatedRound("ROUND-002", "global-v2", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-002")).thenReturn(Optional.of(round));

        FederatedNodeUpdate pune = new FederatedNodeUpdate(
                "ROUND-002", "PUNE", "pune-v2", "global-v2", 1500, "[]", 0.4034, 0.4500, 0.7500, 0.1700, "ref"
        );
        FederatedNodeUpdate mumbai = new FederatedNodeUpdate(
                "ROUND-002", "MUMBAI", "mumbai-v2", "global-v2", 900, "[]", 0.4248, 0.4610, 0.7567, 0.1750, "ref"
        );

        when(updateRepository.findByRoundId("ROUND-002")).thenReturn(List.of(pune, mumbai));
        when(globalModelRepository.findAll()).thenReturn(Collections.emptyList());

        AggregationResponse res = roundService.triggerAggregation("ROUND-002");

        assertThat(res).isNotNull();
        assertThat(res.globalModelVersion()).isEqualTo("global-v3");
        assertThat(res.participatingNodesCount()).isEqualTo(2);
        assertThat(res.totalSamples()).isEqualTo(2400);
        assertThat(res.status()).isEqualTo("COMPLETED");

        verify(globalModelRepository).save(any(FederatedGlobalModel.class));
    }

    @Test
    @DisplayName("Active Global Model Resolution: Returns active model from repository")
    void testGetActiveGlobalModel() {
        FederatedGlobalModel activeModel = new FederatedGlobalModel(
                "global-v2", "global-v1", "ROUND-001", true, 3000,
                "[\"PUNE\",\"MUMBAI\",\"DELHI\"]", "[]", 0.3156, 0.4215, 0.7238, 0.1782,
                "storage/models/global/global-v2.joblib"
        );
        when(globalModelRepository.findByIsActiveTrue()).thenReturn(Optional.of(activeModel));

        GlobalModelDto dto = roundService.getActiveGlobalModel();

        assertThat(dto).isNotNull();
        assertThat(dto.version()).isEqualTo("global-v2");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.totalSamples()).isEqualTo(3000);
        assertThat(dto.metrics().mae()).isEqualTo(0.3156);
    }

    @Test
    @DisplayName("Weights Validation: Rejects update when weights dimension != 37")
    void testSubmitNodeUpdate_InvalidWeightsDimension() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[\"PUNE\"]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));

        // 10 weights instead of 37
        List<Double> badWeights = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);
        NodeUpdateDto updateDto = new NodeUpdateDto(
                "PUNE", "PUNE", "pune-v1", "global-v1", 100, badWeights,
                new EvaluationMetricsDto(1.0, 2.0, 0.7, 0.2), "ref"
        );

        assertThatThrownBy(() -> roundService.submitNodeUpdate("ROUND-001", updateDto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Parameter weights dimension mismatch: expected 37 parameters");
    }

    @Test
    @DisplayName("Weights Validation: Rejects parameter poisoning with extreme values")
    void testSubmitNodeUpdate_PoisonedWeights() {
        FederatedRound round = new FederatedRound("ROUND-001", "global-v1", 2, "[\"PUNE\"]");
        when(roundRepository.findByRoundId("ROUND-001")).thenReturn(Optional.of(round));

        List<Double> poisonedWeights = new ArrayList<>(Collections.nCopies(36, 1.0));
        poisonedWeights.add(9999999.0); // Extreme poisoning value
        NodeUpdateDto updateDto = new NodeUpdateDto(
                "PUNE", "PUNE", "pune-v1", "global-v1", 100, poisonedWeights,
                new EvaluationMetricsDto(1.0, 2.0, 0.7, 0.2), "ref"
        );

        assertThatThrownBy(() -> roundService.submitNodeUpdate("ROUND-001", updateDto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("possible parameter poisoning");
    }

    @Test
    @DisplayName("Mathematical FedAvg: Computes exact coordinate-wise sample-weighted weights vector")
    void testTriggerAggregation_WithWeights_ExactFedAvg() throws Exception {
        FederatedRound round = new FederatedRound("ROUND-003", "global-v1", 2, "[]");
        when(roundRepository.findByRoundId("ROUND-003")).thenReturn(Optional.of(round));

        // Node 1: 100 samples, all weights = 2.0
        List<Double> w1 = Collections.nCopies(37, 2.0);
        String w1Json = objectMapper.writeValueAsString(w1);
        FederatedNodeUpdate u1 = new FederatedNodeUpdate(
                "ROUND-003", "PUNE", "pune-v1", "global-v1", 100, w1Json, 1.0, 2.0, 0.8, 0.1, "ref"
        );

        // Node 2: 300 samples, all weights = 6.0
        List<Double> w2 = Collections.nCopies(37, 6.0);
        String w2Json = objectMapper.writeValueAsString(w2);
        FederatedNodeUpdate u2 = new FederatedNodeUpdate(
                "ROUND-003", "MUMBAI", "mumbai-v1", "global-v1", 300, w2Json, 2.0, 3.0, 0.7, 0.2, "ref"
        );

        // Expected coordinate-wise FedAvg: (100 * 2.0 + 300 * 6.0) / 400 = (200 + 1800) / 400 = 5.0
        when(updateRepository.findByRoundId("ROUND-003")).thenReturn(List.of(u1, u2));
        when(globalModelRepository.findAll()).thenReturn(Collections.emptyList());

        AggregationResponse res = roundService.triggerAggregation("ROUND-003");

        assertThat(res).isNotNull();
        assertThat(res.totalSamples()).isEqualTo(400);

        ArgumentCaptor<FederatedGlobalModel> captor = ArgumentCaptor.forClass(FederatedGlobalModel.class);
        verify(globalModelRepository).save(captor.capture());
        FederatedGlobalModel savedGlobal = captor.getValue();

        List<Double> aggregatedWeights = objectMapper.readValue(savedGlobal.getWeightsJson(), new TypeReference<List<Double>>() {});
        assertThat(aggregatedWeights).hasSize(37);
        for (Double w : aggregatedWeights) {
            assertThat(w).isEqualTo(5.0);
        }
    }
}
