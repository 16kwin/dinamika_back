-- V3__create_awms_orders.sql
CREATE TABLE awms_orders_list (
    order_uid VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255),
    order_number VARCHAR(255),
    order_datetime TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    statusreason VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE awms_orders_full (
    order_uid VARCHAR(255) PRIMARY KEY,
    order_json JSONB NOT NULL,
    FOREIGN KEY (order_uid) REFERENCES awms_orders_list(order_uid)
);

CREATE TABLE awms_order_statuses (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    order_uid VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    sub_status VARCHAR(255),
    FOREIGN KEY (order_uid) REFERENCES awms_orders_list(order_uid)
);

CREATE TABLE awms_tkp_list (
    tkp_uid VARCHAR(255) PRIMARY KEY,
    order_uid VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255),
    order_number VARCHAR(255),
    order_datetime TIMESTAMP WITH TIME ZONE,
    total_cost NUMERIC(15, 2),
    delivery_date DATE,
    status VARCHAR(255),
    statusinvoice VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (order_uid) REFERENCES awms_orders_list(order_uid)
);

CREATE TABLE awms_tkp_full (
    tkp_uid VARCHAR(255) PRIMARY KEY,
    order_uid VARCHAR(255) NOT NULL,
    tkp_json JSONB NOT NULL,
    FOREIGN KEY (order_uid) REFERENCES awms_orders_list(order_uid),
    FOREIGN KEY (tkp_uid) REFERENCES awms_tkp_list(tkp_uid)
);

CREATE TABLE awms_tkp_statuses (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tkp_uid VARCHAR(255) NOT NULL,
    order_uid VARCHAR(255),
    status VARCHAR(255),
    sub_status VARCHAR(255),
    FOREIGN KEY (tkp_uid) REFERENCES awms_tkp_list(tkp_uid),
    FOREIGN KEY (order_uid) REFERENCES awms_orders_list(order_uid)
);
CREATE TABLE awms_order_tracking (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    order_uid VARCHAR(255) NOT NULL,
    tracking_status VARCHAR(255),
    tracking_sub_status VARCHAR(255)
);

-- Трек уже создан в V2, ок

CREATE INDEX idx_awms_order_statuses_uid ON awms_order_statuses(order_uid);
CREATE INDEX idx_awms_tkp_list_order_uid ON awms_tkp_list(order_uid);
CREATE INDEX idx_awms_tkp_full_order_uid ON awms_tkp_full(order_uid);
CREATE INDEX idx_awms_tkp_statuses_tkp_uid ON awms_tkp_statuses(tkp_uid);
CREATE INDEX idx_awms_tkp_statuses_order_uid ON awms_tkp_statuses(order_uid);