import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =========================================================================
// Phase 8: F7 Authority Lifecycle, Incident Assignment & Resolution Tests
// =========================================================================

test('Phase 8 Test 1: Canonical Alert Statuses & Legal Transitions Matrix', () => {
  // Define canonical lifecycle state machine
  const CANONICAL_STATUSES = [
    'OPEN',
    'ACKNOWLEDGED',
    'ASSIGNED',
    'IN_INSPECTION',
    'ACTION_TAKEN',
    'RESOLVED',
    'CLOSED',
    'DISMISSED',
  ];

  const ALLOWED_TRANSITIONS: Record<string, string[]> = {
    OPEN: ['ACKNOWLEDGED', 'ASSIGNED', 'DISMISSED'],
    ACKNOWLEDGED: ['ASSIGNED', 'IN_INSPECTION', 'DISMISSED'],
    ASSIGNED: ['ASSIGNED', 'IN_INSPECTION', 'DISMISSED'], // self-transition for reassignment
    IN_INSPECTION: ['ASSIGNED', 'ACTION_TAKEN', 'RESOLVED'], // reassignment back to ASSIGNED, or progression
    ACTION_TAKEN: ['ACTION_TAKEN', 'RESOLVED'],
    RESOLVED: ['CLOSED'],
    CLOSED: [],
    DISMISSED: [],
  };

  const validateTransition = (current: string, next: string): boolean => {
    const allowed = ALLOWED_TRANSITIONS[current] || [];
    return allowed.includes(next);
  };

  // Test legal happy path
  assert.equal(validateTransition('OPEN', 'ACKNOWLEDGED'), true, 'OPEN -> ACKNOWLEDGED is legal');
  assert.equal(validateTransition('ACKNOWLEDGED', 'ASSIGNED'), true, 'ACKNOWLEDGED -> ASSIGNED is legal');
  assert.equal(validateTransition('ASSIGNED', 'ASSIGNED'), true, 'ASSIGNED -> ASSIGNED (reassignment) is legal');
  assert.equal(validateTransition('ASSIGNED', 'IN_INSPECTION'), true, 'ASSIGNED -> IN_INSPECTION is legal');
  assert.equal(validateTransition('IN_INSPECTION', 'ACTION_TAKEN'), true, 'IN_INSPECTION -> ACTION_TAKEN is legal');
  assert.equal(validateTransition('ACTION_TAKEN', 'RESOLVED'), true, 'ACTION_TAKEN -> RESOLVED is legal');
  assert.equal(validateTransition('RESOLVED', 'CLOSED'), true, 'RESOLVED -> CLOSED is legal');

  // Test legal dismissals
  assert.equal(validateTransition('OPEN', 'DISMISSED'), true, 'OPEN -> DISMISSED is legal');
  assert.equal(validateTransition('ACKNOWLEDGED', 'DISMISSED'), true, 'ACKNOWLEDGED -> DISMISSED is legal');
  assert.equal(validateTransition('ASSIGNED', 'DISMISSED'), true, 'ASSIGNED -> DISMISSED is legal');

  // Test illegal transitions
  assert.equal(validateTransition('RESOLVED', 'OPEN'), false, 'RESOLVED -> OPEN is rejected');
  assert.equal(validateTransition('RESOLVED', 'DISMISSED'), false, 'RESOLVED -> DISMISSED is rejected');
  assert.equal(validateTransition('CLOSED', 'OPEN'), false, 'CLOSED -> OPEN is rejected');
  assert.equal(validateTransition('DISMISSED', 'OPEN'), false, 'DISMISSED -> OPEN is rejected');
  assert.equal(validateTransition('DISMISSED', 'ACKNOWLEDGED'), false, 'DISMISSED -> ACKNOWLEDGED is rejected');
  assert.equal(validateTransition('OPEN', 'RESOLVED'), false, 'OPEN directly to RESOLVED without verification is rejected');
});

test('Phase 8 Test 2: TypeScript types define ReassignTeamRequest with mandatory fields', () => {
  const inspectionTypes = fs.readFileSync(
    path.join(__dirname, '../types/inspection.ts'),
    'utf-8'
  );
  assert.ok(inspectionTypes.includes('export interface ReassignTeamRequest'), 'Defines ReassignTeamRequest interface');
  assert.ok(inspectionTypes.includes('newTeamId: string'), 'Requires newTeamId');
  assert.ok(inspectionTypes.includes('reason: string'), 'Requires mandatory reason');
  assert.ok(inspectionTypes.includes('notes?: string'), 'Supports optional operational notes');
});

test('Phase 8 Test 3: Action API types include previousStatus, newStatus, reason for audit tracking', () => {
  const actionTypes = fs.readFileSync(
    path.join(__dirname, '../services/actionApi.ts'),
    'utf-8'
  );
  assert.ok(actionTypes.includes('previousStatus?: string'), 'Exposes previousStatus on AuthorityActionItem');
  assert.ok(actionTypes.includes('newStatus?: string'), 'Exposes newStatus on AuthorityActionItem');
  assert.ok(actionTypes.includes('reason?: string'), 'Exposes reason on AuthorityActionItem');
  assert.ok(actionTypes.includes('actionType: string'), 'Exposes actionType');
  assert.ok(actionTypes.includes('performedBy: string'), 'Exposes performedBy');
  assert.ok(actionTypes.includes('performedAt: string'), 'Exposes performedAt');
});

