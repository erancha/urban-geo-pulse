select
	count(*),
	sum(pedestrians_count) as sum_streets_pedestrians_count,
	sum(mobilized_count) as sum_streets_mobilized_count
from
	agg_streets_activity
;

delete from agg_streets_activity
	-- where
		-- 	timestamp_in_sec between (now() AT TIME ZONE 'UTC' - interval '420 seconds') 
		--  and /* now() AT TIME ZONE 'UTC' */ '2025-03-15 12:00:00'
;


select
	count(*),
	sum(pedestrians_count) as sum_neighborhoods_pedestrians_count,
	sum(mobilized_count) as sum_neighborhoods_mobilized_count
from
	agg_neighborhoods_activity
;

delete from agg_neighborhoods_activity
	-- where
		-- 	timestamp_in_sec between (now() AT TIME ZONE 'UTC' - interval '420 seconds') 
		--  and /* now() AT TIME ZONE 'UTC' */ '2025-03-15 12:00:00'
;
