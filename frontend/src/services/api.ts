import axios from 'axios';

export function normalizeApiBaseUrl(rawUrl?: string): string {
  if (!rawUrl || typeof rawUrl !== 'string') {
    return '/api/v1';
  }
  const trimmed = rawUrl.trim().replace(/\/+$/, '');
  if (!trimmed) {
    return '/api/v1';
  }
  // Collapse any duplicate trailing /api/v1 segments (e.g. /api/v1/api/v1 -> /api/v1)
  if (trimmed.endsWith('/api/v1')) {
    return trimmed.replace(/(\/api\/v1)+$/, '/api/v1');
  }
  // If user provided base ending in /api (e.g. https://host/api), append /v1
  if (trimmed.endsWith('/api')) {
    return `${trimmed}/v1`;
  }
  // Otherwise append /api/v1
  return `${trimmed}/api/v1`;
}

export function resolveApiBaseUrl(): string {
  const envUrl = typeof import.meta !== 'undefined' ? import.meta.env?.VITE_API_BASE_URL : undefined;
  return normalizeApiBaseUrl(envUrl);
}

export const apiClient = axios.create({
  baseURL: resolveApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

apiClient.interceptors.request.use(
  (config) => {
    const token = typeof localStorage !== 'undefined' ? localStorage.getItem('aerosentinel_jwt') : null;
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => {
    // Detect when server returned SPA HTML instead of API JSON (e.g. Vercel SPA rewrite fallback)
    const contentType = response.headers?.['content-type'] || '';
    const isHtml = typeof contentType === 'string' && contentType.includes('text/html');
    const isHtmlBody = typeof response.data === 'string' && response.data.trim().toLowerCase().startsWith('<!doctype html');
    if (isHtml || isHtmlBody) {
      const err = new Error(
        'API_RESPONSE_IS_SPA_HTML: Expected JSON API response but received HTML document. Check VITE_API_BASE_URL and backend routing.'
      );
      (err as any).isSpaHtmlFallback = true;
      (err as any).response = response;
      return Promise.reject(err);
    }
    return response;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      // Handle unauthorized session expiration
    }
    return Promise.reject(error);
  }
);

export default apiClient;
