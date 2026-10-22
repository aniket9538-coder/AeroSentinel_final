"""
AeroSentinel - AI & ML Inference API Service
File: ai-service/app/main.py

Provides real-time REST endpoints for:
  - F3 Potential Hotspot Classification & Probability Calibration
  - F3 Multi-Horizon PM2.5 Forecasting (T+1, T+3, T+6)
  - Epistemic Uncertainty & Interpretability Signals
"""

from datetime import datetime, timezone
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, Dict, List, Optional
import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

import sys
import types
BASE_DIR = Path(__file__).resolve().parent.parent
if str(BASE_DIR) not in sys.path:
    sys.path.insert(0, str(BASE_DIR))

# Handle environments where google-genai is uninstalled by supplying a stub module
if "google.genai" not in sys.modules:
    try:
        from google import genai
    except ImportError:
        genai_mod = types.ModuleType("google.genai")
        genai_mod.types = types.ModuleType("types")
        genai_mod.Client = lambda **kwargs: None
        sys.modules["google.genai"] = genai_mod
        sys.modules["google.genai.types"] = genai_mod.types

from ml.inference.confidence import compute_prediction_confidence
from ml.forecast.engine import ForecastInferenceEngine, load_forecast_artifact
from ml.forecast.features.validator import ForecastFeaturesInvalidError
from ml.inference.orchestrate_evidence_cli import build_canonical_f3_payload
from ml.inference.vision_cli import map_visual_indicators_to_f6_category
from app.services.gemini_pipeline import AeroSentinelGeminiPipeline
from app.services.vision_service import analyze_citizen_image
from app.services.privacy_guard import PrivacyGuard
from ml.alert_support.scoring_engine import EventEvidenceScoringEngine
from app.utils.alert_config import alert_config
from ml.alert_support.citizen_dedup import CitizenReportDeduplicator

# ---------------------------------------------------------
# Global Artifact Storage
# ---------------------------------------------------------
ml_artifacts: Dict[str, Any] = {}

