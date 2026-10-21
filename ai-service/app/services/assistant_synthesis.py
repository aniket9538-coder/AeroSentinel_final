"""
AeroSentinel - Trusted Conversational Assistant Grounded Synthesis Service
File: ai-service/app/services/assistant_synthesis.py

Provides grounded natural-language synthesis for Citizen Assistant queries.
Strictly preserves numerical values and sources from validated tool executions.
Supports English (en), Hindi (hi), and Marathi (mr).
"""

import json
import logging
import re
from typing import Dict, Any, List, Optional
from app.services.gemini_client import gemini_client

logger = logging.getLogger("AeroSentinel.AssistantSynthesis")

ASSISTANT_SYSTEM_INSTRUCTION = """
You are the AeroSentinel Conversational Assistant. Your mission is to provide accurate, transparent, and grounded environmental and weather information to citizens.

CRITICAL GROUNDING RULES:
1. USE ONLY THE EXACT NUMERICAL VALUES PROVIDED IN THE TOOL RESULTS. NEVER alter, round differently, guess, or invent temperature, PM2.5, humidity, wind, or precipitation values.
2. Clearly distinguish current weather observations from weather forecasts. Observations are ground measurements taken at a specific past/current time; forecasts are forward-looking numerical simulations.
3. Clearly distinguish official government warnings (from IMD / NDMA SACHET) from AeroSentinel model risk evaluations. Never claim an AeroSentinel risk is an official government warning.
4. If a PM2.5 forecast or hotspot risk is provided, mention that models are calibrated strictly for Pune Metropolitan Region and that Random Forest models are experimental (where a persistence baseline was superior on holdout evaluations).
5. If data is unavailable or stale, clearly explain the limitation without making excuses.
6. Provide answers in the requested language (English, Hindi, or Marathi) while preserving units (°C, µg/m³, km/h, mm) and source names.

Return a JSON object conforming strictly to:
{
  "answer": "Clear, grounded user response text.",
  "preserved_numerical_values": [12.5, 45.0],
  "limitations": ["Explanation of any data limitations"]
}
"""


def extract_numbers_from_tools(tool_results: Dict[str, Any]) -> List[float]:
    """Recursively extracts all valid numeric values from tool results for validation."""
    numbers = []

    def _walk(obj):
        if isinstance(obj, dict):
            for v in obj.values():
                _walk(v)
        elif isinstance(obj, list):
            for item in obj:
                _walk(item)
        elif isinstance(obj, (int, float)) and not isinstance(obj, bool):
            numbers.append(float(obj))

    _walk(tool_results)
    return numbers


def extract_critical_facts(tool_results: Dict[str, Any]) -> Dict[str, float]:
    """Extracts known environmental metric facts from tool payloads."""
    facts = {}
    cw = tool_results.get("currentWeather")
    if isinstance(cw, dict) and cw.get("status") == "VALID":
        if cw.get("temperature") is not None:
            facts["temperature"] = float(cw["temperature"])
        if cw.get("humidity") is not None:
            facts["humidity"] = float(cw["humidity"])
        if cw.get("windSpeed") is not None:
            facts["windSpeed"] = float(cw["windSpeed"])
        if cw.get("precipitation") is not None:
            facts["precipitation"] = float(cw["precipitation"])

    aq = tool_results.get("airQuality")
    if isinstance(aq, dict) and aq.get("status") == "VALID":
        if aq.get("pm25") is not None:
            facts["pm25"] = float(aq["pm25"])
        if aq.get("pm10") is not None:
            facts["pm10"] = float(aq["pm10"])

    wf = tool_results.get("weatherForecast")
    if isinstance(wf, dict) and wf.get("status") == "VALID":
        if wf.get("temperature") is not None:
            facts["forecast_temp"] = float(wf["temperature"])
        if wf.get("precipitation") is not None:
            facts["forecast_precip"] = float(wf["precipitation"])

    return facts


