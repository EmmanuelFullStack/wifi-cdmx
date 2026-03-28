CREATE TABLE IF NOT EXISTS wifi_points (
    id                  BIGSERIAL        PRIMARY KEY,
    colonia             VARCHAR(255)     NOT NULL DEFAULT '',
    alcaldia            VARCHAR(255)     NOT NULL DEFAULT '',
    calle               TEXT             NOT NULL DEFAULT '',
    programa            VARCHAR(255)     NOT NULL DEFAULT '',
    fecha_instalacion   DATE,
    lat                 DOUBLE PRECISION NOT NULL,
    lon                 DOUBLE PRECISION NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wifi_alcaldia
    ON wifi_points (LOWER(alcaldia));

CREATE INDEX IF NOT EXISTS idx_wifi_lat_lon
    ON wifi_points (lat, lon);

COMMENT ON TABLE  wifi_points     IS 'Public WiFi access points in Mexico City (CDMX open data)';
COMMENT ON COLUMN wifi_points.lat IS 'Latitude in decimal degrees (WGS84)';
COMMENT ON COLUMN wifi_points.lon IS 'Longitude in decimal degrees (WGS84)';