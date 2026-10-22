import apiClient from './api';
import {
  AssistantChatRequest,
  AssistantChatResponse,
  LocationDto,
  OfficialWarningToolResult,
  WeatherToolResult,
  AirQualityToolResult,
  PM25RiskToolResult,
} from '../types/assistant';

export const assistantService = {
  chat: async (request: AssistantChatRequest): Promise<AssistantChatResponse> => {
    const response = await apiClient.post<AssistantChatResponse>('/assistant/chat', request);
    return response.data;
  },

  checkHealth: async (): Promise<{ status: string; service: string }> => {
    const response = await apiClient.get<{ status: string; service: string }>('/assistant/health');
    return response.data;
  },

  getWarnings: async (location?: LocationDto): Promise<OfficialWarningToolResult> => {
    const params: Record<string, any> = {};
    if (location?.cityName) params.cityName = location.cityName;
    if (location?.latitude != null) params.latitude = location.latitude;
    if (location?.longitude != null) params.longitude = location.longitude;
    const response = await apiClient.get<OfficialWarningToolResult>('/assistant/warnings', { params });
    return response.data;
  },

  getCurrentWeather: async (location?: LocationDto): Promise<WeatherToolResult> => {
    const params: Record<string, any> = {};
    if (location?.cityName) params.cityName = location.cityName;
    if (location?.latitude != null) params.latitude = location.latitude;
    if (location?.longitude != null) params.longitude = location.longitude;
    const response = await apiClient.get<WeatherToolResult>('/assistant/weather/current', { params });
    return response.data;
  },

  getWeatherForecast: async (location?: LocationDto, timeRange: string = 'Next 72 Hours'): Promise<WeatherToolResult> => {
    const params: Record<string, any> = { timeRange };
    if (location?.cityName) params.cityName = location.cityName;
    if (location?.latitude != null) params.latitude = location.latitude;
    if (location?.longitude != null) params.longitude = location.longitude;
    const response = await apiClient.get<WeatherToolResult>('/assistant/weather/forecast', { params });
    return response.data;
  },

  getAirQuality: async (location?: LocationDto): Promise<AirQualityToolResult> => {
    const params: Record<string, any> = {};
    if (location?.cityName) params.cityName = location.cityName;
    if (location?.latitude != null) params.latitude = location.latitude;
    if (location?.longitude != null) params.longitude = location.longitude;
    const response = await apiClient.get<AirQualityToolResult>('/assistant/air-quality', { params });
    return response.data;
  },

  getPM25Risk: async (location?: LocationDto): Promise<PM25RiskToolResult> => {
    const params: Record<string, any> = {};
    if (location?.cityName) params.cityName = location.cityName;
    if (location?.latitude != null) params.latitude = location.latitude;
    if (location?.longitude != null) params.longitude = location.longitude;
    const response = await apiClient.get<PM25RiskToolResult>('/assistant/pm25-risk', { params });
    return response.data;
  },
};

export default assistantService;

