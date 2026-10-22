export interface SavedLocation {
  id: string;
  userId: string;
  label: string;
  cityName: string;
  latitude: number;
  longitude: number;
  h3Index: string;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLocationRequest {
  label: string;
  cityName: string;
  latitude: number;
  longitude: number;
  isDefault?: boolean;
}

export interface AlertSubscription {
  id: string;
  userId: string;
  locationId?: string | null;
  locationLabel?: string | null;
  cityName: string;
  h3Index?: string | null;
  pollutant: string;
  thresholdValue: number;
  deliveryChannel: string;
  enabled: boolean;
  lastTriggeredAt?: string | null;
  createdAt: string;
  channelStatusNote?: string;
}

export interface CreateSubscriptionRequest {
  locationId?: string;
  cityName: string;
  h3Index?: string;
  pollutant: string;
  thresholdValue: number;
  deliveryChannel?: string;
}

export interface InAppNotification {
  id: string;
  userId: string;
  subscriptionId?: string;
  title: string;
  message: string;
  pollutant: string;
  observedValue: number;
  thresholdValue: number;
  triggeringSource: string;
  readStatus: boolean;
  createdAt: string;
}

export interface TrendPoint {
  observedAt: string;
  pm25?: number;
  pm10?: number;
  aqi?: number;
  source: string;
  dataQuality: string;
}

export interface TrendSummary {
  minPm25?: number;
  maxPm25?: number;
  avgPm25?: number;
  latestPm25?: number;
  observationCount: number;
  trendDirection: 'IMPROVING' | 'DETERIORATING' | 'STABLE' | 'NO_DATA';
}

export interface AirQualityTrendResponse {
  cityId: string;
  cityName: string;
  stationCode?: string;
  stationName?: string;
  observations: TrendPoint[];
  summary: TrendSummary;
  honestMissingDataNote: string;
}
