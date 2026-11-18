-- FoodRescue Initial Schema Migration (PostgreSQL / Supabase compatible)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Profiles (mirrors auth.users)
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Hotels
CREATE TABLE IF NOT EXISTS hotels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    venue_type VARCHAR(50) NOT NULL,
    address TEXT NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    rooms INT DEFAULT 0,
    seats INT DEFAULT 0,
    banquet_capacity INT DEFAULT 0,
    events_per_month INT DEFAULT 0,
    operating_days_per_week INT DEFAULT 7,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    public_listing BOOLEAN NOT NULL DEFAULT TRUE,
    verification_doc_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. NGOs
CREATE TABLE IF NOT EXISTS ngos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    registration_no VARCHAR(255),
    address TEXT NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    capacity_portions INT DEFAULT 0,
    service_radius_km NUMERIC(6,2) DEFAULT 10.0,
    trust_score NUMERIC(4,2) NOT NULL DEFAULT 1.0,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    verification_doc_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Volunteers
CREATE TABLE IF NOT EXISTS volunteers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id UUID NOT NULL,
    ngo_id UUID NOT NULL,
    name VARCHAR(255),
    phone VARCHAR(50),
    vehicle VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Listings
CREATE TABLE IF NOT EXISTS listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    diet VARCHAR(50) NOT NULL,
    portions_listed INT NOT NULL CHECK (portions_listed > 0),
    cooked_at TIMESTAMPTZ NOT NULL,
    pickup_by TIMESTAMPTZ NOT NULL,
    packaging_notes TEXT,
    safety_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'posted',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (pickup_by > cooked_at)
);

CREATE INDEX IF NOT EXISTS idx_listings_status_pickup_by ON listings (status, pickup_by);
CREATE INDEX IF NOT EXISTS idx_listings_hotel_id ON listings (hotel_id, created_at DESC);

-- 6. Pickups
CREATE TABLE IF NOT EXISTS pickups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    ngo_id UUID NOT NULL REFERENCES ngos(id) ON DELETE CASCADE,
    volunteer_id UUID REFERENCES volunteers(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'claimed',
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    collected_in_geofence BOOLEAN,
    portions_received INT CHECK (portions_received >= 0),
    left_behind_portions INT CHECK (left_behind_portions >= 0),
    photo_path TEXT,
    cancel_reason TEXT,
    voided BOOLEAN NOT NULL DEFAULT FALSE,
    current_lat DOUBLE PRECISION,
    current_lng DOUBLE PRECISION,
    eta_minutes INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pickups_ngo_id ON pickups (ngo_id, created_at DESC);

-- 7. Location Pings (live tracking breadcrumbs)
CREATE TABLE IF NOT EXISTS location_pings (
    id BIGSERIAL PRIMARY KEY,
    pickup_id UUID NOT NULL REFERENCES pickups(id) ON DELETE CASCADE,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    accuracy_m NUMERIC(6,2),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_location_pings_pickup_id ON location_pings (pickup_id, recorded_at DESC);

-- 8. Tags (fixed evaluation criteria)
CREATE TABLE IF NOT EXISTS tags (
    code VARCHAR(100) PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 9. Pickup Tags (join table)
CREATE TABLE IF NOT EXISTS pickup_tags (
    pickup_id UUID NOT NULL REFERENCES pickups(id) ON DELETE CASCADE,
    tag_code VARCHAR(100) NOT NULL REFERENCES tags(code) ON DELETE CASCADE,
    PRIMARY KEY (pickup_id, tag_code)
);

-- 10. Flags (moderation / anomaly queue)
CREATE TABLE IF NOT EXISTS flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pickup_id UUID NOT NULL REFERENCES pickups(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'auto',
    status VARCHAR(50) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- 11. Admin Actions (audit log)
CREATE TABLE IF NOT EXISTS admin_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID,
    reason TEXT,
    before_data TEXT,
    after_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. Certificates (monthly impact recognition)
CREATE TABLE IF NOT EXISTS certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    month DATE NOT NULL,
    pdf_path TEXT NOT NULL,
    meals INT NOT NULL DEFAULT 0,
    ngos_served INT NOT NULL DEFAULT 1,
    co2_kg_avoided NUMERIC(10,2) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (hotel_id, month)
);

-- 13. Score Snapshots (leaderboard and Rescue Scores)
CREATE TABLE IF NOT EXISTS score_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    completed_pickups INT NOT NULL,
    portions_rescued INT NOT NULL,
    rescue_rate NUMERIC(5,2),
    participation NUMERIC(5,2),
    listing_accuracy NUMERIC(5,2),
    handoff_quality NUMERIC(5,2),
    left_behind NUMERIC(5,2),
    composite NUMERIC(5,2),
    tier VARCHAR(50),
    rank_in_category INT,
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    streak_weeks INT DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 14. News Items
CREATE TABLE IF NOT EXISTS news_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    snippet TEXT,
    source_name VARCHAR(255),
    url TEXT,
    image_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 15. In-app Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255),
    message TEXT,
    type VARCHAR(50) NOT NULL,
    data TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id, created_at DESC);

-- 16. Follows (public user following hotel)
CREATE TABLE IF NOT EXISTS follows (
    user_id UUID NOT NULL,
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, hotel_id)
);

-- 17. Kudos (weekly encouragement)
CREATE TABLE IF NOT EXISTS kudos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    hotel_id UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, hotel_id, week_start)
);

-- 18. Platform Settings
CREATE TABLE IF NOT EXISTS platform_settings (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Insert Initial Master Tags
INSERT INTO tags (code, label, kind, active) VALUES
    ('ready_on_time', 'Ready on Time', 'positive', true),
    ('well_packaged', 'Well Packaged', 'positive', true),
    ('fresh_hot', 'Fresh & Temperature Maintained', 'positive', true),
    ('accurate_quantity', 'Accurate Portion Count', 'positive', true),
    ('friendly_staff', 'Helpful Staff', 'positive', true),
    ('delayed_handoff', 'Delayed Handoff', 'issue', true),
    ('quantity_short', 'Quantity Short', 'issue', true),
    ('poor_packaging', 'Poor Packaging', 'issue', true),
    ('temperature_issue', 'Temperature Mismatch', 'issue', true)
ON CONFLICT (code) DO NOTHING;
