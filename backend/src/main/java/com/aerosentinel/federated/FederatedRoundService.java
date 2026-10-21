package com.aerosentinel.federated;

import com.aerosentinel.exception.ResourceNotFoundException;
import com.aerosentinel.exception.ValidationException;
import com.aerosentinel.federated.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FederatedRoundService {

    private static final Logger log = LoggerFactory.getLogger(FederatedRoundService.class);

    private final FederatedRoundRepository roundRepository;
    private final FederatedNodeUpdateRepository updateRepository;
    private final FederatedGlobalModelRepository globalModelRepository;
    private final ObjectMapper objectMapper;

    public FederatedRoundService(
            FederatedRoundRepository roundRepository,
            FederatedNodeUpdateRepository updateRepository,
            FederatedGlobalModelRepository globalModelRepository,
            ObjectMapper objectMapper
    ) {
        this.roundRepository = roundRepository;
        this.updateRepository = updateRepository;
        this.globalModelRepository = globalModelRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RoundResponse startRound(String customRoundId, String baseVersion, Integer minQuorum, List<String> participatingNodes) {
        String baseVer = baseVersion;
        if (baseVer == null || baseVer.isBlank()) {
            baseVer = globalModelRepository.findByIsActiveTrue()
                    .map(FederatedGlobalModel::getVersion)
                    .orElse("global-v1");
        }

        int quorum = (minQuorum != null && minQuorum > 0) ? minQuorum : 2;
        List<String> nodes = (participatingNodes != null && !participatingNodes.isEmpty())
                ? participatingNodes
                : List.of("PUNE", "MUMBAI", "DELHI");

        String nodesJson;
        try {
            nodesJson = objectMapper.writeValueAsString(nodes);
        } catch (Exception e) {
            nodesJson = "[\"PUNE\",\"MUMBAI\",\"DELHI\"]";
        }

        String roundId;
        if (customRoundId != null && !customRoundId.isBlank()) {
            roundId = customRoundId.trim();
            if (roundRepository.existsByRoundId(roundId)) {
                throw new IllegalStateException("Federated round already exists: " + roundId);
            }
        } else {
            // Auto-generate round ID: ROUND-001, ROUND-002, etc.
            long existingCount = roundRepository.count();
            roundId = String.format("ROUND-%03d", existingCount + 1);
            int attempts = 1;
            while (roundRepository.existsByRoundId(roundId)) {
                attempts++;
                roundId = String.format("ROUND-%03d", existingCount + attempts);
            }
        }

        FederatedRound round = new FederatedRound(roundId, baseVer, quorum, nodesJson);
        FederatedRound saved = roundRepository.save(round);

        log.info("FEDERATED_ROUND_STARTED roundId={} baseModelVersion={} minQuorum={}",
                roundId, baseVer, quorum);

        return toRoundResponse(saved);
    }

    @Transactional
    public RoundResponse startRound(String baseVersion, Integer minQuorum, List<String> participatingNodes) {
        return startRound(null, baseVersion, minQuorum, participatingNodes);
    }

    @Transactional
    public UpdateResponse submitNodeUpdate(String roundId, NodeUpdateDto updateDto) {
        FederatedRound round = roundRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Federated round not found: " + roundId));

        if ("COMPLETED".equalsIgnoreCase(round.getStatus()) || "FAILED".equalsIgnoreCase(round.getStatus())) {
            throw new ValidationException("Round " + roundId + " is already " + round.getStatus() + " and cannot accept updates");
        }

        String cityName = updateDto.getEffectiveCityName();

        // 1. Stale base model validation
        if (updateDto.baseModelVersion() != null && !updateDto.baseModelVersion().trim().equalsIgnoreCase(round.getBaseModelVersion().trim())) {
            throw new ValidationException(String.format(
                    "Stale base model: round %s requires baseModelVersion '%s', but update provided '%s'",
                    roundId, round.getBaseModelVersion(), updateDto.baseModelVersion()
            ));
        }

        // 2. Duplicate update rejection
        if (updateRepository.existsByRoundIdAndCityName(roundId, cityName)) {
            throw new IllegalStateException(String.format(
                    "Duplicate update: city '%s' has already submitted an update for round '%s'",
                    cityName, roundId
            ));
        }

        // 3. Sample count validation
        if (updateDto.sampleCount() <= 0) {
            throw new ValidationException("Sample count must be strictly greater than 0");
        }

        // 4. Parameter weights validation (dimension & poisoning check)
        if (updateDto.weights() != null && !updateDto.weights().isEmpty()) {
            if (updateDto.weights().size() != 37) {
                throw new ValidationException(String.format(
                        "Parameter weights dimension mismatch: expected 37 parameters (36 features + intercept) for f3-features-v1, received %d",
                        updateDto.weights().size()
                ));
            }
            for (Double w : updateDto.weights()) {
                if (w == null || !Double.isFinite(w)) {
                    throw new ValidationException("Parameter weights must be finite numerical values (no NaN or Inf)");
                }
                if (Math.abs(w) > 100000.0) {
                    throw new ValidationException("Parameter update exceeds allowable bounds (possible parameter poisoning): " + w);
                }
            }
        }

        // Advance round lifecycle to UPDATES_COLLECTING
        if ("CREATED".equalsIgnoreCase(round.getStatus()) || "MODEL_DISTRIBUTED".equalsIgnoreCase(round.getStatus()) || "TRAINING".equalsIgnoreCase(round.getStatus())) {
            round.setStatus("UPDATES_COLLECTING");
            roundRepository.save(round);
        }

        String weightsJson = null;
        if (updateDto.weights() != null && !updateDto.weights().isEmpty()) {
            try {
                weightsJson = objectMapper.writeValueAsString(updateDto.weights());
            } catch (Exception ignored) {}
        }

        EvaluationMetricsDto m = updateDto.metrics();
        Double mae = m != null ? m.mae() : null;
        Double rmse = m != null ? m.rmse() : null;
        Double rocAuc = m != null ? m.rocAuc() : null;
        Double brierScore = m != null ? m.brierScore() : null;

        FederatedNodeUpdate update = new FederatedNodeUpdate(
                roundId,
                cityName,
                updateDto.localModelVersion() != null ? updateDto.localModelVersion() : cityName.toLowerCase() + "-" + roundId.toLowerCase(),
                round.getBaseModelVersion(),
                updateDto.sampleCount(),
                weightsJson,
                mae,
                rmse,
                rocAuc,
                brierScore,
                updateDto.artifactReference()
        );

        FederatedNodeUpdate saved = updateRepository.save(update);
        log.info("FEDERATED_UPDATE_RECORDED roundId={} city={} samples={} mae={}",
                roundId, cityName, updateDto.sampleCount(), mae);

        return new UpdateResponse(
                saved.getUpdateId(),
                saved.getRoundId(),
                saved.getCityName(),
                "VALIDATED",
                saved.getSampleCount(),
                saved.getSubmittedAt()
        );
    }

    @Transactional(noRollbackFor = {ValidationException.class})
    public AggregationResponse triggerAggregation(String roundId) {
        FederatedRound round = roundRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Federated round not found: " + roundId));

        if ("COMPLETED".equalsIgnoreCase(round.getStatus())) {
            throw new ValidationException("Round " + roundId + " has already been aggregated and completed");
        }

        List<FederatedNodeUpdate> updates = updateRepository.findByRoundId(roundId);

        // Quorum Check
        if (updates.size() < round.getMinQuorum()) {
            round.setStatus("FAILED");
            round.setFailureReason(String.format(
                    "Insufficient updates for aggregation: received %d, minimum quorum is %d",
                    updates.size(), round.getMinQuorum()
            ));
            round.setCompletedAt(Instant.now());
            roundRepository.save(round);
            throw new ValidationException(round.getFailureReason());
        }

        round.setStatus("AGGREGATING");
        roundRepository.save(round);

        // Mathematical Sample-Weighted FedAvg Aggregation over Metrics & Parameter Weights
        int totalSamples = 0;
        double weightedMaeSum = 0.0;
        double weightedRmseSqSum = 0.0;
        double weightedRocAucSum = 0.0;
        double weightedBrierSum = 0.0;

        List<String> participatingCities = new ArrayList<>();
        double[] aggregatedWeights = new double[37];
        boolean hasWeights = false;

        for (FederatedNodeUpdate u : updates) {
            int n = u.getSampleCount();
            totalSamples += n;
            participatingCities.add(u.getCityName());

            if (u.getMae() != null) weightedMaeSum += u.getMae() * n;
            if (u.getRmse() != null) weightedRmseSqSum += (u.getRmse() * u.getRmse()) * n;
            if (u.getRocAuc() != null) weightedRocAucSum += u.getRocAuc() * n;
            if (u.getBrierScore() != null) weightedBrierSum += u.getBrierScore() * n;

            // Parameter coordinate aggregation
            if (u.getWeightsJson() != null && !u.getWeightsJson().isBlank()) {
                try {
                    List<Double> nodeWeights = objectMapper.readValue(u.getWeightsJson(), new TypeReference<List<Double>>() {});
                    if (nodeWeights != null && !nodeWeights.isEmpty()) {
                        hasWeights = true;
                        for (int i = 0; i < Math.min(37, nodeWeights.size()); i++) {
                            Double val = nodeWeights.get(i);
                            if (val != null && Double.isFinite(val)) {
                                aggregatedWeights[i] += val * n;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse weights for node update {}: {}", u.getCityName(), e.getMessage());
                }
            }
        }

        double aggMae = totalSamples > 0 ? weightedMaeSum / totalSamples : 0.0;
        double aggRmse = totalSamples > 0 ? Math.sqrt(weightedRmseSqSum / totalSamples) : 0.0;
        double aggRocAuc = totalSamples > 0 ? weightedRocAucSum / totalSamples : 0.75;
        double aggBrier = totalSamples > 0 ? weightedBrierSum / totalSamples : 0.20;

        EvaluationMetricsDto aggMetrics = new EvaluationMetricsDto(
                Math.round(aggMae * 10000.0) / 10000.0,
                Math.round(aggRmse * 10000.0) / 10000.0,
                Math.round(aggRocAuc * 10000.0) / 10000.0,
                Math.round(aggBrier * 10000.0) / 10000.0
        );

        // Normalize aggregated weights
        List<Double> finalAggregatedWeights = new ArrayList<>();
        if (hasWeights && totalSamples > 0) {
            for (int i = 0; i < 37; i++) {
                finalAggregatedWeights.add(Math.round((aggregatedWeights[i] / totalSamples) * 10000.0) / 10000.0);
            }
        }

        String aggregatedWeightsJson;
        try {
            aggregatedWeightsJson = objectMapper.writeValueAsString(finalAggregatedWeights);
        } catch (Exception e) {
            aggregatedWeightsJson = "[]";
        }

        // Version increment: global-v1 -> global-v2 -> global-v3
        String baseVer = round.getBaseModelVersion();
        String nextVer;
        try {
            int verNum = Integer.parseInt(baseVer.replaceAll("[^0-9]", ""));
            nextVer = "global-v" + (verNum + 1);
        } catch (Exception e) {
            nextVer = "global-" + roundId.toLowerCase();
        }

        // Deactivate previous active models
        List<FederatedGlobalModel> allModels = globalModelRepository.findAll();
        for (FederatedGlobalModel gm : allModels) {
            if (gm.isActive()) {
                gm.setActive(false);
                globalModelRepository.save(gm);
            }
        }

        String citiesJson;
        try {
            citiesJson = objectMapper.writeValueAsString(participatingCities);
        } catch (Exception e) {
            citiesJson = "[]";
        }

        String artifactPath = "storage/models/global/" + nextVer + ".json";

        // Save serialized global consensus model artifact with SHA-256 integrity hash
        try {
            Path artifactDir = Paths.get("storage", "models", "global");
            Files.createDirectories(artifactDir);
            Path artifactFile = artifactDir.resolve(nextVer + ".json");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(aggregatedWeightsJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            String sha256Hex = sb.toString();

            Map<String, Object> artifactMap = new LinkedHashMap<>();
            artifactMap.put("version", nextVer);
            artifactMap.put("baseModelVersion", baseVer);
            artifactMap.put("roundId", roundId);
            artifactMap.put("algorithm", "FED_AVG_RIDGE");
            artifactMap.put("featureSchemaVersion", "f3-features-v1");
            artifactMap.put("parameterCount", finalAggregatedWeights.size());
            artifactMap.put("totalSamplesAggregated", totalSamples);
            artifactMap.put("participatingNodes", participatingCities);
            artifactMap.put("weights", finalAggregatedWeights);
            artifactMap.put("metrics", aggMetrics);
            artifactMap.put("sha256", sha256Hex);
            artifactMap.put("createdAt", Instant.now().toString());

            Files.writeString(artifactFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifactMap), StandardCharsets.UTF_8);
            log.info("FEDERATED_ARTIFACT_SAVED path={} sha256={}", artifactFile, sha256Hex);
        } catch (Exception e) {
            log.warn("Could not write physical model artifact file: {}", e.getMessage());
        }

        FederatedGlobalModel newGlobal = new FederatedGlobalModel(
                nextVer,
                baseVer,
                roundId,
                true,
                totalSamples,
                citiesJson,
                aggregatedWeightsJson,
                aggMetrics.mae(),
                aggMetrics.rmse(),
                aggMetrics.rocAuc(),
                aggMetrics.brierScore(),
                artifactPath
        );
        globalModelRepository.save(newGlobal);

        // Update round as COMPLETED
        round.setStatus("COMPLETED");
        round.setTargetModelVersion(nextVer);
        round.setTotalSamples(totalSamples);
        round.setMae(aggMetrics.mae());
        round.setRmse(aggMetrics.rmse());
        round.setRocAuc(aggMetrics.rocAuc());
        round.setBrierScore(aggMetrics.brierScore());
        round.setCompletedAt(Instant.now());
        roundRepository.save(round);

        log.info("FEDERATED_AGGREGATION_COMPLETED roundId={} newModel={} nodes={} totalSamples={}",
                roundId, nextVer, participatingCities.size(), totalSamples);

        return new AggregationResponse(
                roundId,
                "COMPLETED",
                baseVer,
                nextVer,
                participatingCities.size(),
                totalSamples,
                aggMetrics,
                artifactPath,
                round.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public RoundDetailResponse getRoundStatus(String roundId) {
        FederatedRound round = roundRepository.findByRoundId(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Federated round not found: " + roundId));

        List<FederatedNodeUpdate> updates = updateRepository.findByRoundId(roundId);

        List<UpdateResponse> updateResponses = updates.stream().map(u -> new UpdateResponse(
                u.getUpdateId(),
                u.getRoundId(),
                u.getCityName(),
                "VALIDATED",
                u.getSampleCount(),
                u.getSubmittedAt()
        )).toList();

        List<String> nodes = parseJsonList(round.getParticipatingNodes());

        EvaluationMetricsDto metrics = null;
        if (round.getMae() != null || round.getRmse() != null) {
            metrics = new EvaluationMetricsDto(
                    round.getMae(),
                    round.getRmse(),
                    round.getRocAuc(),
                    round.getBrierScore()
            );
        }

        return new RoundDetailResponse(
                round.getRoundId(),
                round.getBaseModelVersion(),
                round.getTargetModelVersion(),
                round.getStatus(),
                round.getMinQuorum(),
                updates.size(),
                nodes,
                round.getTotalSamples(),
                metrics,
                round.getFailureReason(),
                round.getCreatedAt(),
                round.getCompletedAt(),
                updateResponses
        );
    }

    @Transactional(readOnly = true)
    public List<RoundResponse> listRounds() {
        return roundRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toRoundResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GlobalModelDto getActiveGlobalModel() {
        FederatedGlobalModel model = globalModelRepository.findByIsActiveTrue()
                .orElseGet(() -> globalModelRepository.findById("global-v1")
                        .orElse(new FederatedGlobalModel(
                                "global-v1", null, null, true, 0,
                                "[\"PUNE\",\"MUMBAI\",\"DELHI\"]", "[]",
                                0.0, 0.0, 0.5, 0.25,
                                "storage/models/global/global-v1.joblib"
                        )));
        return toGlobalModelDto(model);
    }

    @Transactional(readOnly = true)
    public List<GlobalModelDto> getModelCatalog() {
        return globalModelRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toGlobalModelDto)
                .toList();
    }

    private RoundResponse toRoundResponse(FederatedRound round) {
        return new RoundResponse(
                round.getRoundId(),
                round.getBaseModelVersion(),
                round.getStatus(),
                round.getMinQuorum(),
                parseJsonList(round.getParticipatingNodes()),
                round.getCreatedAt()
        );
    }

    private GlobalModelDto toGlobalModelDto(FederatedGlobalModel m) {
        EvaluationMetricsDto metrics = new EvaluationMetricsDto(
                m.getMae(),
                m.getRmse(),
                m.getRocAuc(),
                m.getBrierScore()
        );
        return new GlobalModelDto(
                m.getVersion(),
                m.getBaseModelVersion(),
                m.getRoundId(),
                m.isActive(),
                m.getTotalSamples(),
                parseJsonList(m.getParticipatingNodes()),
                metrics,
                m.getArtifactPath(),
                m.getCreatedAt()
        );
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Arrays.stream(json.replace("[", "").replace("]", "").replace("\"", "").split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
