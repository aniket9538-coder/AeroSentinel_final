/**
 * AeroSentinel - Assistant Conversational Types (Phase 4)
 * File: frontend/src/types/assistant.ts
 */

export interface LocationDto {
  cityName?: string;
  latitude?: number;
  longitude?: number;
  h3Index?: string;
}

export interface SourceReferenceDto {
  provider: string;
  stationName?: string;
  stationCode?: string;
  url?: string;
  timestamp?: string;
  freshness?: 'LIVE' | 'STALE' | 'NO_DATA' | 'SOURCE_UNAVAILABLE';
  dataType?: string;
}

export interface AssistantChatRequest {
  message: string;
  location?: LocationDto;
  locale?: string;
}

export interface AssistantChatResponse {
  answer: string;
  language: string;
  sources: SourceReferenceDto[];
  observationPeriod?: string;
  status: 'SUCCESS' | 'PARTIAL_DATA' | 'NO_DATA' | 'UNAVAILABLE' | 'NEEDS_CLARIFICATION';
  limitations: string[];
  correlationId: string;
  timestamp: string;
  clarificationPrompt?: string;
}

export interface OfficialWarningItem {
  warningId: string;
  issuingAuthority: string;
  title: string;
  warningType: string;
  severity: 'RED' | 'ORANGE' | 'YELLOW' | 'ADVISORY' | string;
  affectedArea: string;
  issuedAt?: string;
  validFrom?: string;
  validUntil?: string;
  officialReferenceUrl?: string;
}

export interface OfficialWarningToolResult {
  status: 'VERIFIED_WARNINGS_FOUND' | 'NO_ACTIVE_WARNINGS' | 'SOURCE_UNAVAILABLE' | 'LOCATION_NOT_COVERED' | 'UNCONFIGURED' | string;
  locationName: string;
  warnings: OfficialWarningItem[];
  sourceAuthority: string;
  sourceUrl: string;
  retrievedAt?: string;
  limitations: string[];
}

export interface HourlyForecastEntry {
  timestamp: string;
  temperature?: number;
  humidity?: number;
  precipitation?: number;
  windSpeed?: number;
}

export interface WeatherToolResult {
  isForecast: boolean;
  locationName: string;
  latitude?: number;
  longitude?: number;
  temperature?: number;
  temperatureUnit?: string;
  humidity?: number;
  humidityUnit?: string;
  windSpeed?: number;
  windSpeedUnit?: string;
  windDirection?: number;
  precipitation?: number;
  precipitationUnit?: string;
  surfacePressure?: number;
  observationTime?: string;
  forecastTimeRange?: string;
  hourlyForecasts?: HourlyForecastEntry[];
  source: string;
  freshness: string;
  status: string;
  limitations: string[];
}

export interface AirQualityToolResult {
  locationName: string;
  stationName?: string;
  stationCode?: string;
  pm25?: number;
  pm25Unit?: string;
  observedAt?: string;
  freshness: 'LIVE' | 'STALE' | 'NO_DATA' | 'SOURCE_UNAVAILABLE' | string;
  source: string;
  status: string;
  limitations: string[];
}

export interface PM25RiskToolResult {
  isExperimental: boolean;
  locationName: string;
  h3Index?: string;
  hazardLevel?: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL' | string;
  hazardConfidence?: number | null;
  baselineDisclosure?: string;
  status: string;
  limitations: string[];
}