def verify_numerical_claims(answer: str, facts: Dict[str, float]) -> bool:
    """
    Verifies that numeric metrics claimed in text do not contradict tool facts.
    Allows harmless numbers (dates, hours like 24h, years like 2026, status codes).
    """
    if not answer or not facts:
        return True

    # Check temperature claim
    if "temperature" in facts:
        expected_t = facts["temperature"]
        # Look for patterns like "35°C" or "35 °C"
        matches = re.findall(r"(-?\d+(?:\.\d+)?)\s*°?C", answer)
        for m in matches:
            val = float(m)
            # If a Celsius degree is stated and it differs significantly from the tool fact, flag violation
            if abs(val - expected_t) > 0.5 and "forecast_temp" in facts and abs(val - facts["forecast_temp"]) > 0.5:
                logger.warning(f"Numerical violation: claimed {val}°C, expected {expected_t}°C")
                return False

    # Check PM2.5 claim
    if "pm25" in facts:
        expected_pm = facts["pm25"]
        matches = re.findall(r"PM\s*2\.?5.*?(-?\d+(?:\.\d+)?)", answer, re.IGNORECASE)
        for m in matches:
            val = float(m)
            if abs(val - expected_pm) > 0.5:
                logger.warning(f"Numerical violation: claimed PM2.5 of {val}, expected {expected_pm}")
                return False

    return True


def build_deterministic_synthesis(
    user_message: str,
    tool_results: Dict[str, Any],
    locale: str = "en",
    location_name: str = "Local Area"
) -> Dict[str, Any]:
    """Produces deterministic grounded synthesis when Gemini is unconfigured or unavailable."""
    lines = []
    preserved = []
    limitations = []
    lang = locale.lower().strip() if locale else "en"

    if lang == "hi":
        return _build_hindi_synthesis(tool_results, location_name)
    elif lang == "mr":
        return _build_marathi_synthesis(tool_results, location_name)

    # English Default
    cw = tool_results.get("currentWeather")
    if cw and isinstance(cw, dict) and cw.get("status") == "VALID":
        lines.append(f"Current Weather in {cw.get('locationName', location_name)}:")
        if cw.get("temperature") is not None:
            lines.append(f"• Temperature: {cw['temperature']}°C")
            preserved.append(float(cw["temperature"]))
        if cw.get("humidity") is not None:
            lines.append(f"• Relative Humidity: {cw['humidity']}%")
            preserved.append(float(cw["humidity"]))
        if cw.get("windSpeed") is not None:
            lines.append(f"• Wind Speed: {cw['windSpeed']} km/h")
            preserved.append(float(cw["windSpeed"]))
        lines.append(f"• Source: {cw.get('source', 'Open-Meteo')} (Observed at {cw.get('observationTime', 'recent')})\n")
    elif cw and isinstance(cw, dict):
        lines.append(f"Current Weather: Observation data currently unavailable for {cw.get('locationName', location_name)}.\n")
        if cw.get("limitations"):
            limitations.extend(cw["limitations"])

    wf = tool_results.get("weatherForecast")
    if wf and isinstance(wf, dict) and wf.get("status") == "VALID":
        lines.append(f"Weather Forecast for {wf.get('locationName', location_name)} ({wf.get('forecastTimeRange', 'Next 6-24 Hours')}):")
        if wf.get("temperature") is not None:
            lines.append(f"• Expected Average Temperature: {wf['temperature']}°C")
            preserved.append(float(wf["temperature"]))
        if wf.get("precipitation") is not None:
            lines.append(f"• Max Expected Precipitation: {wf['precipitation']} mm")
            preserved.append(float(wf["precipitation"]))
        if wf.get("windSpeed") is not None:
            lines.append(f"• Expected Wind Speed: {wf['windSpeed']} km/h")
            preserved.append(float(wf["windSpeed"]))
        lines.append(f"• Source: {wf.get('source', 'Open-Meteo Forecast')}")
        lines.append("• Note: Forecast derived from numerical meteorological simulations; distinct from current ground observations.\n")

    aq = tool_results.get("airQuality")
    if aq and isinstance(aq, dict) and aq.get("status") == "VALID":
        lines.append(f"Air Quality in {aq.get('locationName', location_name)}:")
        if aq.get("pm25") is not None:
            lines.append(f"• PM2.5 Concentration: {aq['pm25']} µg/m³")
            preserved.append(float(aq["pm25"]))
        if aq.get("pm10") is not None:
            lines.append(f"• PM10 Concentration: {aq['pm10']} µg/m³")
            preserved.append(float(aq["pm10"]))
        lines.append(f"• Monitoring Station: {aq.get('stationName') or aq.get('stationCode', 'Reference Monitor')}")
        lines.append(f"• Freshness: {aq.get('freshness', 'LIVE')} (Source: {aq.get('source', 'CPCB / OpenAQ')})\n")
    elif aq and isinstance(aq, dict):
        lines.append(f"Air Quality: {aq.get('limitations', ['No telemetry available'])[0] if aq.get('limitations') else 'No recent measurements.'}\n")

    ow = tool_results.get("officialWarnings")
    if ow and isinstance(ow, dict):
        lines.append("Official Government Disaster Warnings (IMD / NDMA SACHET):")
        status = ow.get("status")
        if status == "VERIFIED_WARNINGS_FOUND" and ow.get("warnings"):
            for w in ow["warnings"]:
                lines.append(f"• [{w.get('severity', 'WARNING')}] {w.get('headline')} (Type: {w.get('warningType')}, Authority: {w.get('issuingAuthority')})")
        elif status == "NO_ACTIVE_WARNINGS":
            lines.append("• No active official weather or disaster warnings issued for this area at this time.")
        else:
            lines.append("• Official warning feeds (IMD / NDMA SACHET) are currently unreachable or unconfigured for automated polling. Please consult official portals directly: https://mausam.imd.gov.in and https://sachet.ndma.gov.in.")
        lines.append("")

    pr = tool_results.get("pm25Risk")
    if pr and isinstance(pr, dict):
        if pr.get("status") == "MODEL_DOMAIN_UNSUPPORTED":
            lines.append("PM2.5 ML Model Risk: Unavailable. Hotspot and forecasting ML models are currently calibrated strictly for the Pune Metropolitan Region. Observations are available, but ML hotspot risk is unsupported outside Pune.\n")
        elif pr.get("status") == "VALID":
            lines.append("PM2.5 Risk & Forecast (Pune Domain):")
            if pr.get("riskLevel"):
                lines.append(f"• Hotspot Risk Level: {pr['riskLevel']} (Model probability: {pr.get('riskScore', 0.0):.2f})")
                if pr.get("riskScore") is not None:
                    preserved.append(float(pr["riskScore"]))
            if pr.get("forecastHorizons"):
                lines.append("• Multi-Horizon Predicted PM2.5 (Experimental ML):")
                for h in pr["forecastHorizons"]:
                    lines.append(f"  - +{h['horizonHours']}h: {h['predictedPm25']} µg/m³ [Range: {h['lowerBound']} – {h['upperBound']} µg/m³]")
                    preserved.append(float(h["predictedPm25"]))
                lines.append("• Notice: Random Forest forecasters are experimental. Holdout benchmarks show naive persistence baseline is superior on short lead times.\n")

    if not lines:
        lines.append(f"Thank you for contacting AeroSentinel. We provide verified weather observations, air quality telemetry, official warnings, and PM2.5 hazard estimates for {location_name}.")

    return {
        "answer": "\n".join(lines).strip(),
        "language": lang,
        "status": "SUCCESS",
        "generator": "deterministic_fallback",
        "preserved_numerical_values": preserved,
        "limitations": limitations
    }


