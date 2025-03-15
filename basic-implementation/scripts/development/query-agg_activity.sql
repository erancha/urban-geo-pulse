------------------------------
-- totals (in the time range):
------------------------------
select
	count(*) as rowsCount,
	sum(pedestrians_count) as sum_streets_pedestrians_count,
	sum(mobilized_count) as sum_streets_mobilized_count
--	,sum(pedestrians_count) + sum(mobilized_count) as sum_streets_people_count
from
	agg_streets_activity
where
	timestamp_in_sec between
		(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
		-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
	;

select
	count(*),
	sum(pedestrians_count) as sum_neighborhoods_pedestrians_count,
	sum(mobilized_count) as sum_neighborhoods_mobilized_count
from
	agg_neighborhoods_activity
where
	timestamp_in_sec between
		(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
		-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
;


--------------------------------------------------------------
-- N most populated streets (in the time range):
--------------------------------------------------------------
with agg_streets_activity_sum as (
	select 
		name,
		sum(asa.pedestrians_count) as sum_pedestrians_streets_count,
		sum(asa.mobilized_count) as sum_mobilized_streets_count
	from
		agg_streets_activity asa
	join nyc_streets ns on
		asa.street_gid = ns.gid
	where
		timestamp_in_sec between
			(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
			-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
	group by 
		name
)
select
	name,
	sum_pedestrians_streets_count,
	sum_mobilized_streets_count
from
	agg_streets_activity_sum
order by 
	sum_pedestrians_streets_count desc,
	name
limit 10
;


--------------------------------------------
-- first and last items (in the time range):
--------------------------------------------
WITH agg_streets_activity_range_lastUpdateTimestamp AS (
	select 
		timestamp_in_sec,
		lastUpdateTimestamp
	from agg_streets_activity asa
	where
		timestamp_in_sec between
			(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
			-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
), agg_streets_activity_range_desc AS (
    SELECT lastUpdateTimestamp as timestamp FROM agg_streets_activity_range_lastUpdateTimestamp ORDER BY lastUpdateTimestamp DESC
), agg_streets_activity_range_insertTimestamp AS (
	select 
		timestamp_in_sec,
		insertTimestamp
	from agg_streets_activity asa
	where
		insertTimestamp between 
			(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
			-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
), agg_streets_activity_range_asc AS (
    SELECT insertTimestamp as timestamp FROM agg_streets_activity_range_insertTimestamp ORDER BY insertTimestamp
)
select cast((
         	(select timestamp from agg_streets_activity_range_desc limit 1) -
         	(select timestamp from agg_streets_activity_range_asc limit 1)) as text) || ' minutes' as elapsed
--(select timestamp from agg_streets_activity_range_desc limit 1) 
--UNION
--(select timestamp from agg_streets_activity_range_asc limit 1)
;


-------------------------------------------------------------
-- all streets / neighborhood activities (in the time range):
-------------------------------------------------------------
select 
	name,
	timestamp_in_sec,
	asa.pedestrians_count,
	asa.mobilized_count,
	insertTimestamp, 
	lastUpdateTimestamp 
from 
	agg_streets_activity asa
join nyc_streets ns on
	asa.street_gid = ns.gid
where
	timestamp_in_sec between
		(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
		-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
order by
	timestamp_in_sec desc,
	name
;

select 
	name,
	timestamp_in_sec,
	ana.pedestrians_count,
	ana.mobilized_count,
	insertTimestamp, 
	lastUpdateTimestamp 
from 
	agg_neighborhoods_activity ana 
join nyc_neighborhoods nn
	on ana.neighborhood_gid = nn.gid
where
	timestamp_in_sec between
		(now() AT TIME ZONE 'UTC' - interval '30 minutes') and (now() AT TIME ZONE 'UTC')
		-- ('2025-03-15 12:00:00'::timestamp AT TIME ZONE 'UTC') and ('2025-03-15 12:30:00'::timestamp AT TIME ZONE 'UTC')  
order by
	timestamp_in_sec desc,
	name
;


----------------------------------------
-- table disk sizes and records counts :
----------------------------------------
SELECT 
    t.table_name,
    REPLACE(pg_size_pretty(pg_total_relation_size(t.table_name::text) / (1024 * 1024)), ' bytes', '') AS size_mb
FROM 
    information_schema.tables AS t
WHERE 
    t.table_schema = 'public'
    AND t.table_name::text LIKE 'agg_%'
ORDER BY 
    pg_total_relation_size(t.table_name::text) DESC;
 
   