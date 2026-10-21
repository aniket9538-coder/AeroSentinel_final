"""
AeroSentinel - Assistant Synthesis & Grounding Test Suite (Phase 4.1)
File: ai-service/tests/test_assistant_synthesis.py
"""

import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch

from app.main import app
from app.services.assistant_synthesis import (
    extract_numbers_from_tools,
    extract_critical_facts,
    verify_numerical_claims,
    build_deterministic_synthesis,
    synthesize_assistant_turn,
)


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def sample_tool_results():
    return {
        "currentWeather": {
            "status": "VALID",
            "locationName": "Pune",
            "temperature": 25.4,
            "humidity": 55.0,
            "windSpeed": 11.2,
            "source": "Open-Meteo NWP Model Analysis",
            "observationTime": "2026-10-18T12:00:00Z"
        },
        "weatherForecast": {
            "status": "VALID",
            "locationName": "Pune",
            "temperature": 23.5,
            "precipitation": 2.4,
            "windSpeed": 9.5,
            "source": "Open-Meteo Numerical Forecast",
            "forecastTimeRange": "Evening"
        },
        "airQuality": {
            "status": "VALID",
            "locationName": "Pune",
            "pm25": 48.0,
            "pm10": 85.0,
            "stationName": "Shivajinagar CAAQMS",
            "stationCode": "PUN-001",
            "source": "CPCB",
            "freshness": "LIVE"
        },
        "officialWarnings": {
            "status": "NO_ACTIVE_WARNINGS",
            "locationName": "Pune"
        },
        "pm25Risk": {
            "status": "VALID",
            "locationName": "Pune",
            "riskLevel": "MODERATE",
            "riskScore": 0.45,
            "forecastHorizons": [
                {
                    "horizonHours": 1,
                    "predictedPm25": 50.2,
                    "lowerBound": 47.8,
                    "upperBound": 52.6,
                    "unit": "ug/m3"
                }
            ]
        }
    }


def test_extract_numbers_from_tools(sample_tool_results):
    numbers = extract_numbers_from_tools(sample_tool_results)
    assert 25.4 in numbers
    assert 55.0 in numbers
    assert 48.0 in numbers
    assert 50.2 in numbers


def test_deterministic_synthesis_preserves_facts(sample_tool_results):
    synthesis = build_deterministic_synthesis(
        user_message="What is the weather and air quality in Pune?",
        tool_results=sample_tool_results,
        locale="en",
        location_name="Pune"
    )
    assert synthesis["status"] == "SUCCESS"
    assert synthesis["generator"] == "deterministic_fallback"
    assert "25.4°C" in synthesis["answer"]
    assert "48.0 µg/m³" in synthesis["answer"]
    assert "Shivajinagar CAAQMS" in synthesis["answer"]
    assert "Open-Meteo NWP Model Analysis" in synthesis["answer"]
    assert 25.4 in synthesis["preserved_numerical_values"]
    assert 48.0 in synthesis["preserved_numerical_values"]


def test_deterministic_synthesis_hindi(sample_tool_results):
    synthesis = build_deterministic_synthesis(
        user_message="मौसम",
        tool_results=sample_tool_results,
        locale="hi",
        location_name="Pune"
    )
    assert synthesis["status"] == "SUCCESS"
    assert synthesis["language"] == "hi"
    assert "वर्तमान मौसम" in synthesis["answer"]
    assert "25.4°C" in synthesis["answer"]
    assert "48.0 µg/m³" in synthesis["answer"]


def test_deterministic_synthesis_marathi(sample_tool_results):
    synthesis = build_deterministic_synthesis(
        user_message="हवामान",
        tool_results=sample_tool_results,
        locale="mr",
        location_name="Pune"
    )
    assert synthesis["status"] == "SUCCESS"
    assert synthesis["language"] == "mr"
    assert "सध्याचे हवामान" in synthesis["answer"]
    assert "25.4°C" in synthesis["answer"]
    assert "48.0 µg/m³" in synthesis["answer"]


def test_unsupported_domain_explained_truthfully():
    tool_results = {
        "pm25Risk": {
            "status": "MODEL_DOMAIN_UNSUPPORTED",
            "locationName": "Mumbai"
        }
    }
    synthesis = build_deterministic_synthesis(
        user_message="What is the PM2.5 risk in Mumbai?",
        tool_results=tool_results,
        locale="en",
        location_name="Mumbai"
    )
    assert "Pune Metropolitan Region" in synthesis["answer"]
    assert "unsupported outside Pune" in synthesis["answer"]


def test_numerical_verification_rejects_hallucinations(sample_tool_results):
    facts = extract_critical_facts(sample_tool_results)
    assert facts["temperature"] == 25.4
    assert facts["pm25"] == 48.0

    # Grounded answer: matches facts
    valid_text = "The current temperature is 25.4°C and PM2.5 is 48.0 in 2026."
    assert verify_numerical_claims(valid_text, facts) is True

    # Hallucinated temperature (claims 38.5°C when tool had 25.4°C)
    fake_temp_text = "The current temperature is 38.5°C in Pune."
    assert verify_numerical_claims(fake_temp_text, facts) is False

    # Hallucinated PM2.5 (claims PM2.5 is 99.0 when tool had 48.0)
    fake_pm_text = "The PM2.5 concentration is 99.0 ug/m3."
    assert verify_numerical_claims(fake_pm_text, facts) is False


def test_api_endpoint_assistant_synthesize(client, sample_tool_results):
    payload = {
        "user_message": "What is the weather in Pune right now?",
        "tool_results": sample_tool_results,
        "locale": "en",
        "location_name": "Pune"
    }
    response = client.post("/api/v1/assistant/synthesize", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "SUCCESS"
    assert "answer" in data
    assert "Pune" in data["answer"]
    assert len(data["preserved_numerical_values"]) > 0