def _build_hindi_synthesis(tool_results: Dict[str, Any], location_name: str) -> Dict[str, Any]:
    lines = []
    preserved = []
    cw = tool_results.get("currentWeather")
    if cw and isinstance(cw, dict) and cw.get("status") == "VALID":
        lines.append(f"{cw.get('locationName', location_name)} में वर्तमान मौसम:")
        if cw.get("temperature") is not None:
            lines.append(f"• तापमान: {cw['temperature']}°C")
            preserved.append(float(cw["temperature"]))
        if cw.get("humidity") is not None:
            lines.append(f"• सापेक्ष आर्द्रता: {cw['humidity']}%")
            preserved.append(float(cw["humidity"]))
        lines.append(f"• स्रोत: {cw.get('source', 'Open-Meteo')}\n")

    aq = tool_results.get("airQuality")
    if aq and isinstance(aq, dict) and aq.get("status") == "VALID":
        lines.append(f"{aq.get('locationName', location_name)} में वायु गुणवत्ता:")
        if aq.get("pm25") is not None:
            lines.append(f"• PM2.5 सघनता: {aq['pm25']} µg/m³")
            preserved.append(float(aq["pm25"]))
        lines.append(f"• निगरानी स्टेशन: {aq.get('stationName') or aq.get('stationCode')}\n")

    ow = tool_results.get("officialWarnings")
    if ow and isinstance(ow, dict):
        lines.append("सरकारी आधिकारिक चेतावनियाँ (IMD / NDMA):")
        if ow.get("status") == "NO_ACTIVE_WARNINGS":
            lines.append("• वर्तमान में इस क्षेत्र के लिए कोई सक्रिय आपदा या मौसम चेतावनी नहीं है।\n")
        else:
            lines.append("• कृपया आधिकारिक पोर्टल देखें: https://mausam.imd.gov.in\n")

    if not lines:
        lines.append(f"एयरोसेंटिनल में आपका स्वागत है। हम सत्यापित मौसम तथा वायु गुणवत्ता डेटा प्रदान करते हैं।")

    return {
        "answer": "\n".join(lines).strip(),
        "language": "hi",
        "status": "SUCCESS",
        "generator": "deterministic_fallback",
        "preserved_numerical_values": preserved,
        "limitations": []
    }