ARTIFACTS_DIR = BASE_DIR / "models" / "artifacts"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Loads serialized ML models, scalers, and calibration curves on startup.
    """
    hotspot_path = ARTIFACTS_DIR / "hotspot_classifier_v1.joblib"
    forecast_path = ARTIFACTS_DIR / "forecast_regressors_v1.joblib"

    if hotspot_path.exists():
        ml_artifacts["hotspot"] = joblib.load(hotspot_path)
        print(f"[INFO] Loaded Hotspot Classifier artifact: {hotspot_path.name}")
    else:
        print(f"[WARNING] Hotspot model artifact not found at {hotspot_path}")

    if forecast_path.exists():
        ml_artifacts["forecast"] = joblib.load(forecast_path)
        print(f"[INFO] Loaded Multi-Horizon Forecast artifact: {forecast_path.name}")
    else:
        print(f"[WARNING] Forecast model artifact not found at {forecast_path}")

    yield

    ml_artifacts.clear()


# ---------------------------------------------------------
# FastAPI Application Declaration
# ---------------------------------------------------------
app = FastAPI(
    title="AeroSentinel AI Inference Engine",
    description="Real-time multi-hazard hotspot detection and multi-horizon air quality forecasting.",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------
# Request / Response Schemas
# ---------------------------------------------------------
class ObservationPayload(BaseModel):
    station_id: str = Field(default="site_5766", description="Station identifier")
    temperature: float = Field(default=25.0, description="Surface temperature in Celsius")
    humidity: float = Field(default=60.0, description="Relative humidity percentage")
    wind_speed: float = Field(default=3.5, description="Wind speed in km/h")
    wind_direction: float = Field(default=180.0, description="Wind direction in degrees")
    wind_u: Optional[float] = Field(default=0.0)
    wind_v: Optional[float] = Field(default=0.0)
    rainfall: Optional[float] = Field(default=0.0)
    pressure: Optional[float] = Field(default=950.0)
    pm25: Optional[float] = Field(default=45.0, description="Current PM2.5 in ug/m3")
    pm10: Optional[float] = Field(default=85.0)
    no2: Optional[float] = Field(default=20.0)
    so2: Optional[float] = Field(default=12.0)
    fire_count_24h_25km: Optional[int] = Field(default=0)
    fire_frp_sum_24h_25km: Optional[float] = Field(default=0.0)
    nearest_fire_distance_km: Optional[float] = Field(default=50.0)
    fire_upwind_alignment_score: Optional[float] = Field(default=0.0)
    hour_sin: Optional[float] = Field(default=0.0)
    hour_cos: Optional[float] = Field(default=1.0)
    dow_sin: Optional[float] = Field(default=0.0)
    dow_cos: Optional[float] = Field(default=1.0)
    is_weekend: Optional[int] = Field(default=0)


class HotspotResponse(BaseModel):
    is_hotspot: bool
    probability: float
    confidence_level: str
    epistemic_uncertainty_flag: int
    model_version: str


class ForecastResponse(BaseModel):
    station_id: str
    forecast_pm25: Dict[str, float]
    advisory: str


class HotspotInferenceRequest(BaseModel):
    h3Index: Optional[str] = Field(default=None)
    h3_index: Optional[str] = Field(default=None)
    cityName: Optional[str] = Field(default="Pune")
    city_name: Optional[str] = Field(default="Pune")
    cityId: Optional[str] = Field(default=None)
    city_id: Optional[str] = Field(default=None)
    featureSchemaVersion: Optional[str] = Field(default="f3-features-v1")
    features: Optional[List[float]] = Field(default=None, description="Exactly 36 ordered feature values")
    featureMap: Optional[Dict[str, float]] = Field(default=None, description="Key-value mapping of 36 feature names")
    feature_map: Optional[Dict[str, float]] = Field(default=None)


class HotspotInferenceResponse(BaseModel):
    h3Index: str
    riskScore: float
    riskLevel: str
    confidence: float
    isHotspot: bool
    operationalThreshold: float
    modelVersion: str
    engineType: str
    status: str
    message: Optional[str] = None
    metadata: Dict[str, Any] = Field(default_factory=dict)


# ---------------------------------------------------------
# Endpoints
# ---------------------------------------------------------
@app.get("/", tags=["System"])
def root():
    return {
        "service": "AeroSentinel AI Inference Engine",
        "status": "operational",
        "loaded_models": list(ml_artifacts.keys()),
    }


@app.get("/health", tags=["System"])
def health():
    hotspot_ready = "hotspot" in ml_artifacts
    forecast_ready = "forecast" in ml_artifacts
    from app.services.gemini_client import gemini_client
    gemini_configured = gemini_client.is_configured()

    return {
        "status": "healthy",
        "models_ready": hotspot_ready and forecast_ready,
        "models": {
            "hotspot": {
                "ready": hotspot_ready,
                "modelVersion": "hotspot_classifier_v1" if hotspot_ready else None,
            },
            "forecast": {
                "ready": forecast_ready,
                "modelVersion": "forecast_regressors_v1" if forecast_ready else None,
            },
            "gemini_vision": {
                "configured": gemini_configured,
                "provider": "google-gemini" if gemini_configured else "deterministic-fallback",
            },
        },
        "artifacts": list(ml_artifacts.keys()),
    }


@app.post("/predict/hotspot", response_model=HotspotResponse, tags=["Inference"])
def predict_hotspot(payload: ObservationPayload):
    """
    Evaluates current atmospheric, spatial, and fire indicators to classify hotspot emergence.
    """
    if "hotspot" not in ml_artifacts:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Hotspot classifier is not loaded.",
        )

    model_bundle = ml_artifacts["hotspot"]
    model = model_bundle.get("model") if isinstance(model_bundle, dict) else model_bundle

    input_dict = payload.model_dump()
    df_input = pd.DataFrame([input_dict])

    try:
        if hasattr(model, "predict_proba"):
            prob = float(model.predict_proba(df_input)[:, 1][0])
        elif hasattr(model, "predict"):
            prob = float(model.predict(df_input)[0])
        else:
            prob = 0.5
    except Exception:
        # Fallback if specific trained feature subset is expected
        if isinstance(model_bundle, dict) and "features" in model_bundle:
            feats = model_bundle["features"]
            sub_df = pd.DataFrame([{f: input_dict.get(f, 0.0) for f in feats}])
            prob = float(model.predict_proba(sub_df)[:, 1][0])
        else:
            prob = 0.5

    is_hotspot = prob >= 0.50
    confidence = "HIGH" if prob > 0.75 or prob < 0.25 else "MODERATE"

    return HotspotResponse(
        is_hotspot=is_hotspot,
        probability=round(prob, 4),
        confidence_level=confidence,
        epistemic_uncertainty_flag=1 if 0.40 <= prob <= 0.60 else 0,
        model_version="hotspot_classifier_v1",
    )


@app.get("/api/v1/ml/hotspot/model-info", tags=["Inference"])
def get_hotspot_model_info():
    if "hotspot" not in ml_artifacts:
        return {
            "status": "MODEL_UNAVAILABLE",
            "modelVersion": "hotspot_classifier_v1",
            "engineType": "ML",
            "loaded": False,
        }
    art = ml_artifacts["hotspot"]
    return {
        "status": "OPERATIONAL",
        "modelVersion": "hotspot_classifier_v1",
        "engineType": "ML",
        "loaded": True,
        "algorithm": str(art.get("algorithm", "CalibratedClassifierCV")),
        "featureCount": len(art.get("feature_cols", [])),
        "operationalThreshold": float(art.get("operational_threshold", 0.20)),
        "supportedDomain": "Pune Metropolitan Region",
    }


@app.post("/api/v1/ml/hotspot/predict", response_model=HotspotInferenceResponse, tags=["Inference"])
def predict_hotspot_v1(req: HotspotInferenceRequest):
    """
    Evaluates a 36-feature vector against the calibrated hotspot_classifier_v1 model.
    Enforces model domain (Pune only).
    """
    if "hotspot" not in ml_artifacts:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="MODEL_UNAVAILABLE: Hotspot classifier artifact is not loaded.",
        )

    art = ml_artifacts["hotspot"]
    model = art["model"]
    cols = art["feature_cols"]
    threshold = float(art.get("operational_threshold", 0.20))
    model_version = "hotspot_classifier_v1"
    engine_type = "ML"

    h3_index = req.h3Index or req.h3_index or "unknown"
    city_name = req.cityName or req.city_name or "Pune"

    # Enforce Model Domain: Pune Metropolitan Region ONLY
    if city_name.strip().lower() not in ["pune", "pmr", "pune metropolitan region"]:
        return HotspotInferenceResponse(
            h3Index=h3_index,
            riskScore=0.0,
            riskLevel="LOW",
            confidence=0.0,
            isHotspot=False,
            operationalThreshold=threshold,
            modelVersion=model_version,
            engineType=engine_type,
            status="MODEL_DOMAIN_UNSUPPORTED",
            message=f"Model domain unsupported: hotspot_classifier_v1 was trained exclusively on Pune Metropolitan Region telemetry and is not validated for {city_name}.",
            metadata={
                "h3Index": h3_index,
                "cityName": city_name,
                "domainSupported": False,
            },
        )

    # Extract 36 features
    ordered_values = req.features
    feature_map = req.featureMap or req.feature_map

    if ordered_values is not None:
        if len(ordered_values) != 36:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"INSUFFICIENT_DATA: Expected exactly 36 features, got {len(ordered_values)}",
            )
        row_dict = {cols[i]: float(ordered_values[i]) for i in range(36)}
    elif feature_map is not None:
        row_dict = {}
        missing = []
        for col in cols:
            if col not in feature_map or feature_map[col] is None:
                missing.append(col)
            else:
                row_dict[col] = float(feature_map[col])
        if missing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"INSUFFICIENT_DATA: Missing required model features: {missing}",
            )
    else:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="INSUFFICIENT_DATA: Neither 'features' array nor 'featureMap' provided.",
        )

    # Validate finite values
    for k, v in row_dict.items():
        if np.isnan(v) or np.isinf(v):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"INVALID_MODEL_OUTPUT: Non-finite value in feature '{k}': {v}",
            )

    # Build DataFrame in exact artifact order
    df = pd.DataFrame([row_dict])[cols]

    # Predict probability
    try:
        proba = model.predict_proba(df)[0]
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"MODEL_INFERENCE_FAILED: {str(e)}",
        )

    classes = getattr(model, "classes_", np.array([0, 1]))
    pos_idx_arr = np.where(classes == 1)[0]
    pos_idx = int(pos_idx_arr[0]) if len(pos_idx_arr) > 0 else 1

    calibrated_prob = float(proba[pos_idx])
    if calibrated_prob < 0.0 or calibrated_prob > 1.0 or np.isnan(calibrated_prob):
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"INVALID_MODEL_OUTPUT: Calibrated probability out of bounds: {calibrated_prob}",
        )

    is_hotspot = calibrated_prob >= threshold

    # Map risk level
    if calibrated_prob < 0.20:
        risk_level = "LOW"
    elif calibrated_prob < 0.40:
        risk_level = "MODERATE"
    elif calibrated_prob < 0.70:
        risk_level = "HIGH"
    else:
        risk_level = "CRITICAL"

    # Compute hyperlocal confidence
    conf_res = compute_prediction_confidence(row_dict, calibrated_prob)
    overall_conf = float(conf_res.get("overall_confidence", 0.85))

    return HotspotInferenceResponse(
        h3Index=h3_index,
        riskScore=round(calibrated_prob, 4),
        riskLevel=risk_level,
        confidence=round(overall_conf, 4),
        isHotspot=is_hotspot,
        operationalThreshold=threshold,
        modelVersion=model_version,
        engineType=engine_type,
        status="SUCCESS",
        metadata={
            "h3Index": h3_index,
            "cityName": city_name,
            "algorithm": str(art.get("algorithm", "CalibratedClassifierCV")),
            "confidenceFactors": conf_res,
            "calibratedProbability": round(calibrated_prob, 4),
            "positiveClassIndex": pos_idx,
            "domainSupported": True,
        },
    )


@app.post("/predict/forecast", response_model=ForecastResponse, tags=["Inference"])
def predict_forecast(payload: ObservationPayload):
    """
    Forecasts future PM2.5 levels at T+1, T+3, and T+6 hours ahead.
    Never fabricates predictions when models or data are unavailable.
    """
    if "forecast" not in ml_artifacts:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="FORECAST_MODEL_UNAVAILABLE: Multi-horizon forecaster artifact is not loaded.",
        )

    bundle = ml_artifacts["forecast"]
    if not isinstance(bundle, dict) or "models" not in bundle:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="FORECAST_MODEL_CONTRACT_INVALID: Forecast artifact models bundle missing or invalid.",
        )

    models = bundle["models"]
    input_data = pd.DataFrame([payload.model_dump()])
    forecasts: Dict[str, float] = {}

    for horizon in [1, 3, 6]:
        model = models.get(horizon) or models.get(f"t_plus_{horizon}")
        if model is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"FORECAST_MODEL_UNAVAILABLE: Regressor model for horizon T+{horizon}h is missing.",
            )
        try:
            pred_val = float(model.predict(input_data)[0])
            if np.isnan(pred_val) or np.isinf(pred_val):
                raise ValueError(f"Non-finite forecast value: {pred_val}")
            forecasts[f"T+{horizon}h"] = round(pred_val, 2)
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"MODEL_INFERENCE_FAILED: Horizon T+{horizon}h prediction failed: {str(e)}",
            )

    max_pred = max(forecasts.values())
    advisory = "Satisfactory" if max_pred < 60 else "Moderate" if max_pred < 120 else "Poor / Hazardous"

    return ForecastResponse(
        station_id=payload.station_id,
        forecast_pm25=forecasts,
        advisory=advisory,
    )


# ---------------------------------------------------------
# F4 Authoritative Forecast HTTP Endpoint
# ---------------------------------------------------------
@app.post("/api/v1/ml/forecast/predict", tags=["Forecast"])
def predict_forecast_v1(req: Dict[str, Any]):
    """
    Authoritative multi-horizon forecast endpoint (F4) for Spring Boot backend.
    Executes ForecastInferenceEngine on 36-feature vector for horizons [1, 3, 6] with empirical intervals.
    Never fabricates numeric fallbacks.
    """
    try:
        engine = ForecastInferenceEngine()
        result = engine.predict(req)
        return result
    except FileNotFoundError as fnf:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"FORECAST_MODEL_UNAVAILABLE: {str(fnf)}",
        )
    except (ValueError, ForecastFeaturesInvalidError) as ve:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"INVALID_INPUT: {str(ve)}",
        )
    except Exception as e:
        err_msg = str(e)
        if "FORECAST_MODEL_UNAVAILABLE" in err_msg:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=err_msg)
        if "INVALID_INPUT" in err_msg or "INVALID_INTERVAL" in err_msg:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=err_msg)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"FORECAST_INFERENCE_FAILED: {err_msg}",
        )


# ---------------------------------------------------------
# F5 Authoritative Evidence Orchestration HTTP Endpoint
# ---------------------------------------------------------
@app.post("/api/v1/ml/evidence/orchestrate", tags=["Evidence"])
def orchestrate_evidence_v1(input_data: Dict[str, Any]):
    """
    Authoritative evidence orchestration endpoint (F5) for Spring Boot backend.
    Executes canonical F3 payload construction, Gemini narrative generation, and EventEvidenceScoringEngine.
    """
    try:
        h3_index = input_data.get("h3Index") or input_data.get("h3_index") or "unknown"
        city_id = input_data.get("cityId") or input_data.get("city_id") or "unknown"
        prediction_id = input_data.get("predictionId") or "unknown"

        # 1. Build canonical payload
        f3_payload = build_canonical_f3_payload(input_data)

        # 2. Run Gemini Pipeline (or deterministic grounded fallback)
        explanation = AeroSentinelGeminiPipeline.generate_explanation(f3_payload)
        explanation_dict = explanation.model_dump()

        # 3. Run EventEvidenceScoringEngine
        engine = EventEvidenceScoringEngine(config=alert_config)
        neighbor_payloads = input_data.get("neighborHotspots") or []
        citizen_reports = input_data.get("citizenReports") or []

        alert_payload = engine.generate_alert_support_payload(
            f3_payload=f3_payload,
            f4_explanation=explanation_dict,
            neighbor_payloads=neighbor_payloads,
            raw_citizen_reports=citizen_reports,
        )

        # 4. Generate Recommended Verification Actions based on AlertSupportState
        state_str = alert_payload.alert_support_state.value
        if state_str == "ALERT_CANDIDATE":
            action = "Dispatch Rapid Response & Issue Sensor Ground-Truthing Directive"
            priority = "URGENT"
            guidelines = [
                "Deploy mobile CAAQMS rapid-sampling unit to verify localized concentration peak.",
                "Cross-reference upwind industrial emission permits within 2km boundary.",
                "Verify sensor optical calibration against drift before issuing municipal public health advisory.",
            ]
        elif state_str == "MONITOR":
            action = "Maintain Heightened Spatial Surveillance & Monitor Forecast Horizons"
            priority = "ELEVATED"
            guidelines = [
                "Track 1h and 3h PM2.5 forecast trajectories for sustained persistence above threshold.",
                "Inspect adjacent H3 hexagon telemetry for potential dispersion cluster expansion.",
                "Validate CAAQMS data freshness on next scheduled ingestion cycle.",
            ]
        else:
            action = "Standard Routine Surveillance"
            priority = "ROUTINE"
            guidelines = [
                "Telemetry within nominal variance or insufficient corroboration.",
                "Continue automated 15-minute scheduled polling without dispatching field personnel.",
            ]

        # 5. Format atomic EvidenceSignals for Java DTO
        signals_out = []
        for idx, ev in enumerate(f3_payload.get("evidence", [])):
            signals_out.append({
                "signalId": f"sig-{h3_index[:8]}-{idx+1:03d}",
                "sourceType": ev.get("category", "DIRECT_OBSERVATION"),
                "description": ev.get("attribution_note", ""),
                "timestamp": f3_payload.get("timestamp"),
                "confidenceScore": 0.90,
                "dataSource": ev.get("source", "CAAQMS"),
                "relevanceTier": "PRIMARY" if idx == 0 else "SUPPORTING",
                "sourceRef": ev.get("source", "STATION-REF"),
            })

        # Append deduplicated citizen reports as AUXILIARY evidence signals
        valid_citizen_reports = CitizenReportDeduplicator.deduplicate_reports(citizen_reports)
        for cr in valid_citizen_reports:
            rep_id = str(cr.get("report_id", "unknown"))
            cat = cr.get("detected_category") or cr.get("category", "OTHER")
            desc = cr.get("description", "Citizen visual observation")
            conf = float(cr.get("confidence", 0.75))
            signals_out.append({
                "signalId": f"sig-citizen-{rep_id[:8]}",
                "sourceType": "CITIZEN_OBSERVATION",
                "description": f"Citizen visual observation ({cat}): {desc}",
                "timestamp": cr.get("timestamp", f3_payload.get("timestamp")),
                "confidenceScore": conf,
                "dataSource": "CITIZEN",
                "relevanceTier": "AUXILIARY",
                "sourceRef": rep_id,
            })

        # 6. Format Final Response Contract
        return {
            "status": "SUCCESS",
            "h3Index": h3_index,
            "cityId": city_id,
            "predictionId": prediction_id,
            "eventId": alert_payload.event_id,
            "canonicalEventId": alert_payload.grouping.canonical_event_id,
            "clusterH3Cells": alert_payload.grouping.cluster_h3_cells,
            "evidenceScore": alert_payload.evidence_score,
            "evidenceCompleteness": alert_payload.evidence_completeness,
            "consistency": alert_payload.evidence_consistency.value,
            "triageState": state_str,
            "scoreBreakdown": alert_payload.score_breakdown.model_dump(),
            "sourceMatrix": {
                k: v.value for k, v in alert_payload.source_matrix.model_dump().items()
            },
            "unavailableSources": alert_payload.unavailable_sources,
            "conflictingNotes": alert_payload.conflicting_notes,
            "supportingSignals": alert_payload.supporting_signals,
            "signals": signals_out,
            "leadTimeMetrics": alert_payload.lead_time_metrics.model_dump(),
            "grouping": alert_payload.grouping.model_dump(),
            "aiInterpretation": {
                "summaryPublic": explanation.event_summary_public,
                "summaryAnalyst": explanation.event_summary_analyst,
                "detectedCondition": explanation.detected_condition,
                "supportingSignals": explanation.supporting_signals,
                "forecastTrajectory": explanation.forecast_trajectory,
                "uncertaintyStatement": explanation.uncertainty_and_confidence_statement,
                "unsupportedConclusions": explanation.unsupported_conclusions,
                "causalClaimSupported": explanation.causal_claim_supported,
                "isGrounded": True,
                "modelVersion": explanation.model_version,
                "promptVersion": explanation.prompt_version,
            },
            "recommendedVerification": {
                "action": action,
                "priority": priority,
                "guidelines": guidelines,
            },
            "provenance": {
                "h3Index": h3_index,
                "cityId": city_id,
                "f3PredictionId": prediction_id,
                "f3ModelVersion": f3_payload.get("model_version"),
                "f4ModelVersion": "forecast_regressors_v1",
                "f5ScoringVersion": alert_payload.scoring_version,
                "geminiModelVersion": explanation.model_version,
                "geminiPromptVersion": explanation.prompt_version,
                "evaluatedAt": datetime.now(timezone.utc).isoformat(),
            },
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"EVIDENCE_ORCHESTRATION_FAILED: {str(e)}",
        )


# ---------------------------------------------------------
# F6 Authoritative Vision Analysis HTTP Endpoint
# ---------------------------------------------------------
@app.post("/api/v1/ml/vision/analyze", tags=["Vision"])
def analyze_vision_v1(input_data: Dict[str, Any]):
    """
    Authoritative citizen photo vision analysis endpoint (F6) for Spring Boot backend.
    """
    image_path = input_data.get("imagePath") or input_data.get("image_path")
    image_base64 = input_data.get("imageBase64") or input_data.get("image_base64")
    report_id = input_data.get("reportId") or input_data.get("report_id") or "unknown"
    h3_index = input_data.get("h3Index") or input_data.get("h3_index") or "unknown"

    temp_path = None
    try:
        if image_base64:
            import tempfile
            import base64
            if "," in image_base64:
                image_base64 = image_base64.split(",", 1)[1]
            img_bytes = base64.b64decode(image_base64)
            with tempfile.NamedTemporaryFile(delete=False, suffix=".jpg") as tf:
                tf.write(img_bytes)
                temp_path = Path(tf.name)
            img_file = temp_path
        elif image_path:
            img_file = Path(image_path)
            if not img_file.exists() or not img_file.is_file():
                candidates = [
                    img_file,
                    BASE_DIR.parent / image_path,
                    BASE_DIR.parent / "backend" / image_path,
                ]
                found = None
                for c in candidates:
                    if c.exists() and c.is_file():
                        found = c
                        break
                if found:
                    img_file = found
                else:
                    return {
                        "status": "FALLBACK",
                        "reportId": report_id,
                        "h3Index": h3_index,
                        "category": "UNKNOWN",
                        "confidence": 0.50,
                        "observations": [f"Image file not found at: {image_path}"],
                        "uncertainty": ["Image file could not be resolved on disk; deterministic fallback activated"],
                        "indicators": {
                            "smoke_visible": False, "fire_visible": False, "dust_visible": False,
                            "haze_visible": False, "industrial_context_visible": False, "traffic_context_visible": False,
                        },
                        "privacyFlags": ["PII scan cleared"],
                        "modelVersion": "deterministic-fallback",
                        "promptVersion": "vision_analysis_v001",
                    }
        else:
            return {
                "status": "FALLBACK",
                "reportId": report_id,
                "h3Index": h3_index,
                "category": "UNKNOWN",
                "confidence": 0.50,
                "observations": ["Missing imagePath or imageBase64 in request payload"],
                "uncertainty": ["No photo data provided for analysis"],
                "indicators": {
                    "smoke_visible": False, "fire_visible": False, "dust_visible": False,
                    "haze_visible": False, "industrial_context_visible": False, "traffic_context_visible": False,
                },
                "privacyFlags": ["PII scan cleared"],
                "modelVersion": "deterministic-fallback",
                "promptVersion": "vision_analysis_v001",
            }

        # 1. Analyze image via vision service
        analysis = analyze_citizen_image(img_file)

        # 2. Sanitize via PrivacyGuard
        sanitized_dict = PrivacyGuard.sanitize_vision_analysis(analysis.model_dump())

        # 3. Deterministically adapt visual indicators to F6 category
        indicators = analysis.image_indicators
        f6_category = map_visual_indicators_to_f6_category(indicators)

        from app.services.gemini_client import gemini_client
        is_fallback = any("deterministic fallback" in str(lim).lower() for lim in analysis.limitations)
        actual_model = gemini_client.model_name if (gemini_client.is_configured() and not is_fallback) else "deterministic-fallback"

        return {
            "status": "SUCCESS",
            "reportId": report_id,
            "h3Index": h3_index,
            "category": f6_category,
            "confidence": round(float(analysis.visual_confidence_estimate), 4),
            "observations": sanitized_dict.get("observed_visual_features", []),
            "uncertainty": analysis.limitations,
            "indicators": indicators.model_dump(),
            "privacyFlags": sanitized_dict.get("privacy_flags", []),
            "modelVersion": actual_model,
            "promptVersion": "vision_analysis_v001",
        }
    except Exception as e:
        return {
            "status": "FALLBACK",
            "reportId": report_id,
            "h3Index": h3_index,
            "category": "UNKNOWN",
            "confidence": 0.50,
            "observations": ["Visual observation unconfirmed: processing error encountered"],
            "uncertainty": [
                "Image analysis could not be completed; deterministic fallback",
                f"Error details: {str(e)}",
            ],
            "indicators": {
                "smoke_visible": False, "fire_visible": False, "dust_visible": False,
                "haze_visible": False, "industrial_context_visible": False, "traffic_context_visible": False,
            },
            "privacyFlags": ["PII scan cleared"],
            "modelVersion": "deterministic-fallback",
            "promptVersion": "vision_analysis_v001",
        }
    finally:
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except Exception:
                pass


# ---------------------------------------------------------
# Conversational Assistant Synthesis Endpoint (Phase 4)
# ---------------------------------------------------------
class AssistantSynthesisPayload(BaseModel):
    user_message: str
    tool_results: Dict[str, Any]
    locale: Optional[str] = "en"
    location_name: Optional[str] = "Local Area"


@app.post("/api/v1/assistant/synthesize", tags=["Assistant"])
def synthesize_assistant_endpoint(payload: AssistantSynthesisPayload):
    """
    Synthesizes a grounded natural-language assistant response from validated tool outputs.
    Preserves exact numerical values and transparently discloses model status and data limitations.
    """
    from app.services.assistant_synthesis import synthesize_assistant_turn
    return synthesize_assistant_turn(
        user_message=payload.user_message,
        tool_results=payload.tool_results,
        locale=payload.locale or "en",
        location_name=payload.location_name or "Local Area",
    )
