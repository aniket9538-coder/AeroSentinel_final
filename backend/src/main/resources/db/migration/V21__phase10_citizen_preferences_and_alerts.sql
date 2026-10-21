-- AeroSentinel Phase 10 Migration (V21)
-- Citizen Saved Locations, Alert Subscriptions, and In-App Notifications

-- 1. CITIZEN SAVED LOCATIONS
CREATE TABLE IF NOT EXISTS citizen_saved_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    label VARCHAR(100) NOT NULL,
    city_name VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    h3_index VARCHAR(15) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_citizen_saved_locations_user ON citizen_saved_locations(user_id);
CREATE INDEX IF NOT EXISTS idx_citizen_saved_locations_h3 ON citizen_saved_locations(h3_index);

-- 2. CITIZEN ALERT SUBSCRIPTIONS
CREATE TABLE IF NOT EXISTS citizen_alert_subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    location_id UUID REFERENCES citizen_saved_locations(id) ON DELETE CASCADE,
    city_name VARCHAR(100) NOT NULL,
    h3_index VARCHAR(15),
    pollutant VARCHAR(50) NOT NULL DEFAULT 'PM25',
    threshold_value DOUBLE PRECISION NOT NULL,
    delivery_channel VARCHAR(50) NOT NULL DEFAULT 'IN_APP',
    enabled BOOLEAN DEFAULT TRUE,
    last_triggered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_citizen_subscriptions_user ON citizen_alert_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_citizen_subscriptions_enabled ON citizen_alert_subscriptions(enabled);

-- 3. CITIZEN IN-APP NOTIFICATIONS
CREATE TABLE IF NOT EXISTS citizen_in_app_notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    subscription_id UUID REFERENCES citizen_alert_subscriptions(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    pollutant VARCHAR(50) NOT NULL,
    observed_value DOUBLE PRECISION NOT NULL,
    threshold_value DOUBLE PRECISION NOT NULL,
    triggering_source VARCHAR(100) NOT NULL,
    read_status BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_citizen_notifications_user ON citizen_in_app_notifications(user_id, read_status);
CREATE INDEX IF NOT EXISTS idx_citizen_notifications_created ON citizen_in_app_notifications(created_at DESC);
