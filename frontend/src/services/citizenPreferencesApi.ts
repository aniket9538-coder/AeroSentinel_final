import axios from 'axios';
import {
  SavedLocation,
  CreateLocationRequest,
  AlertSubscription,
  CreateSubscriptionRequest,
  InAppNotification,
  AirQualityTrendResponse,
} from '../types/citizenPreferences';

const API_BASE = '/api/v1';

// Get or generate persistent client guest UUID
export const getCitizenGuestId = (): string => {
  let guestId = localStorage.getItem('aerosentinel_guest_id');
  if (!guestId) {
    guestId = 'guest-' + Math.random().toString(36).substring(2, 11) + '-' + Date.now().toString(36);
    localStorage.setItem('aerosentinel_guest_id', guestId);
  }
  return guestId;
};

const getHeaders = () => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Citizen-Id': getCitizenGuestId(),
  };
  const token = localStorage.getItem('aerosentinel_jwt');
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

// --- SAVED LOCATIONS ---
export const fetchSavedLocations = async (): Promise<SavedLocation[]> => {
  const res = await axios.get<SavedLocation[]>(`${API_BASE}/citizen/preferences/locations`, {
    headers: getHeaders(),
  });
  return res.data;
};

export const createSavedLocation = async (req: CreateLocationRequest): Promise<SavedLocation> => {
  const res = await axios.post<SavedLocation>(`${API_BASE}/citizen/preferences/locations`, req, {
    headers: getHeaders(),
  });
  return res.data;
};

export const setDefaultSavedLocation = async (id: string): Promise<SavedLocation> => {
  const res = await axios.put<SavedLocation>(`${API_BASE}/citizen/preferences/locations/${id}/default`, {}, {
    headers: getHeaders(),
  });
  return res.data;
};

export const deleteSavedLocation = async (id: string): Promise<void> => {
  await axios.delete(`${API_BASE}/citizen/preferences/locations/${id}`, {
    headers: getHeaders(),
  });
};

// --- ALERT SUBSCRIPTIONS ---
export const fetchAlertSubscriptions = async (): Promise<AlertSubscription[]> => {
  const res = await axios.get<AlertSubscription[]>(`${API_BASE}/citizen/subscriptions`, {
    headers: getHeaders(),
  });
  return res.data;
};

export const createAlertSubscription = async (req: CreateSubscriptionRequest): Promise<AlertSubscription> => {
  const res = await axios.post<AlertSubscription>(`${API_BASE}/citizen/subscriptions`, req, {
    headers: getHeaders(),
  });
  return res.data;
};

export const deleteAlertSubscription = async (id: string): Promise<void> => {
  await axios.delete(`${API_BASE}/citizen/subscriptions/${id}`, {
    headers: getHeaders(),
  });
};

export const evaluateAlertSubscriptions = async (cityName?: string): Promise<{ subscriptionsEvaluated: number; notificationsTriggered: number }> => {
  const url = cityName ? `${API_BASE}/citizen/subscriptions/evaluate?cityName=${encodeURIComponent(cityName)}` : `${API_BASE}/citizen/subscriptions/evaluate`;
  const res = await axios.post(url, {}, { headers: getHeaders() });
  return res.data;
};

// --- IN-APP NOTIFICATIONS ---
export const fetchInAppNotifications = async (unreadOnly = false): Promise<InAppNotification[]> => {
  const res = await axios.get<InAppNotification[]>(`${API_BASE}/citizen/notifications?unreadOnly=${unreadOnly}`, {
    headers: getHeaders(),
  });
  return res.data;
};

export const markNotificationAsRead = async (id: string): Promise<void> => {
  await axios.put(`${API_BASE}/citizen/notifications/${id}/read`, {}, {
    headers: getHeaders(),
  });
};

// --- HISTORICAL TRENDS & WEATHER HISTORY ---
export const fetchAirQualityTrends = async (cityId: string, stationCode?: string, limit = 50): Promise<AirQualityTrendResponse> => {
  const params = new URLSearchParams({ cityId, limit: String(limit) });
  if (stationCode) params.append('stationCode', stationCode);
  const res = await axios.get<AirQualityTrendResponse>(`${API_BASE}/air/trends?${params.toString()}`);
  return res.data;
};

export const fetchWeatherHistory = async (cityId: string, limit = 50): Promise<any[]> => {
  const res = await axios.get<any[]>(`${API_BASE}/weather/history?cityId=${cityId}&limit=${limit}`);
  return res.data;
};