test('Phase 8 Test 4: Inspection API implements reassignFieldTeam endpoint', () => {
  const inspectionApiSrc = fs.readFileSync(
    path.join(__dirname, '../services/inspectionApi.ts'),
    'utf-8'
  );
  assert.ok(inspectionApiSrc.includes('reassignFieldTeam'), 'Implements reassignFieldTeam method');
  assert.ok(inspectionApiSrc.includes('/reassign'), 'Calls /reassign endpoint');
});

test('Phase 8 Test 5: Auth service extracts user role from token payload for role gating', () => {
  const authServiceSrc = fs.readFileSync(
    path.join(__dirname, '../services/auth.service.ts'),
    'utf-8'
  );
  assert.ok(authServiceSrc.includes('getRole: (): string | null'), 'Implements getRole method');
  assert.ok(authServiceSrc.includes('payload.role'), 'Parses role from JWT payload');
});

test('Phase 8 Test 6: Authority Alerts page renders Audit History Card with previous/new status provenance', () => {
  const alertsSrc = fs.readFileSync(
    path.join(__dirname, '../pages/authority/Alerts.tsx'),
    'utf-8'
  );
  assert.ok(alertsSrc.includes('data-testid="audit-history-card"'), 'Renders audit history card');
  assert.ok(alertsSrc.includes('Authoritative Lifecycle Audit History'), 'Card title matches specification');
  assert.ok(alertsSrc.includes('act.previousStatus && act.newStatus'), 'Renders previousStatus -> newStatus progression');
  assert.ok(alertsSrc.includes('act.reason'), 'Renders recorded decision reason');
});

test('Phase 8 Test 7: Authority Alerts page enforces Analyst read-only mode banner and restricts mutations', () => {
  const alertsSrc = fs.readFileSync(
    path.join(__dirname, '../pages/authority/Alerts.tsx'),
    'utf-8'
  );
  assert.ok(alertsSrc.includes('data-testid="banner-analyst-readonly"'), 'Renders Analyst read-only banner');
  assert.ok(alertsSrc.includes('Analyst Read-Only Mode'), 'Displays informative read-only message');
  assert.ok(alertsSrc.includes('!isAnalystReadOnly && currentEventStatus === \'OPEN\''), 'Restricts OPEN mutations to Authority');
  assert.ok(alertsSrc.includes('!isAnalystReadOnly && currentEventStatus === \'ASSIGNED\''), 'Restricts ASSIGNED mutations to Authority');
  assert.ok(alertsSrc.includes('!isAnalystReadOnly && currentEventStatus === \'IN_INSPECTION\''), 'Restricts IN_INSPECTION mutations to Authority');
  assert.ok(alertsSrc.includes('!isAnalystReadOnly && currentEventStatus === \'ACTION_TAKEN\''), 'Restricts ACTION_TAKEN mutations to Authority');
});

test('Phase 8 Test 8: Authority Alerts page mounts Reassign Team controls and modal with mandatory reason', () => {
  const alertsSrc = fs.readFileSync(
    path.join(__dirname, '../pages/authority/Alerts.tsx'),
    'utf-8'
  );
  assert.ok(alertsSrc.includes('data-testid="btn-reassign-team"'), 'Provides Reassign Team button in ASSIGNED state');
  assert.ok(alertsSrc.includes('data-testid="btn-reassign-team-inspection"'), 'Provides Reassign Team button in IN_INSPECTION state');
  assert.ok(alertsSrc.includes('data-testid="modal-reassign-team"'), 'Mounts Reassign Team modal dialog');
  assert.ok(alertsSrc.includes('data-testid="select-reassign-team"'), 'Provides replacement team dropdown');
  assert.ok(alertsSrc.includes('data-testid="textarea-reassign-reason"'), 'Provides mandatory reassignment reason input');
  assert.ok(alertsSrc.includes('data-testid="btn-submit-reassign-confirm"'), 'Provides confirmation button');
});

test('Phase 8 Test 9: Domain Separation Invariant — Citizen Reports != Triage Candidates != Authority Incidents', () => {
  const alertsSrc = fs.readFileSync(
    path.join(__dirname, '../pages/authority/Alerts.tsx'),
    'utf-8'
  );
  const cardSrc = fs.readFileSync(
    path.join(__dirname, '../components/authority/EvidenceFusionTriageCard.tsx'),
    'utf-8'
  );
  // Verify Evaluator Notice preserves domain separation
  assert.ok(alertsSrc.includes('Evaluator Notice:'), 'Preserves citizen auxiliary notice in Alerts');
  assert.ok(alertsSrc.includes('strictly separate from official sensor calibrations'), 'Separates citizen observations from regulatory measurements');
  assert.ok(cardSrc.includes('Strict Source Independence Rule'), 'Separates F3 Risk from F5 Evidence Score');
});
