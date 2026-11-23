-- Where each posting is, resolved from its free-text location by DanishPlaces. NULL means not
-- resolved yet; an empty array means it can be anywhere (remote, hybrid, or no known place).
ALTER TABLE jobs ADD COLUMN place_lats double precision[], ADD COLUMN place_lons double precision[];

-- The area enrichment is limited to. NULL means the default: about an hour's drive from Viby J.
ALTER TABLE preferences ADD COLUMN enrichment_home text, ADD COLUMN enrichment_radius_km integer;
