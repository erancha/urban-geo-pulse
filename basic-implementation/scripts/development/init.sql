------------------------------------------------
-- New tables for the UrbanGeoPulse application:
------------------------------------------------
--drop TABLE agg_streets_activity;
CREATE TABLE agg_streets_activity (
	id serial4 NOT NULL,
	street_gid int4 NOT NULL,
	timestamp_in_sec timestamp NOT NULL,
	pedestrians_count int4 DEFAULT 0,
	mobilized_count int4 DEFAULT 0,
	insertTimestamp timestamp NOT NULL,
	lastUpdateTimestamp timestamp NOT NULL,
	CONSTRAINT agg_streets_activity_pkey PRIMARY KEY (id),
	CONSTRAINT agg_streets_activity_un_by_street_gid_and_time UNIQUE (street_gid,timestamp_in_sec),
	CONSTRAINT agg_streets_activity_fk FOREIGN KEY (street_gid) REFERENCES nyc_streets(gid) ON DELETE CASCADE ON UPDATE CASCADE
);
create INDEX agg_streets_activity_timestamp_in_sec_idx on agg_streets_activity (timestamp_in_sec);

--drop TABLE agg_neighborhoods_activity;
CREATE TABLE agg_neighborhoods_activity (
	id serial4 NOT NULL,
	neighborhood_gid int4 NOT NULL,
	timestamp_in_sec timestamp NOT NULL,
	pedestrians_count int4 DEFAULT 0,
	mobilized_count int4 DEFAULT 0,
	insertTimestamp timestamp NOT NULL,
	lastUpdateTimestamp timestamp NOT NULL,
	CONSTRAINT neighborhoods_activity_pkey PRIMARY KEY (id),
	CONSTRAINT neighborhoods_activity_un_by_neighborhood_gid_and_time UNIQUE (neighborhood_gid,timestamp_in_sec),
	CONSTRAINT neighborhoods_activity_fk FOREIGN KEY (neighborhood_gid) REFERENCES nyc_neighborhoods(gid) ON DELETE CASCADE ON UPDATE CASCADE
);
create INDEX agg_neighborhoods_activity_timestamp_in_sec_idx on agg_neighborhoods_activity (timestamp_in_sec);