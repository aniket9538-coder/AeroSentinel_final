export interface TranslationDictionary {
  navHome: string;
  navAirQuality: string;
  navWeather: string;
  navWarnings: string;
  navAssistant: string;
  navSavedLocations: string;
  navAlerts: string;

  // AQI Bands
  aqiGood: string;
  aqiModerate: string;
  aqiPoor: string;
  aqiUnhealthy: string;
  aqiSevere: string;
  aqiCritical: string;

  // Metrics (Units preserved strictly in metric notation)
  metricPm25: string;
  metricPm10: string;
  metricAqi: string;
  metricTemperature: string;
  metricHumidity: string;
  metricWind: string;

  // Features
  savedLocationsTitle: string;
  savedLocationsSubtitle: string;
  alertSubscriptionsTitle: string;
  alertSubscriptionsSubtitle: string;
  historicalTrendsTitle: string;
  historicalTrendsSubtitle: string;

  // Actions
  addLocationBtn: string;
  setDefaultBtn: string;
  defaultBadge: string;
  createAlertBtn: string;
  markReadBtn: string;
  refreshBtn: string;

  // Trust notices
  honestDataNotice: string;
  inAppChannelNotice: string;
  officialWarningNotice: string;
}

export const citizenTranslations: Record<string, TranslationDictionary> = {
  en: {
    navHome: 'Overview',
    navAirQuality: 'Air Quality',
    navWeather: 'Weather',
    navWarnings: 'Official Warnings',
    navAssistant: 'AI Assistant',
    navSavedLocations: 'Saved Locations',
    navAlerts: 'Threshold Alerts',

    aqiGood: 'Good',
    aqiModerate: 'Moderate',
    aqiPoor: 'Poor',
    aqiUnhealthy: 'Unhealthy',
    aqiSevere: 'Severe',
    aqiCritical: 'Critical',

    metricPm25: 'PM2.5 (Fine Particulate)',
    metricPm10: 'PM10 (Coarse Particulate)',
    metricAqi: 'Air Quality Index',
    metricTemperature: 'Temperature (°C)',
    metricHumidity: 'Relative Humidity (%)',
    metricWind: 'Wind Speed (km/h)',

    savedLocationsTitle: 'Hyperlocal Saved Locations',
    savedLocationsSubtitle: 'Manage personal residential, workplace, and family monitoring points with H3 Res-8 precision.',
    alertSubscriptionsTitle: 'Opt-In Environmental Threshold Alerts',
    alertSubscriptionsSubtitle: 'Configure verified sensor thresholds and receive reliable in-app notifications without notification spam.',
    historicalTrendsTitle: 'Verified Environmental History & Trends',
    historicalTrendsSubtitle: 'Actual timestamped telemetry from CPCB and OpenAQ monitoring stations. Zero synthetic interpolation.',

    addLocationBtn: 'Add Saved Location',
    setDefaultBtn: 'Set as Default',
    defaultBadge: 'Default',
    createAlertBtn: 'Create Threshold Alert',
    markReadBtn: 'Mark as Read',
    refreshBtn: 'Refresh Data',

    honestDataNotice: 'Historical trends represent genuine station telemetry. Gaps reflect actual provider observation intervals.',
    inAppChannelNotice: 'In-app notification delivery active with safe 1-hour cooldown deduplication.',
    officialWarningNotice: 'Official alerts sourced directly from NDMA Sachet CAP protocol.',
  },

  hi: {
    navHome: 'अवलोकन',
    navAirQuality: 'वायु गुणवत्ता',
    navWeather: 'मौसम',
    navWarnings: 'आधिकारिक चेतावनियाँ',
    navAssistant: 'एआई सहायक',
    navSavedLocations: 'सहेजे गए स्थान',
    navAlerts: 'सीमा अलर्ट',

    aqiGood: 'उत्कृष्ट (Good)',
    aqiModerate: 'मध्यम (Moderate)',
    aqiPoor: 'खराब (Poor)',
    aqiUnhealthy: 'अस्वास्थ्यकर (Unhealthy)',
    aqiSevere: 'गंभीर (Severe)',
    aqiCritical: 'अतिगंभीर (Critical)',

    metricPm25: 'PM2.5 (बारीक कण)',
    metricPm10: 'PM10 (मोटे कण)',
    metricAqi: 'वायु गुणवत्ता सूचकांक (AQI)',
    metricTemperature: 'तापमान (°C)',
    metricHumidity: 'सापेक्ष आर्द्रता (%)',
    metricWind: 'हवा की गति (km/h)',

    savedLocationsTitle: 'सहेजे गए व्यक्तिगत स्थान',
    savedLocationsSubtitle: 'H3 Res-8 सटीकता के साथ अपने घर, कार्यालय और परिवार के निगरानी स्थानों का प्रबंधन करें।',
    alertSubscriptionsTitle: 'सत्यापित सीमा अलर्ट सदस्यता',
    alertSubscriptionsSubtitle: 'वास्तविक सेंसर सीमाओं को कॉन्फ़िगर करें और विश्वसनीय इन-ऐप सूचनाएं प्राप्त करें।',
    historicalTrendsTitle: 'सत्यापित पर्यावरण इतिहास और रुझान',
    historicalTrendsSubtitle: 'CPCB और OpenAQ स्टेशनों से वास्तविक टेलीमेट्री डेटा। कोई कृत्रिम भराव नहीं।',

    addLocationBtn: 'नया स्थान जोड़ें',
    setDefaultBtn: 'डिफ़ॉल्ट बनाएं',
    defaultBadge: 'डिफ़ॉल्ट',
    createAlertBtn: 'नया अलर्ट जोड़ें',
    markReadBtn: 'पढ़ा हुआ चिह्नित करें',
    refreshBtn: 'डेटा रीफ़्रेश करें',

    honestDataNotice: 'ऐतिहासिक रुझान केवल वास्तविक स्टेशन डेटा पर आधारित हैं। कोई कृत्रिम डेटा शामिल नहीं है।',
    inAppChannelNotice: '1 घंटे के सुरक्षित अंतराल के साथ इन-ऐप सूचना वितरण सक्रिय है।',
    officialWarningNotice: 'आधिकारिक चेतावनियाँ सीधे NDMA सचेत (CAP प्रोटोकॉल) से प्राप्त की जाती हैं।',
  },

  mr: {
    navHome: 'आढावा',
    navAirQuality: 'हवेची गुणवत्ता',
    navWeather: 'हवामान',
    navWarnings: 'अधिकृत सूचना',
    navAssistant: 'एआय सहाय्यक',
    navSavedLocations: 'जतन केलेली ठिकाणे',
    navAlerts: 'मर्यादा अलर्ट',

    aqiGood: 'उत्तम (Good)',
    aqiModerate: 'मध्यम (Moderate)',
    aqiPoor: 'वाईट (Poor)',
    aqiUnhealthy: 'अस्वस्थ (Unhealthy)',
    aqiSevere: 'गंभीर (Severe)',
    aqiCritical: 'अतिगंभीर (Critical)',

    metricPm25: 'PM2.5 (सूक्ष्म कण)',
    metricPm10: 'PM10 (स्थूल कण)',
    metricAqi: 'हवा गुणवत्ता निर्देशांक (AQI)',
    metricTemperature: 'तापमान (°C)',
    metricHumidity: 'सापेक्ष आर्द्रता (%)',
    metricWind: 'वाऱ्याचा वेग (km/h)',

    savedLocationsTitle: 'जतन केलेली वैयक्तिक ठिकाणे',
    savedLocationsSubtitle: 'H3 Res-8 अचूकतेसह आपले घर, कार्यालय आणि कौटुंबिक निरीक्षण ठिकाणे व्यवस्थापित करा.',
    alertSubscriptionsTitle: 'पर्यावरण मर्यादा अलर्ट सदस्यता',
    alertSubscriptionsSubtitle: 'सत्यापित सेन्सर मर्यादा सेट करा आणि सुरक्षित इन-अ‍ॅप सूचना प्राप्त करा.',
    historicalTrendsTitle: 'सत्यापित पर्यावरण इतिहास आणि कल',
    historicalTrendsSubtitle: 'CPCB आणि OpenAQ केंद्रांवरून मिळवलेला खरा डेटा. कोणताही बनावट डेटा नाही.',

    addLocationBtn: 'नवीन ठिकाण जोडा',
    setDefaultBtn: 'डीफॉल्ट करा',
    defaultBadge: 'डीफॉल्ट',
    createAlertBtn: 'अलर्ट तयार करा',
    markReadBtn: 'वाचलेले म्हणून चिन्हांकित करा',
    refreshBtn: 'डेटा रीफ्रेश करा',

    honestDataNotice: 'ऐतिहासिक कल पूर्णपणे प्रत्यक्ष केंद्रांच्या डेटावर आधारित आहेत. अंतर वास्तविक वेळेनुसार आहे.',
    inAppChannelNotice: '1 तासाच्या सुरक्षित अंतरासह इन-अ‍ॅप सूचना वितरण सक्रिय आहे.',
    officialWarningNotice: 'अधिकृत सूचना थेट NDMA सचेत (CAP प्रोटोकॉल) वरून घेतल्या जातात.',
  },
};

export const getTranslation = (locale: string = 'en'): TranslationDictionary => {
  if (locale === 'hi') return citizenTranslations.hi;
  if (locale === 'mr') return citizenTranslations.mr;
  return citizenTranslations.en;
};
