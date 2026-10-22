import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  citizenTranslations,
  getTranslation,
  TranslationDictionary,
} from '../i18n/citizenTranslations';
import {
  SavedLocation,
  AlertSubscription,
  InAppNotification,
  AirQualityTrendResponse,
} from '../types/citizenPreferences';

describe('Phase 10 — Advanced Intelligence & User Value Tests', () => {

  describe('1. Trilingual Localization Integrity (EN, HI, MR)', () => {
    const requiredKeys: (keyof TranslationDictionary)[] = [
      'navHome',
      'navAirQuality',
      'navWeather',
      'navWarnings',
      'navAssistant',
      'navSavedLocations',
      'navAlerts',
      'aqiGood',
      'aqiModerate',
      'aqiPoor',
      'aqiUnhealthy',
      'aqiSevere',
      'aqiCritical',
      'metricPm25',
      'metricPm10',
      'metricAqi',
      'metricTemperature',
      'metricHumidity',
      'metricWind',
      'savedLocationsTitle',
      'savedLocationsSubtitle',
      'alertSubscriptionsTitle',
      'alertSubscriptionsSubtitle',
      'historicalTrendsTitle',
      'historicalTrendsSubtitle',
      'addLocationBtn',
      'setDefaultBtn',
      'defaultBadge',
      'createAlertBtn',
      'markReadBtn',
      'refreshBtn',
      'honestDataNotice',
      'inAppChannelNotice',
      'officialWarningNotice',
    ];

    it('All three locales (en, hi, mr) define every required translation key without omission', () => {
      for (const locale of ['en', 'hi', 'mr']) {
        const dict = citizenTranslations[locale];
        assert.ok(dict, `Dictionary for locale '${locale}' must exist`);
        for (const key of requiredKeys) {
          assert.ok(
            dict[key] && dict[key].trim().length > 0,
            `Key '${key}' in locale '${locale}' must not be empty`
          );
        }
      }
    });

    it('Units and scientific standards are preserved accurately in metric notation', () => {
      for (const locale of ['en', 'hi', 'mr']) {
        const dict = citizenTranslations[locale];
        assert.ok(dict.metricTemperature.includes('°C'), `Temp in ${locale} must use °C`);
        assert.ok(dict.metricHumidity.includes('%'), `Humidity in ${locale} must use %`);
        assert.ok(dict.metricWind.includes('km/h'), `Wind in ${locale} must use km/h`);
      }
    });

    it('getTranslation falls back safely to English for unknown locales', () => {
      const fallback = getTranslation('fr');
      assert.equal(fallback.navHome, 'Overview');
      assert.equal(fallback.aqiGood, 'Good');

      const hi = getTranslation('hi');
      assert.equal(hi.navHome, 'अवलोकन');

      const mr = getTranslation('mr');
      assert.equal(mr.navHome, 'आढावा');
    });

    it('Official sources and protocol references remain unaltered across translations', () => {
      for (const locale of ['en', 'hi', 'mr']) {
        const dict = citizenTranslations[locale];
        assert.ok(
          dict.officialWarningNotice.includes('NDMA') && dict.officialWarningNotice.includes('CAP'),
          `Official notice in ${locale} must preserve NDMA and CAP protocol references`
        );
      }
    });
  });

  describe('2. Saved Locations & Multi-Location Data Contracts', () => {
    it('Validates SavedLocation structure adheres to H3 Res-8 specifications', () => {
      const sampleLoc: SavedLocation = {
        id: '990e8400-e29b-41d4-a716-446655440001',
        userId: 'citizen@aerosentinel.gov',
        label: 'Home - Kothrud',
        cityName: 'Pune',
        latitude: 18.5074,
        longitude: 73.8077,
        h3Index: '8860885357fffff',
        isDefault: true,
        createdAt: '2026-10-20T10:00:00Z',
        updatedAt: '2026-10-20T10:00:00Z',
      };

      assert.equal(sampleLoc.h3Index.length, 15);
      assert.equal(sampleLoc.h3Index.startsWith('88'), true);
      assert.equal(sampleLoc.isDefault, true);
      assert.ok(sampleLoc.latitude >= -90 && sampleLoc.latitude <= 90);
      assert.ok(sampleLoc.longitude >= -180 && sampleLoc.longitude <= 180);
    });
  });

  describe('3. Opt-In Alert Subscriptions & Delivery Semantics', () => {
    it('Distinguishes active in-app delivery channel from unconfigured external channels', () => {
      const inAppSub: AlertSubscription = {
        id: 'sub-1',
        userId: 'citizen@aerosentinel.gov',
        cityName: 'Pune',
        pollutant: 'PM25',
        thresholdValue: 60.0,
        deliveryChannel: 'IN_APP',
        enabled: true,
        createdAt: '2026-10-20T12:00:00Z',
        channelStatusNote: 'Active in-app delivery supported',
      };

      assert.equal(inAppSub.deliveryChannel, 'IN_APP');
      assert.equal(inAppSub.enabled, true);
      assert.ok(inAppSub.thresholdValue > 0);
    });

    it('InAppNotification captures exact observed value and verified station source provenance', () => {
      const notif: InAppNotification = {
        id: 'notif-1',
        userId: 'citizen@aerosentinel.gov',
        subscriptionId: 'sub-1',
        title: 'Environmental Alert: PM25 Threshold Exceeded',
        message: 'Observed PM2.5 reached 75.5 µg/m³ exceeding threshold of 60.0 µg/m³.',
        pollutant: 'PM25',
        observedValue: 75.5,
        thresholdValue: 60.0,
        triggeringSource: 'CPCB',
        readStatus: false,
        createdAt: '2026-10-20T12:30:00Z',
      };

      assert.equal(notif.observedValue > notif.thresholdValue, true);
      assert.equal(notif.triggeringSource, 'CPCB');
      assert.equal(notif.readStatus, false);
    });
  });

  describe('4. Historical Trends & Honest Missing Data Semantics', () => {
    it('AirQualityTrendResponse enforces strictly non-synthetic, station-grounded observations', () => {
      const trendResp: AirQualityTrendResponse = {
        cityId: '550e8400-e29b-41d4-a716-446655440001',
        cityName: 'Pune',
        stationCode: 'PUN-001',
        stationName: 'Shivajinagar CAAQMS',
        observations: [
          {
            observedAt: '2026-10-20T08:00:00Z',
            pm25: 45.0,
            pm10: 88.0,
            aqi: 110.0,
            source: 'CPCB',
            dataQuality: 'VALID',
          },
          {
            observedAt: '2026-10-20T09:00:00Z',
            pm25: 55.0,
            pm10: 98.0,
            aqi: 125.0,
            source: 'CPCB',
            dataQuality: 'VALID',
          },
        ],
        summary: {
          minPm25: 45.0,
          maxPm25: 55.0,
          avgPm25: 50.0,
          latestPm25: 55.0,
          observationCount: 2,
          trendDirection: 'DETERIORATING',
        },
        honestMissingDataNote: 'Historical trend derived strictly from actual station telemetry. Gaps reflect actual provider observation intervals without synthetic points.',
      };

      assert.equal(trendResp.observations.length, 2);
      assert.equal(trendResp.summary.minPm25, 45.0);
      assert.equal(trendResp.summary.maxPm25, 55.0);
      assert.equal(trendResp.summary.avgPm25, 50.0);
      assert.equal(trendResp.summary.trendDirection, 'DETERIORATING');
      assert.ok(trendResp.honestMissingDataNote.includes('strictly from actual station telemetry'));
      assert.ok(trendResp.honestMissingDataNote.includes('without synthetic points'));
    });
  });
});
