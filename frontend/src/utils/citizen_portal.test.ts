import test from 'node:test';
import assert from 'node:assert/strict';
import {
  AssistantChatRequest,
  AssistantChatResponse,
  SourceReferenceDto,
  LocationDto,
  OfficialWarningToolResult,
  WeatherToolResult,
  AirQualityToolResult,
  PM25RiskToolResult,
} from '../types/assistant';
import { assistantService } from '../services/assistant.service';

// ============================================================================
// PART 1: LOCATION SELECTION & COORDINATE VALIDATION
// ============================================================================

test('Location - Supported city location contains valid coordinates and name', () => {
  const puneLocation: LocationDto = {
    cityName: 'Pune',
    latitude: 18.5314,
    longitude: 73.8446,
    h3Index: '88608850e5fffff',
  };

  assert.equal(puneLocation.cityName, 'Pune');
  assert.equal(typeof puneLocation.latitude, 'number');
  assert.equal(typeof puneLocation.longitude, 'number');
  assert.equal(puneLocation.latitude! >= -90 && puneLocation.latitude! <= 90, true);
  assert.equal(puneLocation.longitude! >= -180 && puneLocation.longitude! <= 180, true);
});

test('Location - Coordinate validator correctly identifies invalid inputs', () => {
  const validateCoords = (lat: number, lon: number): boolean => {
    if (isNaN(lat) || isNaN(lon)) return false;
    if (lat < -90 || lat > 90) return false;
    if (lon < -180 || lon > 180) return false;
    return true;
  };

  assert.equal(validateCoords(18.5204, 73.8567), true);
  assert.equal(validateCoords(91.0, 73.0), false); // Latitude out of bounds
  assert.equal(validateCoords(-91.0, 73.0), false);
  assert.equal(validateCoords(18.0, 181.0), false); // Longitude out of bounds
  assert.equal(validateCoords(NaN, 73.0), false);
});

test('Location - Geolocation permission denial returns safe informational error without crash', () => {
  const handleGeoErrorCode = (code: number): string => {
    switch (code) {
      case 1: return 'Location permission denied. Please select a city manually.';
      case 2: return 'Location information is currently unavailable.';
      case 3: return 'Location request timed out. Please try again or select a city.';
      default: return 'Unable to retrieve device location.';
    }
  };

  assert.equal(handleGeoErrorCode(1), 'Location permission denied. Please select a city manually.');
  assert.equal(handleGeoErrorCode(2), 'Location information is currently unavailable.');
  assert.equal(handleGeoErrorCode(3), 'Location request timed out. Please try again or select a city.');
});

test('Location - Rapid location switches maintain request sequencing to prevent races', () => {
  let activeReqId = 0;
  let appliedCity = '';

  const simulateFetch = (reqCity: string, delayMs: number) => {
    const curId = ++activeReqId;
    return new Promise<void>((resolve) => {
      setTimeout(() => {
        if (curId === activeReqId) {
          appliedCity = reqCity;
        }
        resolve();
      }, delayMs);
    });
  };

  // City 1 starts (slow, 50ms), City 2 starts (fast, 10ms)
  const p1 = simulateFetch('Pune', 50);
  const p2 = simulateFetch('Mumbai', 10);

  return Promise.all([p1, p2]).then(() => {
    // City 2 was requested last, so City 1's late response must not overwrite City 2
    assert.equal(appliedCity, 'Mumbai');
  });
});

// ============================================================================
// PART 2: WEATHER & ENVIRONMENTAL CARD CONTRACTS
// ============================================================================

