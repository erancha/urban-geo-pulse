select
	count(*),
	sum(pedestrians_count) as sum_streets_pedestrians_count,
	sum(mobilized_count) as sum_streets_mobilized_count
from
	agg_streets_activity
;

	
delete from agg_streets_activity
	-- where
	-- 	lastUpdateTimestamp between (now() AT TIME ZONE 'Israel' - interval '420 seconds') and now() AT TIME ZONE 'Israel'
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
	-- 	lastUpdateTimestamp between (now() AT TIME ZONE 'Israel' - interval '420 seconds') and now() AT TIME ZONE 'Israel'
;
