package com.aerosentinel.federated;

import com.aerosentinel.dto.federated.CreateRoundRequest;
import com.aerosentinel.dto.federated.FederatedRoundResponse;
import com.aerosentinel.federated.dto.AggregationResponse;
import com.aerosentinel.federated.dto.RoundDetailResponse;
import com.aerosentinel.federated.dto.RoundResponse;
import com.aerosentinel.federated.dto.StartRoundRequest;
import com.aerosentinel.federated.dto.UpdateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/federated/rounds")
@CrossOrigin(origins = "*")
public class FederatedRoundController {

    private final FederatedRoundService roundService;
    private final FederatedTrainingService trainingService;

    public FederatedRoundController(FederatedRoundService roundService, FederatedTrainingService trainingService) {
        this.roundService = roundService;
        this.trainingService = trainingService;
    }

    /**
     * POST /api/v1/federated/rounds
     * Initiates a new federated training round with base model version, quorum, and participant nodes.
     */
    @PostMapping
    public ResponseEntity<RoundResponse> createRound(@RequestBody(required = false) CreateRoundRequest request) {
        String roundId = request != null ? request.roundId() : null;
        String baseVersion = request != null ? request.baseModelVersion() : null;
        Integer minQuorum = request != null ? request.minQuorum() : null;
        List<String> participatingNodes = request != null ? request.participatingNodes() : null;

        RoundResponse response = roundService.startRound(roundId, baseVersion, minQuorum, participatingNodes);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/federated/rounds/start
     * Backward-compatible endpoint to start a round.
     */
    @PostMapping("/start")
    public ResponseEntity<RoundResponse> startRound(@RequestBody(required = false) StartRoundRequest request) {
        String baseVersion = request != null ? request.baseVersion() : null;
        Integer minQuorum = request != null ? request.minQuorum() : null;
        List<String> participatingNodes = request != null ? request.participatingNodes() : null;

        RoundResponse response = roundService.startRound(baseVersion, minQuorum, participatingNodes);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/federated/rounds/{roundId}/simulate-round
     * Simulates genuine local training across all enrolled nodes and submits updates to the round.
     */
    @PostMapping("/{roundId}/simulate-round")
    public ResponseEntity<List<UpdateResponse>> simulateRound(@PathVariable String roundId) {
        List<UpdateResponse> updates = trainingService.simulateRoundTraining(roundId);
        return ResponseEntity.ok(updates);
    }

    /**
     * POST /api/v1/federated/rounds/{roundId}/train-node/{nodeId}
     * Executes local training for a specific enrolled node and submits its update.
     */
    @PostMapping("/{roundId}/train-node/{nodeId}")
    public ResponseEntity<UpdateResponse> trainNode(
            @PathVariable String roundId,
            @PathVariable String nodeId
    ) {
        var nodeUpdate = trainingService.trainLocalNode(roundId, null, nodeId);
        UpdateResponse response = roundService.submitNodeUpdate(roundId, nodeUpdate);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/federated/rounds
     * Lists all federated training rounds.
     */
    @GetMapping
    public ResponseEntity<List<RoundResponse>> listRounds() {
        return ResponseEntity.ok(roundService.listRounds());
    }

    /**
     * GET /api/v1/federated/rounds/{roundId}
     * Returns detailed round lifecycle status, participating nodes, updates count, and metrics.
     */
    @GetMapping("/{roundId}")
    public ResponseEntity<RoundDetailResponse> getRound(@PathVariable String roundId) {
        return ResponseEntity.ok(roundService.getRoundStatus(roundId));
    }

    /**
     * POST /api/v1/federated/rounds/{roundId}/aggregate
     * Dispatches sample-weighted FedAvg consensus aggregation across collected node updates.
     */
    @PostMapping("/{roundId}/aggregate")
    public ResponseEntity<AggregationResponse> triggerAggregation(@PathVariable String roundId) {
        AggregationResponse response = roundService.triggerAggregation(roundId);
        return ResponseEntity.ok(response);
    }
}
