import test from 'node:test';
import assert from 'node:assert/strict';
import { AssistantChatRequest, AssistantChatResponse, SourceReferenceDto } from '../types/assistant';
import { assistantService } from '../services/assistant.service';

test('Assistant Contract - Request format validation', () => {
  const req: AssistantChatRequest = {
    message: 'What is the air quality in Shivajinagar Pune right now?',
    location: {
      cityName: 'Pune',
      latitude: 18.5314,
      longitude: 73.8446,
      h3Index: '8860145b59fffff'
    },
    locale: 'en'
  };

  assert.equal(req.message.length > 0, true);
  assert.equal(req.location?.cityName, 'Pune');
  assert.equal(req.locale, 'en');
});

test('Assistant Contract - Response schema and status states', () => {
  const mockSource: SourceReferenceDto = {
    provider: 'CPCB',
    stationName: 'Shivajinagar CAAQMS',
    stationCode: 'PUN-001',
    freshness: 'LIVE',
    dataType: 'PM2.5 ground observation'
  };

  const response: AssistantChatResponse = {
    answer: 'The latest PM2.5 reading at Shivajinagar CAAQMS is 52.4 ug/m3.',
    language: 'en',
    sources: [mockSource],
    observationPeriod: 'Last 60 minutes',
    status: 'SUCCESS',
    limitations: ['Official warning feeds are unavailable for automated ingestion.'],
    correlationId: 'req-test-12345',
    timestamp: '2026-10-18T18:00:00Z'
  };

  assert.equal(response.status, 'SUCCESS');
  assert.equal(response.sources.length, 1);
  assert.equal(response.sources[0].provider, 'CPCB');
  assert.equal(response.sources[0].freshness, 'LIVE');
  assert.equal(response.limitations.length, 1);
  assert.equal(response.correlationId, 'req-test-12345');
});

test('Assistant Contract - Service object structure', () => {
  assert.equal(typeof assistantService.chat, 'function');
  assert.equal(typeof assistantService.checkHealth, 'function');
});