def _build_marathi_synthesis(tool_results: Dict[str, Any], location_name: str) -> Dict[str, Any]:
    lines = []
    preserved = []
    cw = tool_results.get("currentWeather")
    if cw and isinstance(cw, dict) and cw.get("status") == "VALID":
        lines.append(f"{cw.get('locationName', location_name)} येथील सध्याचे हवामान:")
        if cw.get("temperature") is not None:
            lines.append(f"• तापमान: {cw['temperature']}°C")
            preserved.append(float(cw["temperature"]))
        if cw.get("humidity") is not None:
            lines.append(f"• हवेतील आर्द्रता: {cw['humidity']}%")
            preserved.append(float(cw["humidity"]))
        lines.append(f"• स्त्रोत: {cw.get('source', 'Open-Meteo')}\n")

    aq = tool_results.get("airQuality")
    if aq and isinstance(aq, dict) and aq.get("status") == "VALID":
        lines.append(f"{aq.get('locationName', location_name)} येथील हवेची गुणवत्ता:")
        if aq.get("pm25") is not None:
            lines.append(f"• PM2.5 प्रमाण: {aq['pm25']} µg/m³")
            preserved.append(float(aq["pm25"]))
        lines.append(f"• केंद्र: {aq.get('stationName') or aq.get('stationCode')}\n")

    ow = tool_results.get("officialWarnings")
    if ow and isinstance(ow, dict):
        lines.append("शासकीय अधिकृत इशारे (IMD / NDMA):")
        if ow.get("status") == "NO_ACTIVE_WARNINGS":
            lines.append("• या क्षेत्रासाठी सध्या कोणताही सक्रिय शासकीय इशारा नाही.\n")
        else:
            lines.append("• अधिकृत संकेतस्थळास भेट द्या: https://mausam.imd.gov.in\n")

    if not lines:
        lines.append(f"एयरोसेंटिनेल मध्ये आपले स्वागत आहे.")

    return {
        "answer": "\n".join(lines).strip(),
        "language": "mr",
        "status": "SUCCESS",
        "generator": "deterministic_fallback",
        "preserved_numerical_values": preserved,
        "limitations": []
    }


def synthesize_assistant_turn(
    user_message: str,
    tool_results: Dict[str, Any],
    locale: str = "en",
    location_name: str = "Local Area"
) -> Dict[str, Any]:
    """
    Synthesizes an assistant turn using Gemini when configured, or deterministic fallback.
    Performs numerical preservation verification to prevent model hallucination.
    """
    facts = extract_critical_facts(tool_results)

    if gemini_client.is_configured():
        try:
            user_content = (
                f"User Question: {user_message}\n"
                f"Location: {location_name}\n"
                f"Preferred Language: {locale}\n"
                f"Tool Execution Results:\n{json.dumps(tool_results, indent=2)}"
            )

            response = gemini_client.generate_structured_json(
                contents=user_content,
                system_instruction=ASSISTANT_SYSTEM_INSTRUCTION,
                max_retries=1
            )

            if isinstance(response, dict) and response.get("answer"):
                answer = response["answer"].strip()
                preserved_numbers = response.get("preserved_numerical_values", [])

                # Strict numerical verification: ensure no fabricated metrics
                if verify_numerical_claims(answer, facts):
                    return {
                        "answer": answer,
                        "language": locale,
                        "status": "SUCCESS",
                        "generator": "gemini-2.0-flash",
                        "preserved_numerical_values": preserved_numbers,
                        "limitations": response.get("limitations", [])
                    }
                else:
                    logger.warning("Gemini output violated numerical preservation! Reverting to deterministic synthesis.")
        except Exception as e:
            logger.warning(f"Gemini assistant synthesis failed ({e}). Falling back to deterministic synthesis.")

    # Fallback to deterministic synthesis
    return build_deterministic_synthesis(user_message, tool_results, locale, location_name)
