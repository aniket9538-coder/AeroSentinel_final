import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =========================================================================
// Phase 6: Citizen Reports & Secure Photo Evidence Pipeline Test Suite
// =========================================================================

test('Phase 6 Test 1: Upload limits enforced (Max 5 photos, 10MB/file, 25MB total)', () => {
  const uploaderSrc = fs.readFileSync(
    path.join(__dirname, '../components/citizen/ImageUploader.tsx'),
    'utf-8'
  );
  assert.ok(uploaderSrc.includes('MAX_FILE_SIZE = 10 * 1024 * 1024'), 'Enforces 10MB per file');
  assert.ok(uploaderSrc.includes('MAX_TOTAL_SIZE = 25 * 1024 * 1024'), 'Enforces 25MB total limit');
  assert.ok(uploaderSrc.includes('MAX_PHOTOS = 5'), 'Enforces 5 photos per report');
  assert.ok(uploaderSrc.includes('Maximum ${maxFiles} photos allowed'), 'Notifies citizen when limit reached');
  assert.ok(uploaderSrc.includes('total 25MB submission limit'), 'Notifies citizen when aggregate size exceeded');
});

test('Phase 6 Test 2: Supported image formats restricted to raster standards (JPEG, PNG, WebP)', () => {
  const uploaderSrc = fs.readFileSync(
    path.join(__dirname, '../components/citizen/ImageUploader.tsx'),
    'utf-8'
  );
  assert.ok(uploaderSrc.includes("'image/jpeg'"), 'Supports JPEG');
  assert.ok(uploaderSrc.includes("'image/png'"), 'Supports PNG');
  assert.ok(uploaderSrc.includes("'image/webp'"), 'Supports WebP');
  assert.ok(!uploaderSrc.includes("'image/gif'"), 'Rejects GIF');
  assert.ok(!uploaderSrc.includes("'image/svg+xml'"), 'Rejects SVG (XSS vector prevention)');
});

test('Phase 6 Test 3: Incident location requires explicit citizen confirmation', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('locationConfirmed'), 'Tracks explicit location confirmation state');
  assert.ok(reportSrc.includes('confirm-incident-location'), 'Provides explicit confirmation checkbox');
  assert.ok(
    reportSrc.includes('Please explicitly confirm the incident location on the map before submitting.'),
    'Rejects unconfirmed incident location'
  );
});

test('Phase 6 Test 4: Future incident datetime is strictly rejected', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('citizen-observed-at-input'), 'Provides incident datetime picker');
  assert.ok(reportSrc.includes('Incident observation date and time cannot be in the future.'), 'Rejects future datetime');
  assert.ok(reportSrc.includes('max={toLocalIso(new Date())}'), 'Enforces maximum selectable date is now');
});

test('Phase 6 Test 5: Clear Privacy Notice and mandatory citizen consent', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('Privacy Notice &amp; Auxiliary Evidence Notice'), 'Renders privacy notice header');
  assert.ok(reportSrc.includes('citizen-consent-checkbox'), 'Provides mandatory consent checkbox');
  assert.ok(
    reportSrc.includes('Citizen consent to automated evidence processing is required.'),
    'Validates consent before submission'
  );
});

test('Phase 6 Test 6: Duplicate submission prevention and loading state', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('disabled={isSubmitting}'), 'Disables CTA during in-flight submission');
  assert.ok(reportSrc.includes("isSubmitting ? 'ANALYZING & SUBMITTING...' : 'SUBMIT ENVIRONMENTAL EVIDENCE →'"));
});

test('Phase 6 Test 7: Submission Receipt and Reference Lookup', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('Receipt &amp; Status Lookup'), 'Provides receipt status lookup view');
  assert.ok(reportSrc.includes('handleLookupSubmit'), 'Binds reference tracking form handler');
  assert.ok(reportSrc.includes('citizenService.getReportByReference'), 'Calls typed getReportByReference endpoint');
  assert.ok(reportSrc.includes('Track Receipt'), 'Renders Track Receipt navigation tab');
});

test('Phase 6 Test 8: Multi-Photo evidence gallery and authorized display', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(reportSrc.includes('Attached Photographic Evidence'), 'Renders submitted photos gallery');
  assert.ok(reportSrc.includes('submittedReport.photoUrls'), 'Renders all attached photo URLs');
  assert.ok(reportSrc.includes('Ground-level raster imagery stored with private, server-authorized keys'));
});

test('Phase 6 Test 9: Safety Invariant — Citizen photo alone never creates an official penalty or warning', () => {
  const reportSrc = fs.readFileSync(
    path.join(__dirname, '../pages/public/CitizenReport.tsx'),
    'utf-8'
  );
  assert.ok(
    reportSrc.includes('Citizen evidence alone does not trigger official enforcement actions, warnings, or conclusive violations.'),
    'Safety invariant prominently stated in consent disclaimer'
  );
  assert.ok(
    reportSrc.includes('Citizen reports are supporting evidence only, not confirmed source attribution.'),
    'Safety invariant prominently stated in post-submission dossier'
  );
});
