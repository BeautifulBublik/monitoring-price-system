CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_settings
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    email_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    telegram_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    telegram_chat_id VARCHAR(32)
);

CREATE TABLE products
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    url                 VARCHAR(2048) NOT NULL,
    shop_name           VARCHAR(255)  NOT NULL,
    title               VARCHAR(255),
    tracking_status     VARCHAR(16)   NOT NULL,
    threshold_type      VARCHAR(16)   NOT NULL,
    threshold_base      VARCHAR(16)   NOT NULL,
    threshold_value     NUMERIC(12, 2),
    last_notified_price NUMERIC(12, 2),
    last_checked_at     TIMESTAMP(6) WITH TIME ZONE,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE price_history
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    price      NUMERIC(12, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    checked_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

-- The user's product list — the most frequent query in the application.
CREATE INDEX idx_products_user_id ON products (user_id);

-- The scheduler sweep selects only active products.
CREATE INDEX idx_products_tracking_status ON products (tracking_status);

-- The same URL cannot be tracked twice; this also speeds up the duplicate check
-- (a url of length 2048 will not fit into a Postgres index, so we index its hash).
CREATE UNIQUE INDEX uq_products_user_url ON products (user_id, md5(url));

-- Covers both the history-for-a-period query and the latest-price lookup (ORDER BY checked_at DESC LIMIT 1).
CREATE INDEX idx_price_history_product_checked_at ON price_history (product_id, checked_at DESC);
