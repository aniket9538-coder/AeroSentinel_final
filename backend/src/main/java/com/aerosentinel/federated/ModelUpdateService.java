package com.aerosentinel.federated;

import com.aerosentinel.dto.federated.ModelUpdateResponse;
import com.aerosentinel.dto.federated.SubmitModelUpdateRequest;
import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ModelUpdateService {

    private static final Logger log = LoggerFactory.getLogger(ModelUpdateService.class);

    private final ModelUpdateRepository updateRepository;
    private final FederatedRoundRepository roundRepository;
    private final ObjectMapper objectMapper;

    public ModelUpdateService(
            ModelUpdateRepository updateRepository,
            FederatedRoundRepository roundRepository,
            ObjectMapper objectMapper
    ) {
        this.updateRepository = updateRepository;
        this.roundRepository = roundRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ModelUpdateResponse submitUpdate(String roundId, SubmitModelUpdateRequest request) {
        if (request == null || request.nodeId() == null || request.nodeId().trim().isEmpty()) {
            throw new ValidationException("nodeId is required in update request");
        }

        FederatedRound round = roundRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Federated round not found: " + roundId));

        if ("COMPLETED".equalsIgnoreCase(round.getStatus()) || "FAILED".equalsIgnoreCase(round.getStatus())) {
            throw new ValidationException("Round " + roundId + " is already " + round.getStatus() + " and cannot accept updates");
        }

        String nodeId = request.nodeId().trim().toUpperCase();

        // 1. Node Enrollment Check
        List<String> participatingNodes = parseNodes(round.getParticipatingNodes());
        if (!participatingNodes.isEmpty() && !participatingNodes.contains(nodeId)) {
            throw new ValidationException(String.format(
                    "Node '%s' is not an enrolled participant in round '%s' (participants: %s)",
                    nodeId, roundId, participatingNodes
            ));
        }

        // 2. Base model version check
        if (request.baseModelVersion() != null && !request.baseModelVersion().trim().equalsIgnoreCase(round.getBaseModelVersion().trim())) {
            throw new ValidationException(String.format(
                    "Stale base model: round %s requires baseModelVersion '%s', but update provided '%s'",
                    roundId, round.getBaseModelVersion(), request.baseModelVersion()
            ));
        }

        // 3. Duplicate update rejection (HTTP 409 Conflict via IllegalStateException)
        if (updateRepository.existsByRoundIdAndNodeId(roundId, nodeId)) {
            throw new IllegalStateException(String.format(
                    "Duplicate update: node '%s' has already submitted an update for round '%s'",
                    nodeId, roundId
            ));
        }

        // 4. Sample count validation
        if (request.sampleCount() <= 0) {
            throw new ValidationException("sampleCount must be strictly greater than 0");
        }

        // 5. Parameter weights validation (dimension & poisoning check)
        if (request.weights() != null && !request.weights().isEmpty()) {
            if (request.weights().size() != 37) {
                throw new ValidationException(String.format(
                        "Parameter weights dimension mismatch: expected 37 parameters (36 features + intercept) for f3-features-v1, received %d",
                        request.weights().size()
                ));
            }
            for (Double w : request.weights()) {
                if (w == null || !Double.isFinite(w)) {
                    throw new ValidationException("Parameter weights must be finite numerical values (no NaN or Inf)");
                }
                if (Math.abs(w) > 100000.0) {
                    throw new ValidationException("Parameter update exceeds allowable bounds (possible parameter poisoning): " + w);
                }
            }
        }

        String weightsJson = null;
        if (request.weights() != null && !request.weights().isEmpty()) {
            try {
                weightsJson = objectMapper.writeValueAsString(request.weights());
            } catch (Exception e) {
                log.warn("Failed to serialize weights to JSON: {}", e.getMessage());
            }
        }

        Double mae = null;
        Double rmse = null;
        Double rocAuc = null;
        Double brierScore = null;
        if (request.metrics() != null) {
            mae = request.metrics().get("mae");
            rmse = request.metrics().get("rmse");
            rocAuc = request.metrics().get("rocAuc");
            brierScore = request.metrics().get("brierScore");
        }

        String localVersion = request.localModelVersion() != null ? request.localModelVersion() : nodeId.toLowerCase() + "-v1";

        ModelUpdate update = new ModelUpdate(
                roundId,
                nodeId,
                round.getBaseModelVersion(),
                localVersion,
                request.sampleCount(),
                mae,
                rmse,
                rocAuc,
                brierScore,
                weightsJson,
                request.artifactReference(),
                UpdateStatus.VALIDATED
        );

        ModelUpdate saved = updateRepository.save(update);

        // Advance round status to UPDATES_COLLECTING if it is still CREATED or TRAINING
        if ("CREATED".equalsIgnoreCase(round.getStatus()) || "TRAINING".equalsIgnoreCase(round.getStatus()) || "MODEL_DISTRIBUTED".equalsIgnoreCase(round.getStatus())) {
            round.setStatus("UPDATES_COLLECTING");
            roundRepository.save(round);
        }

        log.info("FEDERATED_UPDATE_VALIDATED roundId={} node={} samples={} mae={}",
                roundId, nodeId, request.sampleCount(), mae);

        return new ModelUpdateResponse(
                saved.getRoundId(),
                saved.getNodeId(),
                saved.getStatus().name(),
                saved.getSampleCount(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ModelUpdateResponse> getUpdatesForRound(String roundId) {
        return updateRepository.findByRoundId(roundId).stream()
                .map(u -> new ModelUpdateResponse(
                        u.getRoundId(),
                        u.getNodeId(),
                        u.getStatus().name(),
                        u.getSampleCount(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private List<String> parseNodes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
