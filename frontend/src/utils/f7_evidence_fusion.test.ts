import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =========================================================================
// Phase 7: F5 Evidence Fusion, Corroboration & Explainable Triage Test Suite
// =========================================================================

test('Phase 7 Test 1: TypeScript types define the 6 normalized evidence classes', () => {
  const typesSrc = fs.readFileSync(
    path.join(__dirname, '../types/evidence.ts'),
    'utf-8'
  );
  assert.ok(typesSrc.includes('CITIZEN_SUBMITTED'), 'Defines CITIZEN_SUBMITTED evidence class');
  assert.ok(typesSrc.includes('CITIZEN_VISION'), 'Defines CITIZEN_VISION evidence class');
  assert.ok(typesSrc.includes('ENVIRONMENTAL_MEASUREMENT'), 'Defines ENVIRONMENTAL_MEASUREMENT evidence class');
  assert.ok(typesSrc.includes('WEATHER_MODEL_CONTEXT'), 'Defines WEATHER_MODEL_CONTEXT evidence class');
  assert.ok(typesSrc.includes('OFFICIAL_WARNING'), 'Defines OFFICIAL_WARNING evidence class');
  assert.ok(typesSrc.includes('EXPERIMENTAL_ML_OUTPUT'), 'Defines EXPERIMENTAL_ML_OUTPUT evidence class');
});

test('Phase 7 Test 2: TriageCandidateDto enforces source independence & metric separation contracts', () => {
  const typesSrc = fs.readFileSync(
    path.join(__dirname, '../types/evidence.ts'),
    'utf-8'
  );
  assert.ok(typesSrc.includes('independentSourceCount: number'), 'Exposes independentSourceCount');
  assert.ok(typesSrc.includes('relatedSourceCount: number'), 'Exposes relatedSourceCount');
  assert.ok(typesSrc.includes('isProvisional: boolean'), 'Exposes isProvisional flag');
  assert.ok(typesSrc.includes('evidenceScore: number'), 'Preserves dedicated evidenceScore');
  assert.ok(typesSrc.includes('riskScore?: number | null'), 'Preserves dedicated riskScore');
  assert.ok(typesSrc.includes('visualConfidence?: number | null'), 'Preserves dedicated visualConfidence');
  assert.ok(typesSrc.includes('policyVersion: string'), 'Exposes policyVersion');
});

test('Phase 7 Test 3: Evidence API client provides getTriageCandidate and fuseAndPersist', () => {
  const apiSrc = fs.readFileSync(
    path.join(__dirname, '../services/evidenceApi.ts'),
    'utf-8'
  );
  assert.ok(apiSrc.includes('getTriageCandidate'), 'Implements getTriageCandidate');
  assert.ok(apiSrc.includes('/evidence/triage/h3/'), 'Calls /evidence/triage/h3/{h3Index}');
  assert.ok(apiSrc.includes('fuseAndPersist'), 'Implements fuseAndPersist');
  assert.ok(apiSrc.includes('/evidence/triage/fuse'), 'Calls /evidence/triage/fuse');
});

test('Phase 7 Test 4: EvidenceFusionTriageCard displays 12 Safety Invariants & Source Independence rules', () => {
  const cardSrc = fs.readFileSync(
    path.join(__dirname, '../components/authority/EvidenceFusionTriageCard.tsx'),
    'utf-8'
  );
  assert.ok(cardSrc.includes('Strict Source Independence Rule'), 'Displays source independence rule');
  assert.ok(cardSrc.includes('PROVISIONAL TRIAGE CANDIDATE — AUXILIARY OBSERVATION'), 'Renders provisional observation banner');
  assert.ok(cardSrc.includes('Never establishes confirmed violations or high operational alerts alone'), 'Enforces Safety Invariant 1 in UI');
  assert.ok(cardSrc.includes('F5 Evidence Score'), 'Renders F5 Evidence Score');
  assert.ok(cardSrc.includes('F3 ML Risk Score'), 'Renders F3 ML Risk Score');
  assert.ok(cardSrc.includes('Visual AI Confidence'), 'Renders Visual AI Confidence');
  assert.ok(cardSrc.includes('Independent Sources'), 'Renders Independent Sources count');
});

test('Phase 7 Test 5: Authority Alerts page mounts F5 Evidence Fusion card', () => {
  const alertsSrc = fs.readFileSync(
    path.join(__dirname, '../pages/authority/Alerts.tsx'),
    'utf-8'
  );
  assert.ok(alertsSrc.includes('EvidenceFusionTriageCard'), 'Imports EvidenceFusionTriageCard');
  assert.ok(alertsSrc.includes('<EvidenceFusionTriageCard'), 'Mounts EvidenceFusionTriageCard in detail view');
  assert.ok(alertsSrc.includes('h3Index={selectedAlert.h3Index}'), 'Passes active H3 index to EvidenceFusionTriageCard');
});

test('Phase 7 Test 6: Excluded evidence explains out-of-scope reasons (Stale, Window Exceeded, Coalesced)', () => {
  const cardSrc = fs.readFileSync(
    path.join(__dirname, '../components/authority/EvidenceFusionTriageCard.tsx'),
    'utf-8'
  );
  assert.ok(cardSrc.includes('Excluded Evidence'), 'Provides collapsible excluded evidence view');
  assert.ok(cardSrc.includes('reasonCode'), 'Displays reason code');
  assert.ok(cardSrc.includes('showExcluded'), 'Toggles excluded evidence breakdown');
});