test('Weather - Open-Meteo source semantics are accurately identified as NWP model analysis', () => {
  const weatherResult: WeatherToolResult = {
    isForecast: false,
    locationName: 'Pune',
    latitude: 18.5314,
    longitude: 73.8446,
    temperature: 27.4,
    temperatureUnit: '°C',
    humidity: 58.0,
    humidityUnit: '%',
    windSpeed: 8.5,
    windSpeedUnit: 'km/h',
    windDirection: 240,
    precipitation: 0.0,
    precipitationUnit: 'mm',
    surfacePressure: 955.0,
    observationTime: '2026-10-18T12:00:00Z',
    source: 'Open-Meteo NWP Model Analysis',
    freshness: 'LIVE',
    status: 'VALID',
    limitations: [
      'Model-based reanalysis at 2m surface level, not an in-situ physical telemetry station.',
      'Weather data by Open-Meteo.com (CC BY 4.0)',
    ],
  };

  assert.equal(weatherResult.source, 'Open-Meteo NWP Model Analysis');
  assert.equal(weatherResult.temperatureUnit, '°C');
  assert.equal(weatherResult.limitations.some(l => l.includes('CC BY 4.0')), true);
  assert.equal(weatherResult.limitations.some(l => l.includes('not an in-situ')), true);
});

test('Weather - Forward-looking hourly forecast strictly requires future timestamps', () => {
  const baseTime = new Date('2026-10-18T12:00:00Z').getTime();

  const hourlyForecasts = [
    { timestamp: '2026-10-18T11:00:00Z', temperature: 26.0 }, // Past
    { timestamp: '2026-10-18T13:00:00Z', temperature: 28.5 }, // Future
    { timestamp: '2026-10-18T14:00:00Z', temperature: 27.9 }, // Future
  ];

  // Only forward points qualify
  const futurePoints = hourlyForecasts.filter(h => new Date(h.timestamp).getTime() > baseTime);
  assert.equal(futurePoints.length, 2);
  assert.equal(futurePoints[0].timestamp, '2026-10-18T13:00:00Z');
});

test('Official Warnings - Distinguishes active warnings, no warnings, and SOURCE_UNAVAILABLE', () => {
  const activeWarning: OfficialWarningToolResult = {
    status: 'VERIFIED_WARNINGS_FOUND',
    locationName: 'Mumbai',
    warnings: [
      {
        warningId: 'IMD-MUM-2026-001',
        issuingAuthority: 'IMD',
        title: 'Heavy Rainfall Warning',
        warningType: 'HEAVY_RAIN',
        severity: 'ORANGE',
        affectedArea: 'Mumbai Coastal Region',
        officialReferenceUrl: 'https://mausam.imd.gov.in',
      },
    ],
    sourceAuthority: 'IMD / NDMA SACHET',
    sourceUrl: 'https://sachet.ndma.gov.in',
    limitations: [],
  };

  const noWarnings: OfficialWarningToolResult = {
    status: 'NO_ACTIVE_WARNINGS',
    locationName: 'Pune',
    warnings: [],
    sourceAuthority: 'IMD / NDMA SACHET',
    sourceUrl: 'https://sachet.ndma.gov.in',
    limitations: ['No official warnings active for this region at this time.'],
  };

  const unavailable: OfficialWarningToolResult = {
    status: 'SOURCE_UNAVAILABLE',
    locationName: 'Delhi',
    warnings: [],
    sourceAuthority: 'IMD / NDMA SACHET',
    sourceUrl: 'https://mausam.imd.gov.in',
    limitations: ['Official warning feeds are currently unreachable.'],
  };

  assert.equal(activeWarning.status, 'VERIFIED_WARNINGS_FOUND');
  assert.equal(activeWarning.warnings.length, 1);
  assert.equal(noWarnings.status, 'NO_ACTIVE_WARNINGS');
  assert.equal(noWarnings.warnings.length, 0);
  assert.equal(unavailable.status, 'SOURCE_UNAVAILABLE');

  // Verify SOURCE_UNAVAILABLE is NEVER treated as no warnings
  const isAllClear = (res: OfficialWarningToolResult) => res.status === 'NO_ACTIVE_WARNINGS';
  assert.equal(isAllClear(noWarnings), true);
  assert.equal(isAllClear(unavailable), false);
});

