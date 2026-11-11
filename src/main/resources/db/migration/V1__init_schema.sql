CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT uuidv7(),
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id         UUID PRIMARY KEY DEFAULT uuidv7(),
    name       VARCHAR(120) NOT NULL,
    address    VARCHAR(255) NOT NULL,
    open_time  TIME         NOT NULL,
    close_time TIME         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_venue_hours CHECK (open_time < close_time)
);

CREATE TABLE courts (
    id             UUID PRIMARY KEY DEFAULT uuidv7(),
    venue_id       UUID        NOT NULL REFERENCES venues (id),
    name           VARCHAR(80)   NOT NULL,
    sport_type     VARCHAR(20)   NOT NULL,
    price_per_hour NUMERIC(12, 2) NOT NULL CHECK (price_per_hour > 0),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_court_name_per_venue UNIQUE (venue_id, name)
);
CREATE INDEX idx_courts_venue ON courts (venue_id);

CREATE TABLE bookings (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    code         VARCHAR(30)    NOT NULL UNIQUE,
    user_id      UUID           NOT NULL REFERENCES users (id),
    court_id     UUID           NOT NULL REFERENCES courts (id),
    booking_date DATE           NOT NULL,
    start_time   TIME           NOT NULL,
    end_time     TIME           NOT NULL,
    total_price  NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    expires_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT chk_booking_time CHECK (start_time < end_time)
);

CREATE INDEX idx_bookings_court_date ON bookings (court_id, booking_date);

CREATE INDEX idx_bookings_user ON bookings (user_id);

CREATE TABLE payments (
    id           UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id      UUID           NOT NULL REFERENCES users (id),
    amount       NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    provider_ref VARCHAR(100)   UNIQUE,
    snap_token   VARCHAR(255),
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);


-- versi menggunakan merchant reference, provider reference, dan checkout 
-- CREATE TABLE payments (
--     id                  UUID PRIMARY KEY DEFAULT uuidv7(),
--     booking_id          UUID NOT NULL REFERENCES bookings(id),
--     amount              NUMERIC(12, 2) NOT NULL,
--     currency            VARCHAR(3) NOT NULL DEFAULT 'IDR',
--     status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
--     provider            VARCHAR(30) NOT NULL,
--     merchant_reference  VARCHAR(100) NOT NULL,
--     provider_reference  VARCHAR(150),
--     checkout_url        TEXT,
--     provider_metadata   JSONB,
--     paid_at             TIMESTAMPTZ,
--     created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
--     updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

--     CONSTRAINT uq_payment_merchant_reference
--         UNIQUE (provider, merchant_reference),

--     CONSTRAINT uq_payment_provider_reference
--         UNIQUE (provider, provider_reference)
-- );
