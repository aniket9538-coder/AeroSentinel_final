import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeApiBaseUrl, apiClient } from '../services/api';

test('API Base URL: Missing, undefined, or empty string defaults to /api/v1', () => {
  assert.equal(normalizeApiBaseUrl(undefined), '/api/v1');
  assert.equal(normalizeApiBaseUrl(''), '/api/v1');
  assert.equal(normalizeApiBaseUrl('   '), '/api/v1');
});

test('API Base URL: Hostname without /api/v1 appends /api/v1', () => {
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
  assert.equal(
    normalizeApiBaseUrl('http://localhost:8080'),
    'http://localhost:8080/api/v1'
  );
});

test('API Base URL: Trailing slashes are stripped cleanly', () => {
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com///'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
});

test('API Base URL: URL ending in /api appends /v1', () => {
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api/'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
});

test('API Base URL: URL already ending in /api/v1 remains unchanged', () => {
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api/v1'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api/v1/'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
});

test('API Base URL: Accidental duplicate /api/v1 segments are collapsed', () => {
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api/v1/api/v1'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
  assert.equal(
    normalizeApiBaseUrl('https://aerosentinel-backend.onrender.com/api/v1/api/v1/'),
    'https://aerosentinel-backend.onrender.com/api/v1'
  );
});

test('API Client: Interceptor rejects SPA HTML document response with API_RESPONSE_IS_SPA_HTML error', async () => {
  const originalGet = apiClient.get;
  try {
    // Simulate server returning SPA HTML (200 OK text/html)
    const htmlResponse = {
      status: 200,
      headers: { 'content-type': 'text/html; charset=utf-8' },
      data: '<!doctype html><html lang="en"><head><title>AeroSentinel</title></head><body><div id="root"></div></body></html>',
    };

    // Test interceptor directly through response handler
    const responseInterceptor = (apiClient.interceptors.response as any).handlers[0].fulfilled;
    
    await assert.rejects(
      async () => {
        return responseInterceptor(htmlResponse);
      },
      (err: any) => {
        assert.ok(err instanceof Error);
        assert.ok(err.message.includes('API_RESPONSE_IS_SPA_HTML'));
        assert.equal((err as any).isSpaHtmlFallback, true);
        return true;
      }
    );
  } finally {
    apiClient.get = originalGet;
  }
});