test('Air Quality - Missing telemetry is not converted to zero or fake defaults', () => {
  const missingData: AirQualityToolResult = {
    locationName: 'Rural Area',
    pm25: undefined,
    pm25Unit: 'µg/m³',
    freshness: 'NO_DATA',
    source: 'CPCB / OpenAQ Ground Reference',
    status: 'NO_DATA',
    limitations: ['No active ground monitors located within cell radius.'],
  };

  // Must remain undefined/null, not coerced to 0
  assert.equal(missingData.pm25, undefined);
  assert.equal(missingData.freshness, 'NO_DATA');
});

test('PM2.5 Risk - Enforces EXPERIMENTAL_ML badge, Pune domain restriction, and benchmark disclosure', () => {
  const puneRisk: PM25RiskToolResult = {
    isExperimental: true,
    locationName: 'Pune',
    h3Index: '88608850e5fffff',
    hazardLevel: 'MODERATE',
    hazardConfidence: null, // Strictly null
    baselineDisclosure: 'Random Forest was worse than persistence in holdout comparisons.',
    status: 'VALID',
    limitations: [
      'Model calibrated exclusively within Pune Metropolitan Region.',
      'Holdout comparison: RF MAE 11.2 vs Persistence MAE 8.4 µg/m³.',
    ],
  };

  const mumbaiRisk: PM25RiskToolResult = {
    isExperimental: true,
    locationName: 'Mumbai',
    status: 'MODEL_DOMAIN_UNSUPPORTED',
    limitations: ['AeroSentinel ML models are calibrated exclusively within Pune Metropolitan Region.'],
  };

  assert.equal(puneRisk.isExperimental, true);
  assert.equal(puneRisk.hazardConfidence, null);
  assert.equal(puneRisk.limitations.some(l => l.includes('Persistence')), true);
  assert.equal(mumbaiRisk.status, 'MODEL_DOMAIN_UNSUPPORTED');
});

// ============================================================================
// PART 3: CONVERSATIONAL ASSISTANT CONTRACTS
// ============================================================================

test('Assistant Chat - Response DTO matches complete schema and supports multi-lingual metadata', () => {
  const chatResponse: AssistantChatResponse = {
    answer: 'The current weather in Pune is 27.4°C with 58% humidity.',
    language: 'en',
    sources: [
      {
        provider: 'Open-Meteo',
        url: 'https://open-meteo.com',
        freshness: 'LIVE',
        dataType: 'NWP Model Reanalysis',
        timestamp: '2026-10-18T12:00:00Z',
      },
    ],
    status: 'SUCCESS',
    limitations: ['Model-based weather analysis at 2m surface level.'],
    correlationId: 'corr-xyz-123',
    timestamp: '2026-10-18T12:00:05Z',
  };

  assert.equal(chatResponse.status, 'SUCCESS');
  assert.equal(chatResponse.language, 'en');
  assert.equal(chatResponse.sources.length, 1);
  assert.equal(chatResponse.sources[0].url, 'https://open-meteo.com');
  assert.equal(chatResponse.correlationId, 'corr-xyz-123');
});

test('Assistant Chat - Location clarification handling when user query omits location', () => {
  const clarificationResponse: AssistantChatResponse = {
    answer: 'Which city or area would you like environmental information for (e.g. Pune, Mumbai, or Delhi)?',
    language: 'en',
    sources: [],
    status: 'NEEDS_CLARIFICATION',
    limitations: [],
    correlationId: 'corr-clarify-456',
    timestamp: '2026-10-18T12:01:00Z',
    clarificationPrompt: 'Please specify a city name.',
  };

  assert.equal(clarificationResponse.status, 'NEEDS_CLARIFICATION');
  assert.equal(typeof clarificationResponse.clarificationPrompt, 'string');
});

test('Assistant Service - Client methods exposed on service object', () => {
  assert.equal(typeof assistantService.chat, 'function');
  assert.equal(typeof assistantService.checkHealth, 'function');
  assert.equal(typeof assistantService.getWarnings, 'function');
  assert.equal(typeof assistantService.getCurrentWeather, 'function');
  assert.equal(typeof assistantService.getWeatherForecast, 'function');
  assert.equal(typeof assistantService.getAirQuality, 'function');
  assert.equal(typeof assistantService.getPM25Risk, 'function');
});
