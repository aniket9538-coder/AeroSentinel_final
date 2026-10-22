import axios from 'axios';
import {
  AdminOverview,
  ServiceHealth,
  ProviderObservability,
  ModelObservability,
  SafeAuditEvent,
} from '../types/admin';

const getBaseUrl = (): string => {
  const envUrl = import.meta.env.VITE_API_BASE_URL;
  if (envUrl && typeof envUrl === 'string' && envUrl.trim().length > 0) {
    return envUrl.trim().replace(/\/+$/, '');
  }
  return 'http://localhost:8080';
};

const adminClient = axios.create({
  baseURL: getBaseUrl(),
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

adminClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('aerosentinel_jwt') || localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const fetchAdminOverview = async (): Promise<AdminOverview> => {
  const response = await adminClient.get<AdminOverview>('/api/v1/admin/observability/overview');
  return response.data;
};

export const fetchServiceHealth = async (): Promise<ServiceHealth> => {
  const response = await adminClient.get<ServiceHealth>('/api/v1/admin/observability/services');
  return response.data;
};

export const fetchProviderObservability = async (): Promise<ProviderObservability> => {
  const response = await adminClient.get<ProviderObservability>('/api/v1/admin/observability/providers');
  return response.data;
};

export const fetchModelObservability = async (): Promise<ModelObservability> => {
  const response = await adminClient.get<ModelObservability>('/api/v1/admin/observability/models');
  return response.data;
};

export const fetchSafeAuditEvents = async (): Promise<SafeAuditEvent[]> => {
  const response = await adminClient.get<SafeAuditEvent[]>('/api/v1/admin/observability/audit-events');
  return response.data;
};
