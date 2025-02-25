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

--INSERT INTO agg_streets_activity (street_gid,timestamp_in_sec,pedestrians_count,mobilized_count,insertTimestamp,lastUpdateTimestamp)
--	VALUES (3, DATE_TRUNC('second', CURRENT_TIMESTAMP),random()*100,random()*10,now(),now()),
--		   (6, DATE_TRUNC('second', CURRENT_TIMESTAMP),random()*100,random()*10,now(),now())
--;
--INSERT INTO agg_streets_activity (street_gid,timestamp_in_sec,pedestrians_count,insertTimestamp,lastUpdateTimestamp) VALUES ((select id from nyc_streets where name = 'Astoria Blvd' limit 1), '2023-08-01 14:39:00.0', 5, now(), now()) 
--ON CONFLICT (street_gid, timestamp_in_sec) DO UPDATE SET pedestrians_count = agg_streets_activity.pedestrians_count + 5, lastUpdateTimestamp=now();

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

--INSERT INTO agg_neighborhoods_activity (neighborhood_gid,timestamp_in_sec,pedestrians_count,mobilized_count)
--	VALUES (2, DATE_TRUNC('second', CURRENT_TIMESTAMP),random()*1000,random()*100),
--		   (3, DATE_TRUNC('second', CURRENT_TIMESTAMP),random()*1000,random()*100)
--;



---------------------------
-- streets / neighborhoods:
---------------------------
select count(*) from nyc_neighborhoods;
select count(*) from nyc_streets
--where 
--	--name is not null
--	name like 'West%'
;
--DELETE from nyc_neighborhoods;
--DELETE from nyc_streets;

--------------------------------------------
-- streets intersecting with a neighborhood:
--------------------------------------------
select ns.name as street_name,ns.type,ns.geom as street_geom,st_npoints(ns.geom) as street_points,nn.geom as neighborhood_geom from nyc_streets ns, nyc_neighborhoods nn 
where nn.name = 'Bensonhurst' and st_intersects(ns.geom, nn.geom)
;

------------------------------------------------------
-- streets or neighborhoods intersecting with a point:
------------------------------------------------------
select name,'street' as type,geom,ST_SetSrid(ST_GeomFromText('POINT(585818.3587693551 4505178.045568525)'),26918) from nyc_streets ns 
where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(585818.3587693551 4505178.045568525)'),26918),geom)
union
select name,'neighborhood' as type,geom,ST_SetSrid(ST_GeomFromText('POINT(585818.3587693551 4505178.045568525)'),26918) from nyc_neighborhoods nn 
where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(585818.3587693551 4505178.045568525)'),26918),geom)
;

select name,'street' as type,geom,ST_SetSrid(ST_GeomFromText('POINT(573391.8330048206 4494514.323243747)'),26918) from nyc_streets ns 
where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(573391.8330048206 4494514.323243747)'),26918),geom)
union
select name,'neighborhood' as type,geom,ST_SetSrid(ST_GeomFromText('POINT(573391.8330048206 4494514.323243747)'),26918) from nyc_neighborhoods nn 
where ST_Intersects(ST_SetSrid(ST_GeomFromText('POINT(573391.8330048206 4494514.323243747)'),26918),geom)
;

------------------------------------------------
-- streets, neighborhoods and a point, together:
------------------------------------------------
select name,geom from nyc_streets where name in ('Washington Park', 'Willoughby Ave')
union 
select name,geom from nyc_neighborhoods where name in ('Parkchester','Morris Park')
union
select '**' as name, ST_SetSRID(ST_PointFromText('POINT(586728.5459260647 4505041.07535344)'), 26918)
;

-----------------------------------------------------------------------------------------------------
-- streets with a specific total points (note: there're streets with multiple records in nyc_streets)
-----------------------------------------------------------------------------------------------------
with temp as 
(
	select 
		name,
		sum(st_npoints(geom)) as st_npoints
	from
		nyc_streets
	where
--		name in ('Colden Ave')
		name is not null
	group by 
		name
) 
select /*distinct */name,st_npoints from temp
--where st_npoints = 10
order by 
--	name
	st_npoints desc
;

----------------------------------------------------------------------
-- points of a street, and all streets intersecting with these points:
----------------------------------------------------------------------
with points as
(
	select (ST_DumpPoints(geom)).geom as street_point from nyc_streets
	where name='Duffield St'
)
select /*distinct*/
	ns.name as street_name,street_point,ns.geom as intersecting_street
--	,st_distance((select geom from points limit 1), (select geom from points offset 1 limit 1))
from points p
join nyc_streets ns on ST_Intersects(street_point,ns.geom)
order by 
	name,
	street_point 
;


-------------------------------------------------------
-- agg_streets_activity and agg_neighborhoods_activity:
-------------------------------------------------------
delete from agg_streets_activity
where
--	street_gid = (select gid from nyc_streets where name = 'Duffield St')
	timestamp_in_sec between
	'2023-08-09 00:00:00.000' and now()
;
select * from agg_streets_activity;

select
--		timestamp_in_sec,pedestrians_count,mobilized_count
		count(*),
		sum(pedestrians_count) as sum_pedestrians_count,sum(mobilized_count) as sum_mobilized_count
from agg_streets_activity
where timestamp_in_sec between
--	(now() - interval '300 minutes') and now()
--	'2023-08-10 21:30:00.000' and '2023-08-10 22:00:00.000'
	'2023-08-09 00:00:00.000' and now()
;

WITH agg_streets_activity_sum AS (
		select 
			name,
--			timestamp_in_sec,
			sum(asa.pedestrians_count) as sum_pedestrians_count,
			sum(asa.mobilized_count) as sum_mobilized_count
		from agg_streets_activity asa
		join nyc_streets ns on asa.street_gid = ns.gid
		where
			timestamp_in_sec between 
--	(now() - interval '300 minutes') and now()
--	'2023-08-10 21:30:00.000' and '2023-08-10 22:00:00.000'
	'2023-08-09 00:00:00.000' and now()
--			name = 'Duffield St'
		group by 
--			timestamp_in_sec,
			name
--), agg_streets_activity_sum_desc AS (
--    SELECT timestamp_in_sec FROM agg_streets_activity_sum ORDER BY timestamp_in_sec DESC
--), agg_streets_activity_sum_asc AS (
--    SELECT timestamp_in_sec FROM agg_streets_activity_sum ORDER BY timestamp_in_sec
)
--select cast((
--         	(select timestamp_in_sec from agg_streets_activity_sum_desc limit 1) -
--         	(select timestamp_in_sec from agg_streets_activity_sum_asc limit 1)) as text) || ' minutes' as elapsed
--(SELECT timestamp_in_sec FROM agg_streets_activity_sum_desc LIMIT 1)
--UNION
--(SELECT timestamp_in_sec FROM agg_streets_activity_sum_asc LIMIT 1)
--	
select name,
--	   timestamp_in_sec /*AT TIME ZONE 'Israel'*/,
	   sum_pedestrians_count,
	   sum_mobilized_count
from agg_streets_activity_sum
order by 
--	timestamp_in_sec desc
	sum_pedestrians_count desc,name
limit 10
--
;
